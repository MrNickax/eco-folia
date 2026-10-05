package com.willfp.eco.internal.spigot.data.profiles

import com.willfp.eco.core.data.handlers.DataTypeSerializer
import com.willfp.eco.core.data.handlers.PersistentDataHandler
import com.willfp.eco.core.data.keys.PersistentDataKey
import com.willfp.eco.core.data.keys.PersistentDataKeyType
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.logging.Logger
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class ProfileHandlerShutdownTests {
    /**
     * A handler whose writes take [writeMillis] and that counts its saves.
     */
    private class TestHandler(
        private val writeMillis: Long,
        private val failSave: Boolean = false
    ) : PersistentDataHandler("test") {
        @Volatile
        var saves = 0

        init {
            PersistentDataKeyType.STRING.registerSerializer(this, object : DataTypeSerializer<String>() {
                override fun readAsync(uuid: UUID, key: PersistentDataKey<String>): String? = null

                override fun writeAsync(uuid: UUID, key: PersistentDataKey<String>, value: String) {
                    Thread.sleep(writeMillis)
                }
            })
        }

        override fun getSavedUUIDs(): Set<UUID> = emptySet()

        override fun doSave() {
            check(!failSave) { "save failed" }
            saves++
        }
    }

    private val key = mockk<PersistentDataKey<String>> {
        every { type } returns PersistentDataKeyType.STRING
    }

    private val logger = Logger.getLogger("ProfileHandlerShutdownTests")

    @Test
    fun allHandlersShareOneDeadline() {
        val handlers = listOf(TestHandler(writeMillis = 5_000), TestHandler(writeMillis = 5_000))
        handlers.forEach { it.write(UUID.randomUUID(), key, "stuck") }

        val start = System.nanoTime()
        shutdownDataHandlers(handlers, 400, TimeUnit.MILLISECONDS, logger)
        val elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)

        Assertions.assertTrue(elapsedMillis < 700, "one 400ms deadline for both, took ${elapsedMillis}ms")
        handlers.forEach { Assertions.assertEquals(1, it.saves, "each handler still saves") }
    }

    @Test
    fun oneHandlerFailingDoesNotSkipTheOther() {
        val failing = TestHandler(writeMillis = 0, failSave = true)
        val healthy = TestHandler(writeMillis = 0)

        shutdownDataHandlers(listOf(failing, healthy), 1, TimeUnit.SECONDS, logger)

        Assertions.assertEquals(1, healthy.saves)
    }
}
