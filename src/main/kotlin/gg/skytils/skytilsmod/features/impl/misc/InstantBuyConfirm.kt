/*
 * Skytils - Hypixel Skyblock Quality of Life Mod
 * Copyright (C) 2020-2024 Skytils
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package gg.skytils.skytilsmod.features.impl.misc

import gg.essential.api.EssentialAPI
import gg.essential.universal.UChat
import gg.essential.universal.UResolution
import gg.skytils.skytilsmod.Skytils
import gg.skytils.skytilsmod.events.impl.GuiContainerEvent
import gg.skytils.skytilsmod.features.impl.handlers.AuctionData
import gg.skytils.skytilsmod.mixins.transformers.accessors.AccessorGuiContainer
import gg.skytils.skytilsmod.utils.*
import gg.skytils.skytilsmod.utils.ItemUtil.getItemLore
import gg.skytils.skytilsmod.utils.graphics.ScreenRenderer
import gg.skytils.skytilsmod.utils.graphics.SmartFontRenderer.TextAlignment
import gg.skytils.skytilsmod.utils.graphics.colors.CommonColors
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.settings.KeyBinding
import net.minecraft.inventory.ContainerChest
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

object InstantBuyConfirm {
    private var allowed = false
    private var flag = true

    @SubscribeEvent
    fun onGUIDrawnEvent(event: GuiContainerEvent.ForegroundDrawnEvent) {
        if (!Skytils.config.confirmInstantBuy) return
        if (event.container !is ContainerChest) return
        if (!flag) return

        if (event.chestName !="Confirm Instant Buy" || allowed) return else {
            val item = event.container.getSlot(13).stack ?: return
            val itemLore = getItemLore(item)
            if (itemLore.size <= 3) return
            val totalPrice = Regex("§7Price: §6(?<price>[^ ]{1,13}) coins").find(itemLore[5])?.groups?.get("price")?.value?.trim()?.replace(",","")?.toDouble() ?: return
            //UChat.chat(totalPrice)
            val unitPrice = Regex("§7Per unit: §6(?<price>[^ ]{1,13}) coins").find(itemLore[4])?.groups?.get("price")?.value?.trim()?.replace(",","")?.toDouble() ?: return
            //UChat.chat(unitPrice)
            val basePrice = AuctionData.lowestBINs[ItemUtil.getSkyBlockItemID(ItemUtil.getExtraAttributes(item) ?: return) ?: return] ?: return
            //UChat.chat(basePrice)
            val maxPercentAbove = try {if (Skytils.config.confirmInstantBuyPercent.toInt() > 0) Skytils.config.confirmInstantBuyPercent.toInt() else -1 } catch (e: NumberFormatException) { -1 }
            val maxPriceAbove = try {if (Skytils.config.confirmInstantBuyAbove.toInt() > 0) Skytils.config.confirmInstantBuyAbove.toInt() else -1 } catch (e: NumberFormatException) { -1 }
            //UChat.chat(maxPercentAbove)
            //UChat.chat(maxPriceAbove)


            if ((totalPrice <= maxPriceAbove || maxPriceAbove == -1) && (unitPrice < (basePrice * (1+maxPercentAbove/100)) || maxPercentAbove == -1)) allowed = true
        }
    }



    /*private fun drawChestProfit(chest: DungeonChest) {
        if (chest.items.size > 0) {
            val leftAlign = element.scaleX < UResolution.scaledWidth / 2f
            val alignment = if (leftAlign) TextAlignment.LEFT_RIGHT else TextAlignment.RIGHT_LEFT
            GlStateManager.color(1f, 1f, 1f, 1f)
            GlStateManager.disableLighting()
            var drawnLines = 1
            val profit = chest.profit

            ScreenRenderer.fontRenderer.drawString(
                chest.displayText + "§f: §" + (if (profit > 0) "a" else "c") + NumberUtil.nf.format(
                    profit
                ),
                if (leftAlign) element.scaleX else element.scaleX + element.width,
                element.scaleY,
                chest.displayColor,
                alignment,
                textShadow_
            )

            for (item in chest.items) {
                val line = item.item.displayName + "§f: §a" + NumberUtil.nf.format(item.value)
                ScreenRenderer.fontRenderer.drawString(
                    line,
                    if (leftAlign) element.scaleX else element.scaleX + element.width,
                    element.scaleY + drawnLines * ScreenRenderer.fontRenderer.FONT_HEIGHT,
                    CommonColors.WHITE,
                    alignment,
                    textShadow_
                )
                drawnLines++
            }
        }
    }*/

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onSlotClick(event: GuiContainerEvent.SlotClickEvent) {
        //UChat.chat(GuiScreen.isAltKeyDown())
        if (!Utils.inSkyblock || event.container !is ContainerChest) return
        if (Skytils.config.kismetRerollThreshold != 0 && event.chestName == "Confirm Instant Buy" && !allowed) {
            if (event.slotId == 13 && !GuiScreen.isAltKeyDown()) {
                event.isCanceled = true
                EssentialAPI.getNotifications()
                    .push(
                        "Blocked Instant Buy",
                        "The Item you tried to instant buy needs confirmation due to it's price. Either hold alt or click this notification.",
                        2f,
                        action = {
                            allowed = true
                        })
            }
        } else if (event.slotId == 13) allowed = false
    }
}