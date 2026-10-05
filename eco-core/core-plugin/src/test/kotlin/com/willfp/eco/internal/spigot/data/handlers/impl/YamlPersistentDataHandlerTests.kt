package com.willfp.eco.internal.spigot.data.handlers.impl

import com.willfp.eco.core.PluginLike
import com.willfp.eco.core.config.ConfigType
import com.willfp.eco.core.config.interfaces.LoadableConfig
import com.willfp.eco.core.config.updating.ConfigHandler
import com.willfp.eco.core.data.keys.PersistentDataKey
import com.willfp.eco.core.data.keys.PersistentDataKeyType
import com.willfp.eco.internal.config.EcoLoadableConfig
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.logging.Logger
import org.bukkit.NamespacedKey
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

internal class YamlPersistentDataHandlerTests {
    @TempDir
    lateinit var dir: File

    private val plugin by lazy {
        object : PluginLike {
            override fun getDataFolder() = dir
            override fun getLogger(): Logger = Logger.getLogger("YamlPersistentDataHandlerTests")
            override fun getConfigHandler() = object : ConfigHandler {
                override fun saveAllConfigs() = Unit
                override fun updateConfigs() = Unit
                override fun addConfig(config: LoadableConfig) = Unit
            }
        }
    }

    private fun dataYml(): EcoLoadableConfig {
        File(dir, "data.yml").writeText("")
        return EcoLoadableConfig(ConfigType.YAML, "data", plugin, "", javaClass, false)
    }

    private val keys = (0 until 8).map { index ->
        mockk<PersistentDataKey<String>> {
            every { key } returns NamespacedKey("test", "key$index")
            every { type } returns PersistentDataKeyType.STRING
        }
    }

    @Test
    fun concurrentFirstWritesFromBothHandlersKeepEveryKey() {
        val dataYml = dataYml()
        // eco always runs a local yaml handler next to the default one; with data-handler: yaml both share data.yml
        val local = YamlPersistentDataHandler(dataYml)
        val default = YamlPersistentDataHandler(dataYml)
        val uuids = (1..300).map { UUID.randomUUID() }

        for (uuid in uuids) {
            for ((index, key) in keys.withIndex()) {
                (if (index % 2 == 0) local else default).write(uuid, key, "value$index")
            }
        }
        local.shutdown(30, TimeUnit.SECONDS)
        default.shutdown(30, TimeUnit.SECONDS)

        val missing = uuids.sumOf { uuid ->
            keys.count { dataYml.getStringOrNull("player.$uuid.${it.key}") == null }
        }
        Assertions.assertEquals(0, missing, "keys lost when a new player's section was created twice")
    }

    @Test
    fun saveWritesThePlayerDataToDisk() {
        val dataYml = dataYml()
        val handler = YamlPersistentDataHandler(dataYml)
        val uuid = UUID.randomUUID()

        handler.write(uuid, keys[0], "saved")
        handler.shutdown(30, TimeUnit.SECONDS)

        val reloaded = EcoLoadableConfig(ConfigType.YAML, "data", plugin, "", javaClass, false)
        Assertions.assertEquals("saved", reloaded.getStringOrNull("player.$uuid.${keys[0].key}"))
    }
}
