package com.willfp.eco.internal.config

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/*
Crash-safe file writes for configs (and therefore data.yml).

The live file is never deleted or truncated: the new content goes to <name>.tmp in the
same directory, is forced to disk, and then replaces the live file with an atomic rename.
A crash or power loss at any point leaves either the old or the new file, never a partial one.

Writes of the same file are serialised by a per-file lock so two writers can never clobber
the shared temp file. Every snapshot carries a sequence number taken after its content was
built, so an older snapshot (e.g. a queued async save) never overwrites a newer one.
 */

internal object AtomicFileWriter {
    private val sequence = AtomicLong()
    private val files = ConcurrentHashMap<Path, FileState>()

    private val asyncWriter: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "eco-config-writer").apply { isDaemon = true }
    }

    private class FileState {
        var lastWritten = 0L
    }

    /**
     * Take the sequence number of a snapshot; call it right after building the content.
     */
    fun nextSequence(): Long = sequence.incrementAndGet()

    /**
     * Atomically replace [target] with [content], blocking until it is durable on disk.
     *
     * Does nothing if a newer snapshot of the same file has already been written.
     */
    @Throws(IOException::class)
    fun write(target: Path, content: ByteArray, snapshot: Long = nextSequence()) {
        val path = target.toAbsolutePath().normalize()
        val state = files.computeIfAbsent(path) { FileState() }

        synchronized(state) {
            if (snapshot <= state.lastWritten) {
                return
            }

            replace(path, content)
            state.lastWritten = snapshot
        }
    }

    /**
     * Write [content] to [target] on the single background writer.
     *
     * The snapshot is ordered now, so a later synchronous save of the same file always wins.
     */
    fun writeAsync(target: Path, content: ByteArray): CompletableFuture<Void> {
        val snapshot = nextSequence()
        return CompletableFuture.runAsync({ write(target, content, snapshot) }, asyncWriter)
    }

    private fun replace(target: Path, content: ByteArray) {
        val temp = target.resolveSibling("${target.fileName}.tmp")

        try {
            FileChannel.open(
                temp,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING
            ).use { channel ->
                val buffer = ByteBuffer.wrap(content)
                while (buffer.hasRemaining()) {
                    channel.write(buffer)
                }
                channel.force(true)
            }

            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (e: AtomicMoveNotSupportedException) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (e: IOException) {
            runCatching { Files.deleteIfExists(temp) }.onFailure { e.addSuppressed(it) }
            throw e
        }

        syncDirectory(target.parent)
    }

    /**
     * Make the rename itself durable; not supported on every platform (e.g. Windows), so best effort.
     */
    private fun syncDirectory(directory: Path?) {
        if (directory == null) {
            return
        }

        try {
            FileChannel.open(directory, StandardOpenOption.READ).use { it.force(true) }
        } catch (e: IOException) {
            // Directories can't be opened or synced here; the rename is still atomic.
        }
    }
}
