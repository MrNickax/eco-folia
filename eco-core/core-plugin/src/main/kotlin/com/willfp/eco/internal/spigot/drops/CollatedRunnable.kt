package com.willfp.eco.internal.spigot.drops

import com.willfp.eco.core.EcoPlugin
import com.willfp.eco.internal.drops.EcoDropQueue
import com.willfp.eco.internal.drops.EcoFastCollatedDropQueue
import org.bukkit.Bukkit

class CollatedRunnable(plugin: EcoPlugin) {
    init {
        plugin.scheduler.runTimer({
            for ((key, value) in EcoFastCollatedDropQueue.COLLATED_MAP) {
                Bukkit.getRegionScheduler().run(plugin, value.location) {
                    val queue = EcoDropQueue(key)
                        .setLocation(value.location)
                        .addItems(value.drops)
                        .addXP(value.xp)

                    if (value.telekinetic) {
                        queue.forceTelekinesis()
                    }

                    queue.push()
                }

                EcoFastCollatedDropQueue.COLLATED_MAP.remove(key)
            }
            EcoFastCollatedDropQueue.COLLATED_MAP.clear()
        }, 1, 1)
    }
}