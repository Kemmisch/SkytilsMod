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

import gg.skytils.skytilsmod.Skytils
import gg.skytils.skytilsmod.commands.BaseCommand
import net.minecraft.client.entity.EntityPlayerSP

object JoinCommand : BaseCommand("join") {

    enum class Instances (val aliases: List<String>, val command: String) {
        ENTRANCE(listOf("e","0","entrance"),"CATACOMBS_ENTRANCE"),
        F1(listOf("f1","1","floor1"),"CATACOMBS_FLOOR_ONE"),
        F2(listOf("f2","2","floor2"),"CATACOMBS_FLOOR_TWO"),
        F3(listOf("f3","3","floor3"),"CATACOMBS_FLOOR_THREE"),
        F4(listOf("f4","4","floor4"),"CATACOMBS_FLOOR_FOUR"),
        F5(listOf("f5","5","floor5"),"CATACOMBS_FLOOR_FIVE"),
        F6(listOf("f6","6","floor6"),"CATACOMBS_FLOOR_SIX"),
        F7(listOf("f7","7","floor7"),"CATACOMBS_FLOOR_SEVEN"),
        M1(listOf("m1","8","master1"),"MASTER_CATACOMBS_FLOOR_ONE"),
        M2(listOf("m2","9","master2"),"MASTER_CATACOMBS_FLOOR_TWO"),
        M3(listOf("m3","10","master3"),"MASTER_CATACOMBS_FLOOR_THREE"),
        M4(listOf("m4","11","master4"),"MASTER_CATACOMBS_FLOOR_FOUR"),
        M5(listOf("m5","12","master5"),"MASTER_CATACOMBS_FLOOR_FIVE"),
        M6(listOf("m6","13","master6"),"MASTER_CATACOMBS_FLOOR_SIX"),
        M7(listOf("m7","14","master7"),"MASTER_CATACOMBS_FLOOR_SEVEN"),
        BASIC(listOf("k1","15","basic"),"KUUDRA_NORMAL"),
        HOT(listOf("k2","16","hot"),"KUUDRA_HOT"),
        BURNING(listOf("k3","17","burning"),"KUUDRA_BURNING"),
        FIERY(listOf("k4","18","fiery","f"),"KUUDRA_FIERY"),
        INFERNAL(listOf("k5","19","infernal","i"),"KUUDRA_INFERNAL");

        companion object {
            fun getByAlias(text: String): String? {
                return Instances.entries.find {text in it.aliases}?.command
            }
        }
    }

    override fun getCommandUsage(player: EntityPlayerSP): String =
        "/join f2/m7/fiery/k2/e"

    override fun processCommand(player: EntityPlayerSP, args: Array<String>) {
        if (args.isEmpty()) return
        val instance = Instances.getByAlias(args.joinToString(""))
        Skytils.sendMessageQueue.add("/joininstance ${instance ?: return}")
        }
    }










