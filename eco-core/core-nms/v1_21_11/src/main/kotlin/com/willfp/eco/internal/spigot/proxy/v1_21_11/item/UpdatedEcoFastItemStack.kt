package com.willfp.eco.internal.spigot.proxy.v1_21_11.item

import com.willfp.eco.internal.spigot.proxy.common.mergeIfNeeded
import com.willfp.eco.internal.spigot.proxy.common.modern.ModernEcoFastItemStack
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.enchantment.ItemEnchantments
import org.bukkit.craftbukkit.enchantments.CraftEnchantment
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemStack
import kotlin.math.max

open class UpdatedEcoFastItemStack(bukkit: ItemStack) : ModernEcoFastItemStack(bukkit) {

    override fun getEnchants(checkStored: Boolean): Map<Enchantment, Int> {
        return try {
            val enchantments = handle.get(DataComponents.ENCHANTMENTS) ?: ItemEnchantments.EMPTY

            val map = mutableMapOf<Enchantment, Int>()

            for ((enchantment, level) in enchantments.entrySet()) {
                val bukkitEnchant = CraftEnchantment.minecraftHolderToBukkit(enchantment)
                map[bukkitEnchant] = level
            }

            if (checkStored) {
                val stored = handle.get(DataComponents.STORED_ENCHANTMENTS) ?: return map

                for ((enchantment, level) in stored.entrySet()) {
                    val bukkitEnchant = CraftEnchantment.minecraftHolderToBukkit(enchantment)
                    map[bukkitEnchant] = max(map.getOrDefault(bukkitEnchant, 0), level)
                }
            }

            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    override fun apply() {
        // For 1.21.11, don't use the old setPdc API
        // Just sync the handle back to bukkit without PDC manipulation
        unwrap().mergeIfNeeded(handle)
    }
}