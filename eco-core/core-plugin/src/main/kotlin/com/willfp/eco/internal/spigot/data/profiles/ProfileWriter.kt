package com.willfp.eco.internal.spigot.data.profiles

import com.willfp.eco.core.EcoPlugin
import com.willfp.eco.core.data.keys.PersistentDataKey
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level

/*
The profile writer exists as an optimization to batch writes to the database.

This is necessary because values frequently change multiple times per tick,
and we don't want to write to the database every time a value changes.

Instead, we only commit the last value that was set every interval (default 1 tick).
 */


class ProfileWriter(
    private val plugin: EcoPlugin,
    private val handler: ProfileHandler
) {
    private val saveInterval = plugin.configYml.getInt("save-interval").toLong()
    private val autosaveInterval: Long
    private val valuesToWrite = ConcurrentHashMap<WriteRequest<*>, Any>()

    init {
        // Live servers keep their old config.yml (30 minutes), so the cap is enforced here
        val configured = plugin.configYml.getInt("autosave-interval").toLong()
        autosaveInterval = autosaveIntervalTicks(configured)
        if (configured != autosaveInterval) {
            plugin.logger.warning(
                "autosave-interval $configured is outside $MIN_AUTOSAVE_INTERVAL-$MAX_AUTOSAVE_INTERVAL ticks, " +
                        "using $autosaveInterval"
            )
        }
    }

    fun <T : Any> write(uuid: UUID, key: PersistentDataKey<T>, value: T) {
        valuesToWrite[WriteRequest(uuid, key)] = value
    }

    /**
     * Hand every pending value to its data handler; also called on shutdown so none are lost.
     */
    fun flush() {
        val iterator = valuesToWrite.iterator()

        while (iterator.hasNext()) {
            val (request, value) = iterator.next()
            iterator.remove()

            val dataHandler = if (request.key.isSavedLocally) handler.localHandler else handler.defaultHandler

            // Pass the value to the data handler; one bad write must not drop the rest
            try {
                @Suppress("UNCHECKED_CAST")
                dataHandler.write(request.uuid, request.key as PersistentDataKey<Any>, value)
            } catch (e: Exception) {
                plugin.logger.log(Level.SEVERE, "Could not write ${request.key.key} for ${request.uuid}", e)
            }
        }
    }

    fun startTickingSaves() {
        plugin.scheduler.runTimer(20, saveInterval) {
            flush()
        }
    }

    fun startTickingAutosave() {
        plugin.scheduler.runTimer(autosaveInterval, autosaveInterval) {
            try {
                if (handler.localHandler.shouldAutosave()) {
                    handler.localHandler.save()
                }
            } catch (e: Exception) {
                plugin.logger.log(Level.SEVERE, "Could not autosave data.yml", e)
            }
        }
    }

    private data class WriteRequest<T>(val uuid: UUID, val key: PersistentDataKey<T>)
}

/** Longest allowed data.yml autosave interval: 5 minutes, even if config.yml asks for more. */
internal const val MAX_AUTOSAVE_INTERVAL = 6000L

/** Shortest allowed data.yml autosave interval: 1 second (a period of 0 would never schedule). */
internal const val MIN_AUTOSAVE_INTERVAL = 20L

internal fun autosaveIntervalTicks(configured: Long): Long =
    configured.coerceIn(MIN_AUTOSAVE_INTERVAL, MAX_AUTOSAVE_INTERVAL)

val PersistentDataKey<*>.isSavedLocally: Boolean
    get() = this.isLocal || EcoPlugin.getPlugin(this.key.namespace)?.isUsingLocalStorage == true
