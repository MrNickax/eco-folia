package com.willfp.eco.core.data.handlers

import com.willfp.eco.core.data.keys.PersistentDataKey
import com.willfp.eco.core.data.keys.PersistentDataKeyType
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class PersistentDataHandlerShutdownTests {
    /**
     * A handler whose writes take a while to apply and whose save snapshots what was applied.
     */
    private class SlowHandler(private val writeMillis: Long) : PersistentDataHandler("slow-test") {
        val applied = ConcurrentHashMap<UUID, String>()

        @Volatile
        var saved: Map<UUID, String>? = null

        @Volatile
        var saves = 0

        init {
            PersistentDataKeyType.STRING.registerSerializer(this, object : DataTypeSerializer<String>() {
                override fun readAsync(uuid: UUID, key: PersistentDataKey<String>): String? = applied[uuid]

                override fun writeAsync(uuid: UUID, key: PersistentDataKey<String>, value: String) {
                    Thread.sleep(writeMillis)
                    applied[uuid] = value
                }
            })
        }

        override fun getSavedUUIDs(): Set<UUID> = applied.keys

        override fun doSave() {
            saved = HashMap(applied)
            saves++
        }
    }

    private val key = mockk<PersistentDataKey<String>> {
        every { type } returns PersistentDataKeyType.STRING
    }

    @Test
    fun shutdownSavesWritesThatWereStillQueued() {
        val handler = SlowHandler(writeMillis = 300)
        val uuid = UUID.randomUUID()

        handler.write(uuid, key, "value")
        handler.shutdown()

        Assertions.assertEquals("value", handler.saved?.get(uuid), "a queued write must reach the final save")
    }

    @Test
    fun boundedShutdownGivesUpOnAStuckWriteButStillSaves() {
        val handler = SlowHandler(writeMillis = 5_000)
        val stuck = UUID.randomUUID()

        handler.write(stuck, key, "late")
        val start = System.nanoTime()
        val drained = handler.shutdown(100, TimeUnit.MILLISECONDS)
        val waitedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)

        Assertions.assertFalse(drained, "a write still running is reported")
        Assertions.assertTrue(waitedMillis < 4_000, "the wait is bounded, waited ${waitedMillis}ms")
        Assertions.assertNotNull(handler.saved, "what was applied is saved anyway")
        Assertions.assertNull(handler.saved?.get(stuck))
    }

    @Test
    fun shutdownTwiceSavesAgainWithoutFailing() {
        val handler = SlowHandler(writeMillis = 0)

        handler.shutdown()
        handler.shutdown()

        Assertions.assertEquals(2, handler.saves)
    }
}
