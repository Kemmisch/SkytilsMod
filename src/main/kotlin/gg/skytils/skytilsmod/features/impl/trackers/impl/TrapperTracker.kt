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

package gg.skytils.skytilsmod.features.impl.trackers.impl

import gg.essential.universal.UChat
import gg.skytils.skytilsmod.Skytils
import gg.skytils.skytilsmod.core.structure.GuiElement
import gg.skytils.skytilsmod.features.impl.trackers.Tracker
import gg.skytils.skytilsmod.features.impl.trackers.impl.MythologicalTracker.BurrowDrop
import gg.skytils.skytilsmod.utils.*
import gg.skytils.skytilsmod.utils.graphics.ScreenRenderer
import gg.skytils.skytilsmod.utils.graphics.SmartFontRenderer
import gg.skytils.skytilsmod.utils.graphics.colors.CommonColors
import kotlinx.serialization.SerialName
import kotlinx.serialization.encodeToString
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import java.io.Reader
import java.io.Writer
import kotlinx.serialization.Serializable
import net.minecraftforge.client.event.ClientChatReceivedEvent
import net.minecraftforge.fml.common.eventhandler.EventPriority

object TrapperTracker : Tracker("trapper") {

    private val rarityRegex = Regex("§e\\[NPC] Trevor§f: §rYou can find your §.§.(?<rarity>\\w{1,13}) §fanimal near the .*")

    init {
        TrapperTrackerElement
    }

    enum class TrapperMob (
        val rarity: String,
        var foundTimes: Long = 0L
    ) {
        TRACKABLE("TRACKABLE"),
        UNTRACKABLE("UNTRACKABLE"),
        UNDETECTED("UNDETECTED"),
        ENDANGERED("ENDANGERED"),
        ELUSIVE("ELUSIVE");

        companion object {
            fun getFromRarity(name: String?): TrapperMob? {
                return TrapperMob.entries.find { it.rarity == name }
            }
        }
    }

    @SubscribeEvent
    fun onChat(event: ClientChatReceivedEvent) {
        if (!Utils.inSkyblock || SBInfo.mode != "farming_1" || event.type == 2.toByte()) return
        val formatted = event.message.formattedText
        val match = rarityRegex.find(formatted)
        if (match != null) {
            (TrapperMob.getFromRarity(match.groups.get("rarity")?.value) ?: return).foundTimes++
            markDirty<TrapperTracker>()
        }
    }

    override fun resetLoot() {
        TrapperMob.entries.forEach { it.foundTimes = 0L }
    }

    @Serializable
    data class TrackerSave(
        @SerialName("rarities")
        val mobs: Map<String, Long>
    )

    override fun read(reader: Reader) {
        val save = json.decodeFromString<TrackerSave>(reader.readText())
        TrapperMob.entries.forEach { it.foundTimes = save.mobs[it.rarity] ?: 0L }
    }

    override fun write(writer: Writer) {
        writer.write(
            json.encodeToString(
                TrackerSave(
                    TrapperMob.entries.associate { it.rarity to it.foundTimes }
                )
            )
        )
    }

    override fun setDefault(writer: Writer) {
        write(writer)
    }

    object TrapperTrackerElement : GuiElement("Trapper Tracker", x = 200, y = 140) {
        override fun render() {
            if (toggled && Utils.inSkyblock && SBInfo.mode == "farming_1") {
                val leftAlign = scaleX < sr.scaledWidth / 2f
                val alignment =
                    if (leftAlign) SmartFontRenderer.TextAlignment.LEFT_RIGHT else SmartFontRenderer.TextAlignment.RIGHT_LEFT
                ScreenRenderer.fontRenderer.drawString(
                    "§f${TrapperMob.entries[0].foundTimes}/§a${TrapperMob.entries[1].foundTimes}/§9${TrapperMob.entries[2].foundTimes}/§5${TrapperMob.entries[3].foundTimes}/§6${TrapperMob.entries[4].foundTimes}",
                    if (leftAlign) 0f else width.toFloat(),
                    0f,
                    CommonColors.YELLOW,
                    alignment,
                    textShadow
                )
            }
        }

        override fun demoRender() {

            val leftAlign = scaleX < sr.scaledWidth / 2f
            val alignment =
                if (leftAlign) SmartFontRenderer.TextAlignment.LEFT_RIGHT else SmartFontRenderer.TextAlignment.RIGHT_LEFT
            ScreenRenderer.fontRenderer.drawString(
                "§f500/§f240/§f90/§f40/§f10",
                if (leftAlign) 0f else width.toFloat(),
                0f,
                CommonColors.YELLOW,
                alignment,
                textShadow
            )

        }

        override val height: Int
            get() = ScreenRenderer.fontRenderer.FONT_HEIGHT * 17
        override val width: Int
            get() = ScreenRenderer.fontRenderer.getStringWidth("§f500/§f240/§f90/§f40/§f10")

        override val toggled: Boolean
            get() = Skytils.config.trapperInfo

        init {
            Skytils.guiManager.registerElement(this)
        }
    }
}