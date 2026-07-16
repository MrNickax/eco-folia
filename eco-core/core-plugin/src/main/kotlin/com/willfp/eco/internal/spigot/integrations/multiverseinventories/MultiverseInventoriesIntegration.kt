package com.willfp.eco.internal.spigot.integrations.multiverseinventories

import com.willfp.eco.core.EcoPlugin
import com.willfp.eco.core.events.ArmorChangeEvent
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.mvplugins.multiverse.inventories.event.WorldChangeShareHandlingEvent

class MultiverseInventoriesIntegration(
    private val plugin: EcoPlugin
): Listener {
    @EventHandler
    fun onWorldChange(event: WorldChangeShareHandlingEvent) {
        val player = event.player
        val before = player.inventory.armorContents.toMutableList()

        // The player is mid-teleport between worlds here; their entity scheduler follows them
        // across the move, whereas the global region scheduler would read their inventory from
        // a thread that doesn't own them.
        player.scheduler.run(
            plugin,
            { _ ->
                val after = player.inventory.armorContents.toMutableList()

                Bukkit.getPluginManager().callEvent(
                    ArmorChangeEvent(player, before, after)
                )
            },
            null
        )
    }
}