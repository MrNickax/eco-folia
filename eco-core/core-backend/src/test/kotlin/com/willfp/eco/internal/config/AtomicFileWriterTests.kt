package com.willfp.eco.internal.config

import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

internal class AtomicFileWriterTests {
    @TempDir
    lateinit var dir: File

    @Test
    fun replacesTheFileAndLeavesNoTempFile() {
        val file = File(dir, "data.yml").apply { writeText("old") }

        AtomicFileWriter.write(file.toPath(), "new".toByteArray())

        Assertions.assertEquals("new", file.readText())
        Assertions.assertFalse(File(dir, "data.yml.tmp").exists())
    }

    @Test
    fun overwritesAStaleTempFileFromACrashedWrite() {
        val file = File(dir, "data.yml").apply { writeText("old") }
        File(dir, "data.yml.tmp").writeText("garbage left by a crash, longer than the new content")

        AtomicFileWriter.write(file.toPath(), "new".toByteArray())

        Assertions.assertEquals("new", file.readText())
        Assertions.assertFalse(File(dir, "data.yml.tmp").exists())
    }

    @Test
    fun anOlderSnapshotNeverOverwritesANewerOne() {
        val file = File(dir, "data.yml").apply { writeText("old") }
        val older = AtomicFileWriter.nextSequence()
        val newer = AtomicFileWriter.nextSequence()

        AtomicFileWriter.write(file.toPath(), "newer".toByteArray(), newer)
        AtomicFileWriter.write(file.toPath(), "older".toByteArray(), older)

        Assertions.assertEquals("newer", file.readText())
    }

    @Test
    fun asyncWriteIsDurableWhenTheFutureCompletes() {
        val file = File(dir, "data.yml").apply { writeText("old") }

        AtomicFileWriter.writeAsync(file.toPath(), "new".toByteArray()).get(10, TimeUnit.SECONDS)

        Assertions.assertEquals("new", file.readText())
    }
}
