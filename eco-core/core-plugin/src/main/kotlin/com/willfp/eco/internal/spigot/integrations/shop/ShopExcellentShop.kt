package com.willfp.eco.internal.spigot.integrations.shop

import com.willfp.eco.core.integrations.shop.ShopIntegration
import com.willfp.eco.core.integrations.shop.ShopSellEvent
import com.willfp.eco.core.price.Price
import com.willfp.eco.core.price.impl.PriceEconomy
import com.willfp.eco.core.price.impl.PriceFree
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.inventory.ItemStack
import su.nightexpress.excellentshop.ShopAPI
import su.nightexpress.excellentshop.api.event.TransactionValidatedEvent
import su.nightexpress.excellentshop.api.product.TradeType

/*
 * Rewritten for the ExcellentShop 5.x API (su.nightexpress.excellentshop.*).
 *
 * The 4.x API this integration originally targeted (su.nightexpress.nexshop.*) was fully
 * replaced in 5.x:
 *  - Prices are no longer a single settable double; a product/transaction carries a
 *    multi-currency BalanceHolder.
 *  - The Bukkit ShopTransactionEvent was removed; transactions now expose
 *    TransactionPreValidateEvent / TransactionValidatedEvent / TransactionCompletedEvent.
 *
 * We hook TransactionValidatedEvent (fired after validation, before completion, and
 * cancellable) to apply eco's sell multiplier. Because the payout is multi-currency, we
 * scale every currency in each item's BalanceHolder by the same factor eco computes.
 */
class ShopExcellentShop : ShopIntegration {
    override fun getSellEventAdapter(): Listener {
        return ExcellentShopSellEventListeners
    }

    override fun getUnitValue(itemStack: ItemStack, player: Player): Price {
        val virtualShop = ShopAPI.getVirtualShop() ?: return PriceFree()
        val product = virtualShop.getBestProductFor(
            itemStack.clone().apply {
                amount = 1
            }, TradeType.SELL
        ) ?: return PriceFree()

        return PriceEconomy(
            product.getFinalSellPrice(player)
        )
    }

    override fun isSellable(itemStack: ItemStack, player: Player): Boolean {
        val virtualShop = ShopAPI.getVirtualShop() ?: return false
        val product = virtualShop.getBestProductFor(
            itemStack, TradeType.SELL
        ) ?: return false
        return product.isSellable && product.getFinalSellPrice(player) > 0
    }

    object ExcellentShopSellEventListeners : Listener {
        @EventHandler
        fun shopEventToEcoEvent(event: TransactionValidatedEvent) {
            val transaction = event.transaction

            if (transaction.type != TradeType.SELL) {
                return
            }

            val items = transaction.itemsList
            if (items.isEmpty()) {
                return
            }

            val player = transaction.player

            // Total sell worth summed across every currency in the transaction.
            val oldTotal = transaction.calculateWorth().balanceMap.values.sum()
            if (oldTotal <= 0.0) {
                return
            }

            val previewItem = items.first().product().preview.clone()
            val ecoEvent = ShopSellEvent(player, PriceEconomy(oldTotal), previewItem)
            Bukkit.getPluginManager().callEvent(ecoEvent)

            val newTotal = ecoEvent.value.getValue(player) * ecoEvent.multiplier
            if (newTotal == oldTotal) {
                return
            }

            val factor = newTotal / oldTotal

            // Apply eco's multiplier by scaling each item's per-currency price.
            for (item in items) {
                val holder = item.price()
                for ((currency, amount) in HashMap(holder.balanceMap)) {
                    holder.set(currency, amount * factor)
                }
            }
        }
    }

    override fun getPluginName(): String {
        return "ExcellentShop"
    }
}
