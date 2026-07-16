package com.willfp.eco.internal.drops

import java.util.concurrent.ConcurrentHashMap
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class EcoFastCollatedDropQueue(player: Player) : EcoDropQueue(player) {
    override fun push() {
        // compute() is atomic for the key, which makes the whole merge exclusive against the
        // remove() that CollatedRunnable uses to claim the drops. A plain get/put pair could
        // merge into an entry the flush task had already claimed, mutating the list it was
        // reading and losing the drops that were merged in too late.
        COLLATED_MAP.compute(player) { _, fetched ->
            if (fetched == null) {
                CollatedDrops(items.toMutableList(), location, xp, hasTelekinesis)
            } else {
                fetched.addDrops(items)
                fetched.location = location
                fetched.addXp(xp)
                if (this.hasTelekinesis) {
                    fetched.forceTelekinesis()
                }

                fetched
            }
        }
    }

    class CollatedDrops(
        val drops: MutableList<ItemStack>,
        var location: Location,
        var xp: Int,
        var telekinetic: Boolean
    ) {
        fun addDrops(toAdd: List<ItemStack>): CollatedDrops {
            drops.addAll(toAdd)
            return this
        }

        fun addXp(xp: Int): CollatedDrops {
            this.xp += xp
            return this
        }

        fun forceTelekinesis() {
            telekinetic = true
        }
    }

    companion object {
        val COLLATED_MAP: MutableMap<Player, CollatedDrops> = ConcurrentHashMap()
    }
}