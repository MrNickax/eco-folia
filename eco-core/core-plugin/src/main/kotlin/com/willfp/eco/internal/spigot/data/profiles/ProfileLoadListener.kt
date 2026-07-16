package com.willfp.eco.internal.spigot.data.profiles

import com.willfp.eco.core.EcoPlugin
import com.willfp.eco.util.PlayerUtils
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerLoginEvent
import org.bukkit.event.player.PlayerQuitEvent

class ProfileLoadListener(
    private val plugin: EcoPlugin,
    private val handler: ProfileHandler
) : Listener {
    @EventHandler(priority = EventPriority.LOWEST)
    fun onLogin(event: PlayerLoginEvent) {
        handler.unloadProfile(event.player.uniqueId)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onLeave(event: PlayerQuitEvent) {
        handler.unloadProfile(event.player.uniqueId)
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player

        // Reads the player's display name, so it has to run on the thread that owns them
        // rather than on the global region scheduler.
        player.scheduler.runDelayed(plugin, { PlayerUtils.updateSavedDisplayName(player) }, null, 5L)
    }
}
