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

package gg.skytils.skytilsmod.features.impl.mining

import gg.essential.universal.UChat
import gg.skytils.skytilsmod.Skytils
import gg.skytils.skytilsmod.Skytils.Companion.mc
import gg.skytils.skytilsmod.core.tickTimer
import gg.skytils.skytilsmod.events.impl.skyblock.LocationChangeEvent
import gg.skytils.skytilsmod.utils.ItemUtil.getSkyBlockItemID
import net.minecraft.entity.item.EntityArmorStand
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

object GlaciteTunnels {
    val helmetMap = mapOf("ARMOR_OF_YOG_HELMET" to "Umber", "MINERAL_HELMET" to "Tungsten", "LAPIS_ARMOR_HELMET" to "Lapis")
    var inShaft = false

    init {
        tickTimer(20, repeats = true) {
            if (!inShaft || !Skytils.config.corpseHelper) return@tickTimer
            findCorpses()
        }
    }

    @SubscribeEvent
    fun onLocationChange(event: LocationChangeEvent) {
        if (event.packet.mode.orElse(null) == "mineshaft") {
            inShaft = true
            UChat.chat("Joined shaft")
        } else {
            inShaft = false
        }
    }

    fun findCorpses() {
        UChat.chat("Looking for corpses")
        mc.theWorld.loadedEntityList.filter { it is EntityArmorStand && !it.isInvisible}.forEach {
            val entity = it as EntityArmorStand
            UChat.chat("visible found ${entity.position}")
            UChat.chat(entity.getCurrentArmor(3))
            if (getSkyBlockItemID(entity.getCurrentArmor(3)) in helmetMap) {
                val corpseType = helmetMap[getSkyBlockItemID(entity.getCurrentArmor(3))]
                UChat.chat("$corpseType @ ${entity.posX} ${entity.posY} ${entity.posZ}")
            }
        }
    }
}