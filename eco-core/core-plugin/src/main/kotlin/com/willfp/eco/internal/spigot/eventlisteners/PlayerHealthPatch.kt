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

object PlayerHealthPatch: Listener {
    @EventHandler(priority = EventPriority.LOWEST)
    fun handlePlayerQuit(event: PlayerQuitEvent) {
        event.player.saveHealth()
    }

    @EventHandler
    fun handlePlayerJoin(event: PlayerJoinEvent) {
        if (Eco.get().ecoPlugin.configYml.getBool("enable-health-fix")) {
            val player = event.player
            val plugin = Eco.get().ecoPlugin

            player.scheduler.runDelayed(
                plugin,
                {
                    if (player.isOnline && !player.isDead) {
                        player.health = min(
                            player.savedHealth,
                            player.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0
                        )
                    }
                },
                {},
                5L
            )
        }
    }
}