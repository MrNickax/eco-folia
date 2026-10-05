package com.willfp.eco.internal.config

import java.io.File
import java.io.FileInputStream
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

internal class AtomicFileWriterTests {
    @TempDir
    lateinit var dir: File

    private fun file(name: String = "data.yml") = File(dir, name).apply { writeText("old") }

    private fun tempFiles(name: String = "data.yml") = dir.listFiles()!!
        .filter { (it.name.startsWith(".$name.") && it.name.endsWith(".tmp")) || it.name == "$name.tmp" }

    private fun write(file: File, content: String, durable: Boolean = false) =
        AtomicFileWriter.write(AtomicFileWriter.snapshot(file.toPath(), content.toByteArray()), durable)

    private fun writeAsync(file: File, content: String): CompletableFuture<Void> =
        AtomicFileWriter.writeAsync(AtomicFileWriter.snapshot(file.toPath(), content.toByteArray()))

    /**
     * Hold the background writer until the returned latch is released.
     */
    private fun blockWriter(): CountDownLatch {
        val release = CountDownLatch(1)
        val started = CountDownLatch(1)
        AtomicFileWriter.runOnWriter {
            started.countDown()
            release.await(10, TimeUnit.SECONDS)
        }
        started.await(10, TimeUnit.SECONDS)
        return release
    }

    @Test
    fun replacesTheFileAndLeavesNoTempFile() {
        val file = file()

        write(file, "new")

        Assertions.assertEquals("new", file.readText())
        Assertions.assertEquals(emptyList<File>(), tempFiles())
    }

    @Test
    fun removesStaleTempFilesFromACrashedWrite() {
        val file = file()
        File(dir, ".data.yml.0123456789abcdef.tmp").writeText("garbage left by a crash")

        write(file, "new")

        Assertions.assertEquals("new", file.readText())
        Assertions.assertEquals(emptyList<File>(), tempFiles())
    }

    @Test
    fun anOlderSnapshotNeverOverwritesANewerOne() {
        val file = file()
        val older = AtomicFileWriter.snapshot(file.toPath(), "older".toByteArray())
        val newer = AtomicFileWriter.snapshot(file.toPath(), "newer".toByteArray())

        AtomicFileWriter.write(newer, false)
        AtomicFileWriter.write(older, false)

        Assertions.assertEquals("newer", file.readText())
    }

    @Test
    fun queuedAsyncWritesAreCoalesced() {
        val file = file()
        val release = blockWriter()

        val first = writeAsync(file, "1")
        val second = writeAsync(file, "2")
        val third = writeAsync(file, "3")

        Assertions.assertSame(first, second, "a waiting snapshot is replaced, not queued twice")
        Assertions.assertSame(second, third, "a waiting snapshot is replaced, not queued twice")
        release.countDown()
        third.get(10, TimeUnit.SECONDS)
        Assertions.assertEquals("3", file.readText())
    }

    @Test
    fun shutdownDrainsQueuedWrites() {
        val release = blockWriter()
        val files = (1..3).map { file("f$it.yml") }
        files.forEach { writeAsync(it, "new") }

        Thread {
            Thread.sleep(100)
            release.countDown()
        }.start()

        Assertions.assertTrue(AtomicFileWriter.shutdown(10, TimeUnit.SECONDS), "queued writes drained in time")
        files.forEach { Assertions.assertEquals("new", it.readText(), "${it.name} written before shutdown returned") }
    }

    @Test
    fun writeAsyncStillWorksAfterShutdown() {
        val file = file()
        AtomicFileWriter.shutdown(10, TimeUnit.SECONDS)

        writeAsync(file, "new").get(10, TimeUnit.SECONDS)

        Assertions.assertEquals("new", file.readText())
    }

    @Test
    fun renameRetriesWhileTheTargetIsBrieflyLocked() {
        val file = file()
        // On Windows a reader without FILE_SHARE_DELETE (antivirus, backup, editor) blocks the rename
        val reader = FileInputStream(file)
        Thread {
            Thread.sleep(60)
            reader.close()
        }.start()

        write(file, "new")

        Assertions.assertEquals("new", file.readText())
    }

    @Test
    fun keepsPosixPermissions() {
        Assumptions.assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"))
        val file = file()
        val permissions = PosixFilePermissions.fromString("rw-rw-r--")
        Files.setPosixFilePermissions(file.toPath(), permissions)

        write(file, "new", durable = true)

        Assertions.assertEquals(permissions, Files.getPosixFilePermissions(file.toPath()))
    }

    @Test
    fun onlyDurableWritesSyncTheDirectory() {
        val file = file()
        AtomicFileWriter.shutdown(10, TimeUnit.SECONDS) // nothing left over from other tests
        val before = AtomicFileWriter.directorySyncs.get()

        write(file, "a", durable = false)
        Assertions.assertEquals(before, AtomicFileWriter.directorySyncs.get(), "synchronous save skips it")

        write(file, "b", durable = true)
        Assertions.assertEquals(before + 1, AtomicFileWriter.directorySyncs.get(), "durable save syncs it")

        writeAsync(file, "c").get(10, TimeUnit.SECONDS)
        Assertions.assertEquals(before + 2, AtomicFileWriter.directorySyncs.get(), "async save syncs it")
    }

    @Test
    fun forgetsFilesOnceTheirWritesAreDone() {
        val file = file()

        write(file, "a")
        writeAsync(file, "b").get(10, TimeUnit.SECONDS)

        Assertions.assertFalse(AtomicFileWriter.isTracked(file.toPath()))
    }
}
