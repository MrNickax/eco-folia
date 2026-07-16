package com.willfp.eco.internal.gui.menu

import com.willfp.eco.core.gui.menu.Menu
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import java.util.Collections
import java.util.WeakHashMap

// Synchronized: every player's region thread registers, reads and unregisters menus
// concurrently. Concurrent writes to a bare HashMap-backed map can corrupt the table.
private val inventories = Collections.synchronizedMap(WeakHashMap<Inventory, RenderedInventory>())

object MenuHandler {
    fun registerInventory(
        inventory: Inventory,
        menu: EcoMenu,
        player: Player
    ): RenderedInventory {
        val rendered = RenderedInventory(menu, inventory, player)
        inventories[inventory] = rendered
        return rendered
    }

    fun unregisterInventory(inventory: Inventory) {
        inventories.remove(inventory)
    }
}

fun Inventory.asRenderedInventory(): RenderedInventory? =
    inventories[this]

fun Inventory.getMenu(): Menu? =
    this.asRenderedInventory()?.menu
