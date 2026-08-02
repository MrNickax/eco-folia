package com.willfp.eco.internal.drops

import com.willfp.eco.core.Eco
import com.willfp.eco.core.drops.DropQueue
import com.willfp.eco.core.events.DropQueuePushEvent
import com.willfp.eco.core.integrations.antigrief.AntigriefManager
import com.willfp.eco.util.TelekinesisUtils
import com.willfp.eco.util.giveExpAndApplyMending
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.EntityType
import org.bukkit.entity.ExperienceOrb
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.util.Vector

open class EcoDropQueue(val player: Player) : DropQueue() {
    val items = mutableListOf<ItemStack>()
    var xp: Int = 0
    var location: Location
    var hasTelekinesis = false

    override fun addItem(item: ItemStack): DropQueue {
        items.add(item)
        return this
    }

    override fun addItems(itemStacks: Collection<ItemStack>): DropQueue {
        items.addAll(itemStacks)
        return this
    }

    override fun addXP(amount: Int): DropQueue {
        xp += amount
        return this
    }

    override fun setLocation(location: Location): DropQueue {
        this.location = location
        return this
    }

    override fun forceTelekinesis(): DropQueue {
        hasTelekinesis = true
        return this
    }

    override fun push() {
        if (!hasTelekinesis) {
            hasTelekinesis = TelekinesisUtils.testPlayer(player)
        }

        if (hasTelekinesis && !AntigriefManager.canPickupItem(player, location)) {
            hasTelekinesis = false
        }

        val pushEvent = DropQueuePushEvent(player, items.toMutableList(), location, xp, hasTelekinesis)
        Bukkit.getServer().pluginManager.callEvent(pushEvent)

        if (pushEvent.isCancelled) {
            return
        }

        items.clear()
        items.addAll(pushEvent.items)
        xp = pushEvent.xp

        val world = location.world!!
        location = location.add(0.5, 0.5, 0.5)
        items.removeIf { itemStack: ItemStack -> itemStack.type == Material.AIR }
        if (hasTelekinesis) {
            if (!items.isEmpty()) {
                // The inventory has to be touched on the player's own thread, so the leftover is
                // computed here and only the spawning of what did not fit is handed to the region.
                val leftover = player.inventory.addItem(*items.toTypedArray())
                spawn(world, location, leftover.values.filterNotNull(), 0)
            }
            if (xp > 0)
                player.giveExpAndApplyMending(xp, true)
        } else {
            spawn(world, location, items, xp)
        }
    }

    /**
     * Spawn [drops] and, if positive, an orb worth [xp] at [at] in [world].
     *
     * Adding an entity to a world has to happen on the thread that owns the destination region,
     * which is not necessarily the thread pushing the queue: the drop location is arbitrary and
     * frequently belongs to a different region to the player (the queue is pushed from the
     * player's thread). Spawning inline fails Folia's tick thread check and the drops are lost,
     * so anything outside the current region is dispatched to the region that owns it.
     */
    private fun spawn(
        world: World,
        at: Location,
        drops: Collection<ItemStack>,
        xp: Int
    ) {
        if (drops.isEmpty() && xp <= 0) {
            return
        }

        // The queue keeps mutating its own state, and the spawn may run a tick later, so both the
        // location and the items are snapshotted rather than captured by reference.
        val target = at.clone()
        val toDrop = drops.toList()

        val spawn = Runnable {
            for (drop in toDrop) {
                world.dropItem(target, drop).velocity = Vector()
            }

            if (xp > 0) {
                val orb = world.spawnEntity(target, EntityType.EXPERIENCE_ORB) as ExperienceOrb
                orb.experience = xp
            }
        }

        // Already on the owning thread in the common case (drops at the player), where going
        // through the scheduler would needlessly delay the drops by a tick.
        if (Bukkit.isOwnedByCurrentRegion(target)) {
            spawn.run()
        } else {
            Bukkit.getRegionScheduler().execute(Eco.get().ecoPlugin, target, spawn)
        }
    }

    init {
        location = player.location
    }
}