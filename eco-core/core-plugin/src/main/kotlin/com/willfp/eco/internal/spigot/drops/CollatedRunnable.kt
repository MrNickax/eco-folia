package com.willfp.eco.internal.spigot.drops

import com.willfp.eco.core.EcoPlugin
import com.willfp.eco.internal.drops.EcoDropQueue
import com.willfp.eco.internal.drops.EcoFastCollatedDropQueue

class CollatedRunnable(plugin: EcoPlugin) {
    init {
        plugin.scheduler.runTimer({
            for (key in EcoFastCollatedDropQueue.COLLATED_MAP.keys) {
                // Claim the collated drops before scheduling. remove() is atomic against the
                // compute() in EcoFastCollatedDropQueue.push(), so the value we get here is
                // exclusively ours: nothing can append to it while the task below reads it,
                // and drops pushed after this point are collated for the next flush instead
                // of being silently discarded.
                val value = EcoFastCollatedDropQueue.COLLATED_MAP.remove(key) ?: continue

                // The queue reads and mutates the player (location, inventory, XP, mending),
                // so it has to run on the thread that owns the player. Dispatching to the
                // region of the drop location instead would touch the player from a foreign
                // region thread, which Folia rejects.
                key.scheduler.run(plugin, {
                    val queue = EcoDropQueue(key)
                        .setLocation(value.location)
                        .addItems(value.drops)
                        .addXP(value.xp)

                    if (value.telekinetic) {
                        queue.forceTelekinesis()
                    }

                    queue.push()
                }, null)
            }
        }, 1, 1)
    }
}