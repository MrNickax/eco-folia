package com.willfp.eco.internal.config

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.AccessDeniedException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/*
Crash-safe file writes for configs (and therefore data.yml).

The live file is never deleted or truncated: the new content goes to a uniquely named
.<name>.<random>.tmp in the same directory, is forced to disk, and then replaces the live
file with an atomic rename. A crash or power loss at any point leaves either the old or the
new file, never a partial or empty one.

Durable writes (saveAsync and the data handlers) also sync the directory so the rename itself
survives a power loss. Synchronous saves skip that second sync to keep main and region threads
fast (forcing a small file costs about a millisecond on an SSD); after a power loss they can at
worst roll back to the previous complete file.

Every snapshot takes its sequence number under its config's save lock, so an older snapshot
never overwrites a newer one. A file is only tracked while a snapshot of it is in flight.
 */

object AtomicFileWriter {
    private const val RENAME_ATTEMPTS = 5
    private const val RENAME_BACKOFF_MILLIS = 10L
    private const val WRITER_IDLE_SECONDS = 5L

    /**
     * Directory syncs attempted, for tests.
     */
    internal val directorySyncs = AtomicLong()

    private val sequence = AtomicLong()
    private val files = ConcurrentHashMap<Path, FileState>()
    private val cleanedPaths = ConcurrentHashMap.newKeySet<Path>()

    private val writerLock = Any()

    @Volatile
    private var asyncWriter: ThreadPoolExecutor? = null

    internal class FileState {
        /** Snapshots taken but not yet written or dropped; guarded by the [files] map. */
        var inFlight = 0

        /** Guards the file on disk and [lastWritten]. */
        val ioLock = Any()
        var lastWritten = 0L

        /** The newest snapshot waiting for the background writer; guarded by this. */
        var pending: Snapshot? = null
        var pendingFuture: CompletableFuture<Void>? = null
    }

    internal class Snapshot(
        val path: Path,
        val state: FileState,
        val sequence: Long,
        val content: ByteArray
    )

    /**
     * Take a snapshot of [content] for [target].
     *
     * Call it under the config's own save lock right after building the content, so the
     * snapshots of one config are ordered like their content.
     */
    internal fun snapshot(target: Path, content: ByteArray): Snapshot {
        val path = target.toAbsolutePath().normalize()
        var taken = 0L
        val state = files.compute(path) { _, existing ->
            (existing ?: FileState()).also {
                it.inFlight++
                taken = sequence.incrementAndGet()
            }
        }!!

        return Snapshot(path, state, taken, content)
    }

    /**
     * Write [snapshot] on the calling thread; [durable] also syncs the directory.
     */
    @Throws(IOException::class)
    internal fun write(snapshot: Snapshot, durable: Boolean) {
        try {
            synchronized(snapshot.state.ioLock) {
                if (snapshot.sequence <= snapshot.state.lastWritten) {
                    return // A newer snapshot is already on disk
                }

                replace(snapshot.path, snapshot.content, durable)
                snapshot.state.lastWritten = snapshot.sequence
            }
        } finally {
            release(snapshot)
        }
    }

    /**
     * Write [snapshot] durably on the background writer.
     *
     * While a snapshot of the same file is still waiting, only the newest one is kept and
     * every caller gets the same future.
     */
    internal fun writeAsync(snapshot: Snapshot): CompletableFuture<Void> {
        val state = snapshot.state
        val future: CompletableFuture<Void>

        synchronized(state) {
            val waiting = state.pending
            if (waiting != null) {
                if (snapshot.sequence > waiting.sequence) {
                    state.pending = snapshot
                    release(waiting)
                } else {
                    release(snapshot)
                }
                return state.pendingFuture!!
            }

            future = CompletableFuture()
            state.pending = snapshot
            state.pendingFuture = future
        }

        val task = Runnable { writePending(state) }
        try {
            writer().execute(task)
        } catch (e: RejectedExecutionException) {
            task.run() // The writer is shutting down: write here rather than drop it
        }

        return future
    }

    /**
     * Finish every queued async write and stop the writer thread, waiting at most [timeout].
     *
     * Async writes after this start a new writer, which stops again once idle.
     *
     * @return If every queued write finished in time.
     */
    fun shutdown(timeout: Long, unit: TimeUnit): Boolean {
        val writer = synchronized(writerLock) {
            asyncWriter.also { asyncWriter = null }
        } ?: return true

        writer.shutdown()
        return writer.awaitTermination(timeout, unit)
    }

    internal fun runOnWriter(task: Runnable) = writer().execute(task)

    internal fun isTracked(target: Path) = files.containsKey(target.toAbsolutePath().normalize())

    private fun writer(): ThreadPoolExecutor {
        asyncWriter?.let { return it }

        synchronized(writerLock) {
            asyncWriter?.let { return it }

            // The thread exits when idle so it never pins eco's classloader across reloads
            val writer = ThreadPoolExecutor(
                1, 1,
                WRITER_IDLE_SECONDS, TimeUnit.SECONDS,
                LinkedBlockingQueue()
            ) { runnable ->
                Thread(runnable, "eco-config-writer").apply { isDaemon = true }
            }
            writer.allowCoreThreadTimeOut(true)
            asyncWriter = writer
            return writer
        }
    }

    private fun writePending(state: FileState) {
        val snapshot: Snapshot
        val future: CompletableFuture<Void>
        synchronized(state) {
            snapshot = state.pending!!
            future = state.pendingFuture!!
            state.pending = null
            state.pendingFuture = null
        }

        try {
            write(snapshot, durable = true)
            future.complete(null)
        } catch (e: Throwable) {
            future.completeExceptionally(e)
        }
    }

    private fun release(snapshot: Snapshot) {
        files.computeIfPresent(snapshot.path) { _, state ->
            state.inFlight--
            if (state.inFlight == 0) null else state
        }
    }

    private fun replace(target: Path, content: ByteArray, durable: Boolean) {
        if (cleanedPaths.add(target)) {
            deleteStaleTemps(target)
        }

        val (temp, channel) = openTemp(target)
        try {
            channel.use {
                val buffer = ByteBuffer.wrap(content)
                while (buffer.hasRemaining()) {
                    it.write(buffer)
                }
                it.force(true)
            }

            copyPermissions(target, temp)
            move(temp, target)
        } catch (e: IOException) {
            runCatching { Files.deleteIfExists(temp) }.onFailure { e.addSuppressed(it) }
            throw e
        }

        if (durable) {
            syncDirectory(target.parent)
        }
    }

    private fun tempPattern(target: Path) = Regex("""\.${Regex.escape(target.fileName.toString())}\.[0-9a-f]{16}\.tmp""")

    private fun openTemp(target: Path): Pair<Path, FileChannel> {
        while (true) {
            val random = "%016x".format(ThreadLocalRandom.current().nextLong())
            val temp = target.resolveSibling(".${target.fileName}.$random.tmp")
            try {
                // CREATE_NEW keeps the default permissions, unlike createTempFile's 0600
                return temp to FileChannel.open(temp, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
            } catch (e: FileAlreadyExistsException) {
                // Try another name
            }
        }
    }

    /**
     * Remove temp files left by a crash; only this JVM writes the file and it holds its lock.
     */
    private fun deleteStaleTemps(target: Path) {
        val directory = target.parent ?: return
        val pattern = tempPattern(target)

        try {
            Files.newDirectoryStream(directory) { pattern.matches(it.fileName.toString()) }.use { stale ->
                for (temp in stale) {
                    runCatching { Files.deleteIfExists(temp) }
                }
            }
        } catch (e: IOException) {
            // Leftovers are harmless; the next write uses a new name anyway
        }
    }

    private fun copyPermissions(from: Path, to: Path) {
        if (!Files.exists(from) || "posix" !in from.fileSystem.supportedFileAttributeViews()) {
            return
        }

        try {
            Files.setPosixFilePermissions(to, Files.getPosixFilePermissions(from))
        } catch (e: IOException) {
            // Keep the default permissions rather than fail the save
        }
    }

    private fun move(temp: Path, target: Path) {
        var attempt = 1
        while (true) {
            try {
                try {
                    Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                } catch (e: AtomicMoveNotSupportedException) {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
                }
                return
            } catch (e: AccessDeniedException) {
                // Windows refuses while another process (antivirus, backup, editor) has the file open
                if (attempt >= RENAME_ATTEMPTS) {
                    throw e
                }
                Thread.sleep(RENAME_BACKOFF_MILLIS shl (attempt - 1))
                attempt++
            }
        }
    }

    /**
     * Make the rename itself durable; not supported on every platform (e.g. Windows), so best effort.
     */
    private fun syncDirectory(directory: Path?) {
        if (directory == null) {
            return
        }

        directorySyncs.incrementAndGet()
        try {
            FileChannel.open(directory, StandardOpenOption.READ).use { it.force(true) }
        } catch (e: IOException) {
            // Directories can't be opened or synced here; the rename is still atomic.
        }
    }
}
