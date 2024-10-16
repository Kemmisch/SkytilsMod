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

import gg.skytils.skytilsmod.Skytils
import gg.skytils.skytilsmod.core.structure.GuiElement
import gg.skytils.skytilsmod.utils.SBInfo
import gg.skytils.skytilsmod.utils.Utils
import gg.skytils.skytilsmod.utils.graphics.ScreenRenderer
import gg.skytils.skytilsmod.utils.graphics.SmartFontRenderer.TextAlignment
import gg.skytils.skytilsmod.utils.graphics.colors.CommonColors
import net.minecraftforge.event.world.WorldEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import org.lwjgl.input.Keyboard
import net.minecraft.client.settings.KeyBinding
import net.minecraftforge.fml.common.gameevent.InputEvent.KeyInputEvent
import gg.skytils.skytilsmod.utils.stripControlCodes
import net.minecraftforge.client.event.ClientChatReceivedEvent

object QuickWarp {

    data class Warp(val location: String, val validMode: String?, val timeSent: Long, val timeValid: Long, val reason: String, val display: String, val show: Boolean = true) {}

    var currentWarp: Warp? = null


    var keybindQuickWarp = KeyBinding("Quick Warp", Keyboard.KEY_Z,"Skytils")

    enum class ReasonPriorities (val reason: String) {
        INQUISITOR("inq"),
        BURROW_GUESS("burrow"),
        TRAPPER_DESERT("desert"),
        TRAPPER_TREVOR("trevor"),

    }


    fun setWarp(newWarp: Warp) {
            currentWarp = newWarp

    }

    init {
        QuickWarpElement()
    }

    @SubscribeEvent
    fun onWorldChange(event: WorldEvent.Unload) {
        currentWarp = null
    }

    @SubscribeEvent
    fun onChat(event: ClientChatReceivedEvent) {
        if (currentWarp == null) return
        val unformatted = event.message.unformattedText.stripControlCodes()
        if (unformatted == "Warping...") currentWarp = null
    }


    @SubscribeEvent
    fun onInput(event: KeyInputEvent) {
        if (!Utils.inSkyblock || currentWarp == null) return
        if (Keyboard.isRepeatEvent() || ((System.currentTimeMillis() - currentWarp?.timeSent!!) > currentWarp?.timeValid!!)) return
        val key = Keyboard.getEventKey()

        if (keybindQuickWarp.keyCode == key && keybindQuickWarp.isPressed) {
            Skytils.sendMessageQueue.add("/warp ${currentWarp?.location ?: return}")
            currentWarp = null
        }
    }


    class QuickWarpElement : GuiElement("Quick Warp Display",x=170,y=40) {
        override fun render() {
            if (toggled && Utils.inSkyblock && currentWarp?.show ?: return && (currentWarp?.validMode
                    ?: return) == (SBInfo.mode
                    ?: return) && ((System.currentTimeMillis() - currentWarp?.timeSent!!) < currentWarp?.timeValid!!)
            ) {
                val leftAlign = scaleX < sr.scaledWidth / 2f
                val alignment = if (leftAlign) TextAlignment.LEFT_RIGHT else TextAlignment.RIGHT_LEFT
                val text = "§6Quick Warp§f: ${currentWarp?.display ?: return}"
                ScreenRenderer.fontRenderer.drawString(
                    text,
                    if (leftAlign) 0f else width.toFloat(),
                    0.2f,
                    CommonColors.WHITE,
                    alignment,
                    textShadow
                )
            }
        }

        override fun demoRender() {
            ScreenRenderer.fontRenderer.drawString(
                "§6Quick Warp§f: §eDesert Settlement",
                0f,
                0f,
                CommonColors.WHITE,
                TextAlignment.LEFT_RIGHT,
                textShadow
            )
        }

        override val height: Int
            get() = ScreenRenderer.fontRenderer.FONT_HEIGHT
        override val width: Int
            get() = ScreenRenderer.fontRenderer.getStringWidth("§6Quick Warp§f: §eDesert Settlement")

        override val toggled: Boolean
            get() = (Skytils.config.gardenQuickWarp || Skytils.config.trapperQuickWarp || Skytils.config.inquisitorQuickWarp || Skytils.config.burrowEstimationQuickWarp)

        init {
            Skytils.guiManager.registerElement(this)
        }
    }
}