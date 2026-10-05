package com.willfp.eco.internal.spigot.data.profiles

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class ProfileWriterTests {
    @Test
    fun autosaveIntervalIsCappedAtFiveMinutes() {
        Assertions.assertEquals(6000L, autosaveIntervalTicks(36000), "old 30 minute default -> 5 minutes")
        Assertions.assertEquals(6000L, autosaveIntervalTicks(6001), "just above the cap -> 5 minutes")
        Assertions.assertEquals(6000L, autosaveIntervalTicks(Long.MAX_VALUE), "huge -> 5 minutes")
    }

    @Test
    fun autosaveIntervalKeepsShorterValues() {
        Assertions.assertEquals(6000L, autosaveIntervalTicks(6000), "cap itself is kept")
        Assertions.assertEquals(1200L, autosaveIntervalTicks(1200), "1 minute is kept")
        Assertions.assertEquals(20L, autosaveIntervalTicks(20), "floor itself is kept")
    }

    @Test
    fun autosaveIntervalIsAtLeastOneSecond() {
        Assertions.assertEquals(20L, autosaveIntervalTicks(1), "1 tick -> 1 second")
        Assertions.assertEquals(20L, autosaveIntervalTicks(0), "0 would never schedule -> 1 second")
        Assertions.assertEquals(20L, autosaveIntervalTicks(-5), "negative -> 1 second")
    }
}
