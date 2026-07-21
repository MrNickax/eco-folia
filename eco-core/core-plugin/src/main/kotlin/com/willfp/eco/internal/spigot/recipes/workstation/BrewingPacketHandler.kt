package com.willfp.eco.internal.spigot.recipes.workstation

import com.willfp.eco.core.EcoPlugin
import com.willfp.eco.core.packet.PacketEvent
import com.willfp.eco.core.packet.PacketListener
import com.willfp.eco.core.recipe.workstation.BrewingRecipe
import com.willfp.eco.core.recipe.workstation.WorkstationRecipes
import com.willfp.eco.internal.spigot.proxies.WorkstationPacketProxy
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.block.BrewingStand
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryType

class BrewingPacketHandler(private val plugin: EcoPlugin) : PacketListener, Listener {

    private val pendingBrews = mutableMapOf<Location, ScheduledTask>()
    private val progressTasks = mutableMapOf<Location, ScheduledTask>()

    init {
        WorkstationRecipes.registerBrewCancelHook { cancelBrew(it) }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onShiftClickIngredient(event: InventoryClickEvent) {
        if (event.inventory.type != InventoryType.BREWING) return
        if (!event.isShiftClick) return
        val player = event.whoClicked as? Player ?: return
        val location = event.inventory.location ?: return
        if (location in pendingBrews) return
        // Folia: brewing stand block state must be read on the block's owning region.
        Bukkit.getRegionScheduler().run(plugin, location) { _ ->
            val brewer = (location.block.state as? BrewingStand)?.inventory ?: return@run
            val ingredient = brewer.ingredient ?: return@run
            val recipe = WorkstationRecipes.getAll(BrewingRecipe::class.java)
                .firstOrNull {
                    it.ingredient.matches(ingredient) &&
                    (0..2).any { slot -> it.base.matches(brewer.getItem(slot)) }
                } ?: return@run
            scheduleBrew(location, recipe, player)
        }
    }

    override fun onReceive(event: PacketEvent) {
        val slotNum = plugin.getProxy(WorkstationPacketProxy::class.java)
            .getContainerClickSlot(event.packet) ?: return

        val player = event.player
        if (player.openInventory.topInventory.type != InventoryType.BREWING) return

        val cursor = player.itemOnCursor
        if (cursor == null || cursor.type.isAir) return

        if (slotNum == 3) {
            val recipe = WorkstationRecipes.getAll(BrewingRecipe::class.java)
                .firstOrNull { it.ingredient.matches(cursor) } ?: return
            event.isCancelled = true
            // Folia: mutating the player's open inventory/cursor runs on the player's region.
            player.scheduler.run(plugin, { _ ->
                val topInventory = player.openInventory.topInventory
                if (topInventory.type != InventoryType.BREWING) return@run
                val toPlace = cursor.clone().apply { amount = 1 }
                topInventory.setItem(3, toPlace)
                if (cursor.amount <= 1) player.setItemOnCursor(null)
                else cursor.amount--
                player.updateInventory()
                val location = topInventory.location?.block?.location ?: return@run
                scheduleBrew(location, recipe, player)
            }, null)
        } else if (slotNum in 0..2) {
            val matches = WorkstationRecipes.getAll(BrewingRecipe::class.java)
                .any { it.base.matches(cursor) }
            if (!matches) return
            event.isCancelled = true
            player.scheduler.run(plugin, { _ ->
                val topInventory = player.openInventory.topInventory
                if (topInventory.type != InventoryType.BREWING) return@run
                val current = topInventory.getItem(slotNum)
                if (current != null && !current.type.isAir) return@run
                val toPlace = cursor.clone().apply { amount = 1 }
                topInventory.setItem(slotNum, toPlace)
                if (cursor.amount <= 1) player.setItemOnCursor(null)
                else cursor.amount--
                player.updateInventory()
            }, null)
        }
    }

    fun cancelBrew(location: Location) {
        pendingBrews.remove(location)?.cancel()
        progressTasks.remove(location)?.cancel()
    }

    private fun scheduleBrew(location: Location, recipe: BrewingRecipe, animPlayer: Player? = null) {
        cancelBrew(location)

        val brewTime = recipe.brewTime
        val player = animPlayer
        val nmsPacket = plugin.getProxy(WorkstationPacketProxy::class.java)
        val containerId = if (player != null) nmsPacket.getOpenContainerId(player) else -1

        if (containerId >= 0 && player != null) {
            val totalSteps = (brewTime / 10).coerceAtLeast(1)
            var step = 0
            // Folia: the progress packets target the player, so run on the player's region.
            val progressTask = player.scheduler.runAtFixedRate(plugin, { task ->
                step++
                if (step > totalSteps || player.openInventory.topInventory.type != InventoryType.BREWING) {
                    task.cancel()
                    progressTasks.remove(location)
                    return@runAtFixedRate
                }
                val normalized = (400 * (totalSteps - step) / totalSteps).coerceAtLeast(0)
                nmsPacket.sendContainerDataPacket(player, containerId, normalized)
            }, null, 1L, 10L)
            if (progressTask != null) {
                progressTasks[location] = progressTask
            }
        }

        // Folia: the delayed completion mutates the brewing stand block, so run on the
        // block's owning region.
        pendingBrews[location] = Bukkit.getRegionScheduler().runDelayed(plugin, location, { _ ->
            pendingBrews.remove(location)
            progressTasks.remove(location)?.cancel()

            val state = location.block.state as? BrewingStand ?: return@runDelayed
            val brewer = state.inventory
            val ingredient = brewer.ingredient ?: return@runDelayed
            if (!recipe.ingredient.matches(ingredient)) return@runDelayed

            val matchedSlots = (0..2).filter { recipe.base.matches(brewer.getItem(it)) }
            if (matchedSlots.isEmpty()) return@runDelayed

            val remainingIngredient = ingredient.clone()
            if (remainingIngredient.amount <= 1) brewer.ingredient = null
            else { remainingIngredient.amount--; brewer.ingredient = remainingIngredient }

            val item = recipe.output?.clone() ?: return@runDelayed
            matchedSlots.forEach { brewer.setItem(it, item.clone()) }
            WorkstationRecipes.fireBrewCompleted(location, recipe, matchedSlots)
        }, brewTime.toLong())
    }
}
