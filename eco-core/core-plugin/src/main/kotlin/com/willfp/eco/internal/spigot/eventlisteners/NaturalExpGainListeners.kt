package com.willfp.eco.internal.spigot.eventlisteners

import com.willfp.eco.core.events.NaturalExpGainEvent
import org.bukkit.Bukkit
import org.bukkit.entity.ThrownExpBottle
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.ExpBottleEvent
import org.bukkit.event.player.PlayerExpChangeEvent
import java.util.concurrent.ConcurrentHashMap

class NaturalExpGainListenersPaper : Listener {
    @EventHandler
    fun onEvent(event: PlayerExpChangeEvent) {
        val source = event.source

        if (source is ThrownExpBottle) {
            return
        }

        val ecoEvent = NaturalExpGainEvent(event)
        Bukkit.getPluginManager().callEvent(ecoEvent)
    }
}

class NaturalExpGainListenersSpigot : Listener {
    private val events = ConcurrentHashMap.newKeySet<NaturalExpGainBuilder>()

    @EventHandler
    fun playerChange(event: PlayerExpChangeEvent) {
        val removed = events.removeIf { searchBuilder ->
            val location = searchBuilder.location ?: return@removeIf false

            if (location.world != event.player.location.world) {
                return@removeIf false
            }

            searchBuilder.reason == NaturalExpGainBuilder.BuildReason.BOTTLE &&
                    location.distanceSquared(event.player.location) <= 52
        }

        if (removed) {
            return
        }

        val builder = NaturalExpGainBuilder(NaturalExpGainBuilder.BuildReason.PLAYER)
        builder.event = event
        builder.push()
    }

    @EventHandler
    fun onExpBottle(event: ExpBottleEvent) {
        val builtEvent = NaturalExpGainBuilder(NaturalExpGainBuilder.BuildReason.BOTTLE)
        builtEvent.location = event.entity.location
        events.add(builtEvent)
    }
}