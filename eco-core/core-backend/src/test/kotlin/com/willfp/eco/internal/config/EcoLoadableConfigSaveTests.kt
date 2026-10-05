package com.willfp.eco.internal.config

import com.willfp.eco.core.PluginLike
import com.willfp.eco.core.config.ConfigType
import com.willfp.eco.core.config.interfaces.LoadableConfig
import com.willfp.eco.core.config.updating.ConfigHandler
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

internal class EcoLoadableConfigSaveTests {
    @TempDir
    lateinit var dir: File

    private val records = mutableListOf<LogRecord>()

    private val testLogger = Logger.getAnonymousLogger().apply {
        useParentHandlers = false
        addHandler(object : Handler() {
            override fun publish(record: LogRecord) {
                synchronized(records) { records += record }
            }

            override fun flush() = Unit
            override fun close() = Unit
        })
    }

    private val plugin by lazy {
        object : PluginLike {
            override fun getDataFolder() = dir
            override fun getLogger(): Logger = testLogger
            override fun getConfigHandler() = object : ConfigHandler {
                override fun saveAllConfigs() = Unit
                override fun updateConfigs() = Unit
                override fun addConfig(config: LoadableConfig) = Unit
            }
        }
    }

    private val file get() = File(dir, "data.yml")

    private fun tempFiles() = dir.listFiles()!!.filter { it.name.endsWith(".tmp") }

    private fun createConfig(contents: String = "value: 1\n"): TestConfig {
        file.writeText(contents)
        return TestConfig(plugin)
    }

    @Test
    fun saveKeepsTheOriginalFileWhenSerializationFails() {
        val config = createConfig()
        config.set("value", 2)
        config.failSerialization = true

        Assertions.assertThrows(IllegalStateException::class.java) { config.save() }

        Assertions.assertTrue(file.exists(), "the live file must survive a failed save")
        Assertions.assertEquals("value: 1\n", file.readText(), "the live file must be untouched")
    }

    @Test
    fun saveAsyncKeepsTheOriginalFileWhenSerializationFails() {
        val config = createConfig()
        config.set("value", 2)
        config.failSerialization = true

        Assertions.assertThrows(IllegalStateException::class.java) { config.saveAsync() }

        Assertions.assertEquals("value: 1\n", file.readText(), "the live file must not be truncated")
    }

    @Test
    fun saveWritesTheNewContentAndLeavesNoTempFile() {
        val config = createConfig()
        config.set("value", 2)

        config.save()

        Assertions.assertEquals(2, TestConfig(plugin).getInt("value"))
        Assertions.assertEquals(emptyList<File>(), tempFiles(), "temp file must be moved into place")
    }

    @Test
    fun saveRecreatesAMissingFile() {
        val config = createConfig()
        config.set("value", 2)
        file.delete()

        config.save()

        Assertions.assertTrue(file.exists(), "a missing file must not make every later save a no-op")
        Assertions.assertEquals(2, TestConfig(plugin).getInt("value"))
        Assertions.assertTrue(
            synchronized(records) { records.any { it.level == Level.WARNING && it.message.contains("data.yml") } },
            "recreating a missing file is logged as a warning"
        )
    }

    @Test
    fun saveDurablyWritesAndSyncsTheDirectory() {
        val config = createConfig()
        config.set("value", 2)
        AtomicFileWriter.shutdown(10, TimeUnit.SECONDS) // nothing left over from other tests
        val before = AtomicFileWriter.directorySyncs.get()

        config.saveDurably()

        Assertions.assertEquals(before + 1, AtomicFileWriter.directorySyncs.get())
        Assertions.assertEquals(2, TestConfig(plugin).getInt("value"))
    }

    @Test
    fun saveAsyncEventuallyWritesTheNewContent() {
        val config = createConfig()
        config.set("value", 2)

        config.saveAsync()

        val deadline = System.currentTimeMillis() + 10_000
        while (!file.readText().contains("value: 2") && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
        Assertions.assertEquals(2, TestConfig(plugin).getInt("value"))
    }

    @Test
    fun concurrentSavesOfTheSameFileNeverFail() {
        val config = createConfig()
        val threads = 8
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)

        val futures = (1..threads).map { thread ->
            pool.submit<Unit> {
                start.await()
                repeat(25) { iteration ->
                    config.set("value", thread * 1000 + iteration)
                    if (iteration % 2 == 0) config.save() else config.saveAsync()
                }
            }
        }
        start.countDown()
        futures.forEach { it.get(30, TimeUnit.SECONDS) }
        pool.shutdown()

        // A final synchronous save must win over any async save still queued.
        config.set("value", -1)
        config.save()
        AtomicFileWriter.shutdown(10, TimeUnit.SECONDS) // let queued async saves finish

        Assertions.assertEquals(-1, TestConfig(plugin).getInt("value"))
        Assertions.assertEquals(emptyList<File>(), tempFiles(), "temp files must be moved into place")
    }

    private class TestConfig(plugin: PluginLike) : EcoLoadableConfig(
        ConfigType.YAML,
        "data",
        plugin,
        "",
        TestConfig::class.java,
        false
    ) {
        var failSerialization = false

        override fun toPlaintext(): String {
            check(!failSerialization) { "serialization failed" }
            return super.toPlaintext()
        }
    }
}
