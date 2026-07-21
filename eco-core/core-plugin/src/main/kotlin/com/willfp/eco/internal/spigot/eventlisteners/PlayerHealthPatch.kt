package com.willfp.eco.internal.spigot.eventlisteners

import com.willfp.eco.core.Eco
import com.willfp.eco.util.saveHealth
import com.willfp.eco.util.savedHealth
import org.bukkit.attribute.Attribute
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import kotlin.math.min

object PlayerHealthPatch : Listener {
    @EventHandler(priority = EventPriority.LOWEST)
    fun handlePlayerQuit(event: PlayerQuitEvent) {
        event.player.saveHealth()
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun handlePlayerJoin(event: PlayerJoinEvent) {
        if (!Eco.get().ecoPlugin.configYml.getBool("enable-health-fix")) {
            return
        }

        val player = event.player
        val plugin = Eco.get().ecoPlugin

        var fixDuration = plugin.configYml.getInt("health-fix-duration", 3)
        if (fixDuration <= 0) fixDuration = 3

        var fixInterval = plugin.configYml.getInt("health-fix-interval", 1)
        if (fixInterval <= 0) fixInterval = 1
        else if (fixInterval > fixDuration) fixInterval = fixDuration

        // ceil division
        var timesToRun = (fixDuration + fixInterval - 1) / fixInterval

        var previousMax = player.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0

        // Folia: the task reads and writes the player's health attribute, so it must run
        // on the player's owning region thread. Use the entity scheduler's fixed-rate task
        // (not the global scheduler) and cancel via the ScheduledTask handle.
        player.scheduler.runAtFixedRate(
            plugin,
            { task ->
                try {
                    if (timesToRun <= 0) {
                        task.cancel()
                        return@runAtFixedRate
                    }

                    if (!player.isOnline || player.isDead) return@runAtFixedRate

                    val currentMax = player.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0
                    if (currentMax != previousMax) {
                        // Max health changed - preserve health sensibly.
                        val newHealth = getNewHealth(player.health, player.savedHealth, currentMax, previousMax)
                        player.health = newHealth
                        previousMax = currentMax
                    }
                } catch (ex: Exception) {
                    plugin.logger.warning("Exception while monitoring health attribute: ${ex.message}")
                } finally {
                    timesToRun--
                }
            },
            {},
            1L,
            fixInterval * 20L
        )
    }

    fun getNewHealth(
        currentHealth: Double,
        savedHealth: Double,
        currentMax: Double,
        previousMax: Double
    ): Double {
        if (previousMax <= 0.0) {
            // Fallback: set to current max or saved health.
            return min(savedHealth, currentMax)
        }

        return if (currentHealth >= previousMax) {
            min(savedHealth, currentMax)
        } else {
            // Otherwise, preserve the same percentage of health.
            val percent = currentHealth / previousMax
            min(currentMax * percent, currentMax)
        }
    }
}
