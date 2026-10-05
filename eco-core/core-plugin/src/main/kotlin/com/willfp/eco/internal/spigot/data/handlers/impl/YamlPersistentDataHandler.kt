package com.willfp.eco.internal.spigot.data.handlers.impl

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.eco.core.config.interfaces.LoadableConfig
import com.willfp.eco.core.config.wrapper.LoadableConfigWrapper
import com.willfp.eco.core.data.handlers.DataTypeSerializer
import com.willfp.eco.core.data.handlers.PersistentDataHandler
import com.willfp.eco.core.data.keys.PersistentDataKey
import com.willfp.eco.core.data.keys.PersistentDataKeyType
import com.willfp.eco.internal.config.EcoLoadableConfig
import com.willfp.eco.internal.spigot.EcoSpigotPlugin
import java.math.BigDecimal
import java.util.UUID

class YamlPersistentDataHandler(
    private val dataYml: LoadableConfig
) : PersistentDataHandler("yaml") {
    constructor(plugin: EcoSpigotPlugin) : this(plugin.dataYml)

    init {
        PersistentDataKeyType.STRING.registerSerializer(this, object : YamlSerializer<String>() {
            override fun read(config: Config, key: String) = config.getStringOrNull(key)
        })

        PersistentDataKeyType.BOOLEAN.registerSerializer(this, object : YamlSerializer<Boolean>() {
            override fun read(config: Config, key: String) = config.getBoolOrNull(key)
        })

        PersistentDataKeyType.INT.registerSerializer(this, object : YamlSerializer<Int>() {
            override fun read(config: Config, key: String) = config.getIntOrNull(key)
        })

        PersistentDataKeyType.DOUBLE.registerSerializer(this, object : YamlSerializer<Double>() {
            override fun read(config: Config, key: String) = config.getDoubleOrNull(key)
        })

        PersistentDataKeyType.STRING_LIST.registerSerializer(this, object : YamlSerializer<List<String>>() {
            override fun read(config: Config, key: String) = config.getStringsOrNull(key)
        })

        PersistentDataKeyType.CONFIG.registerSerializer(this, object : YamlSerializer<Config>() {
            override fun read(config: Config, key: String) = config.getSubsectionOrNull(key)
        })

        PersistentDataKeyType.BIG_DECIMAL.registerSerializer(this, object : YamlSerializer<BigDecimal>() {
            override fun read(config: Config, key: String) = config.getBigDecimalOrNull(key)
        })
    }

    override fun getSavedUUIDs(): Set<UUID> {
        return dataYml.getSubsection("player").getKeys(false)
            .map { UUID.fromString(it) }
            .toSet()
    }

    override fun shouldAutosave(): Boolean {
        return true
    }

    override fun doSave() {
        // Runs off the main thread (executor or shutdown), so it can afford the durable save
        synchronized(dataYml) {
            dataYml.saveDurably()
        }
    }

    /*
    Both yaml handlers (local and default) share data.yml and write from their own executors.
    Setting a nested key creates missing sections check-then-act, so two first writes for a
    new player could each create the section and one would replace the other. Every access
    to data.yml from here goes through its monitor.
     */
    private abstract inner class YamlSerializer<T: Any>: DataTypeSerializer<T>() {
        protected abstract fun read(config: Config, key: String): T?

        final override fun readAsync(uuid: UUID, key: PersistentDataKey<T>): T? {
            return synchronized(dataYml) {
                read(dataYml, "player.$uuid.${key.key}")
            }
        }

        final override fun writeAsync(uuid: UUID, key: PersistentDataKey<T>, value: T) {
            synchronized(dataYml) {
                dataYml.set("player.$uuid.${key.key}", value)
            }
        }
    }
}

private fun LoadableConfig.saveDurably() {
    when (this) {
        is EcoLoadableConfig -> this.saveDurably()
        is LoadableConfigWrapper -> this.handle.saveDurably()
        else -> this.save()
    }
}
