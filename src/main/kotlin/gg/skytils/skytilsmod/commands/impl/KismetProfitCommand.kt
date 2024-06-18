/*
 * Skytils - Hypixel Skyblock Quality of Life Mod
 * Copyright (C) 2020-2023 Skytils
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

package gg.skytils.skytilsmod.commands.impl

import gg.essential.universal.UChat
import gg.skytils.skytilsmod.Skytils.Companion.failPrefix
import gg.skytils.skytilsmod.Skytils.Companion.successPrefix
import gg.skytils.skytilsmod.commands.BaseCommand
import gg.skytils.skytilsmod.utils.NumberUtil
import net.minecraft.client.entity.EntityPlayerSP
import gg.skytils.skytilsmod.features.impl.handlers.AuctionData
import gg.skytils.skytilsmod.utils.Utils.isMarauder

object KismetProfitCommand : BaseCommand("kismetprofit", aliases = listOf("kismet")) {

    data class ItemDrop(val normalChance: Double, val masterChance: Double, val skyblockItemID: String?, val chestPrice: Int, val nU: Int, val mU: Int, val c: Int)

    private val seventhLoot = listOf(
        ItemDrop(0.54, 0.89, "AUTO_RECOMBOBULATOR", 10000000,33,33,1),
        ItemDrop(0.13, 0.18, "IMPLOSION_SCROLL", 50000000,35,38,1),
        ItemDrop(0.13, 0.18, "SHADOW_WARP_SCROLL", 50000000,35,38,1),
        ItemDrop(0.13, 0.18, "WITHER_SHIELD_SCROLL", 50000000,35,38,1),
        ItemDrop(0.1, 0.18295, "NECRON_HANDLE", 100000000,36,38,1),
        ItemDrop(4.29, 6.73, "RECOMBOBULATOR_3000", 6000000,25,25,1),
        ItemDrop(6.3, 7.0, "WITHER_BOOTS", 2500000,17,17,1),
        ItemDrop(3.43, 4.31, "WITHER_LEGGINGS", 6000000,25,25,1),
        ItemDrop(0.53, 0.95, "WITHER_CHESTPLATE", 10000000,31,31,1),
        ItemDrop(5.95, 6.73, "WITHER_HELMET", 4000000,21,21,1),
        ItemDrop(0.0, 0.08, "DARK_CLAYMORE", 150000000,100,36,1),
        ItemDrop(0.0, 0.33, "FIFTH_MASTER_STAR", 9000000,100,32,1),
        ItemDrop(0.0, 0.04, "NECRON_DYE", 10000000,100,20,1),
        ItemDrop(0.0, 0.33, "MASTER_SKULL_TIER_5", 32000000,100,25,1)/*,
        ItemDrop(5.27,5.88,"WITHER_CATALYST",2000000,16,16,1),//catalyst
        ItemDrop(5.27,5.85,"FUMING_POTATO_BOOK",2000000,17,17,1),//fuming
        ItemDrop(5.95,6.73,"WITHER_BLOOD",3000000,21,21,1),//wither blood
        ItemDrop(5.44,6.73,"WITHER_CLOAK",4500000,23,23,1),//wither cloak
        ItemDrop(15.7,17.15,"PRECURSOR_GEAR",2000000,14,14,1),//precgear
        ItemDrop(0.59,1.0,"ENCHANTED_BOOK-ULTIMATE_ONE_FOR_ALL-1",2000000,29,29,1),//ofa
        ItemDrop(0.0,0.3,"ENCHANTED_BOOK-THUNDERLORD-7",2000000,100,20,1),//TL7
        ItemDrop(9.91,11.52,"ENCHANTED_BOOK-ULTIMATE_SOUL_EATER-1",2000000,18,18,1),//SE
        ItemDrop(0.0,0.0,null,2000000,12,12,2),//combo2/npng
        ItemDrop(0.0,0.0,null,2000000,10,10,6),//LS,UltJ,Bank,Rej3,wis,ultW
        ItemDrop(0.0,0.0,null,2000000,8,8,2),//iq/ff7
        ItemDrop(0.32,0.3,null, 2000000, 6, 6, 3),//FISH*/
    )

    override fun getCommandUsage(player: EntityPlayerSP): String =
        "/kismetprofit (master)"

    override fun processCommand(player: EntityPlayerSP, args: Array<String>) {

        var averageDropProfit = 0.0

        for (drop in seventhLoot) {

            val dropChance = if (args.isEmpty()) drop.normalChance else drop.masterChance
            var itemProfit = ((AuctionData.lowestBINs[drop.skyblockItemID] ?: 0.0) - (drop.chestPrice * (if (isMarauder) 0.8 else 1.0))) * dropChance / 100.0
            if (itemProfit <= 0.0) {
                itemProfit = 0.0
            }
            averageDropProfit += itemProfit
        }
        val kismetPrice = AuctionData.lowestBINs["KISMET_FEATHER"] ?: 0.0
        val averageProfit = averageDropProfit - kismetPrice
        if (kismetPrice == 0.0) {
            UChat.chat("$failPrefix §cKismet Price is 0.")
        }
        else {
            UChat.chat("$successPrefix §fKismet Feathers are currently: §6${NumberUtil.nf.format(kismetPrice)}")
            if (isMarauder) {
                UChat.chat("$successPrefix §6Paul - Marauder Perk")
            }
            UChat.chat("$successPrefix §fAverage profit per bedrock chest is currently: §6${NumberUtil.nf.format(averageDropProfit)}" + if (args.isEmpty()) " §b(F7)" else (" §b(M7)"))
            if (averageProfit > 0.0) {
            UChat.chat("$successPrefix §fAverage profit per reroll is currently: §a${NumberUtil.nf.format(averageProfit)}" + if (args.isEmpty()) " §b(F7)" else (" §b(M7)"))}
            else {
                UChat.chat("$successPrefix §fAverage loss per reroll is currently: §c${NumberUtil.nf.format(averageProfit)}" + if (args.isEmpty()) " §b(F7)" else (" §b(M7)"))}
            }
        }
    }










