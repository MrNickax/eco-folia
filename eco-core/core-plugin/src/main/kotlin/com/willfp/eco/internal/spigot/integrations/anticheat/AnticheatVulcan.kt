package com.willfp.eco.internal.spigot.integrations.anticheat

import com.willfp.eco.core.integrations.anticheat.AnticheatIntegration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import me.frep.vulcan.api.event.VulcanFlagEvent
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

class AnticheatVulcan : AnticheatIntegration, Listener {
    // Folia: exempt/unexempt and the violation event run on different region threads.
    private val exempt: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    override fun getPluginName(): String {
        return "Vulcan"
    }

    override fun exempt(player: Player) {
        exempt.add(player.uniqueId)
    }

    override fun unexempt(player: Player) {
        exempt.remove(player.uniqueId)
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private fun onViolate(event: VulcanFlagEvent) {
        if (!exempt.contains(event.player.uniqueId)) {
            return
        }
        event.isCancelled = true
    }
}