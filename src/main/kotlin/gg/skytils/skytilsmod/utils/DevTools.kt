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

package gg.skytils.skytilsmod.utils

import codes.som.anthony.koffee.modifiers.varargs
import gg.essential.universal.UChat
import gg.skytils.skytilsmod.utils.SBInfo.lastOpenContainerName
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.settings.KeyBinding
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.InputEvent.KeyInputEvent
import org.lwjgl.input.Keyboard

object DevTools {
    val toggles = HashMap<String, Boolean>()
    var allToggle = false
        private set
    var devKey = KeyBinding("Dev Tool", Keyboard.KEY_Y, "Skytils")
    val validModes = listOf("containerName")
    var mode = "containerName"


    @SubscribeEvent
    fun onInput(event: KeyInputEvent) {
        val key = Keyboard.getEventKey()
        if (key != devKey.keyCode || Keyboard.isRepeatEvent() || !devKey.isPressed) return
        GuiScreen.setClipboardString(lastOpenContainerName.toString())
        UChat.chat(lastOpenContainerName.toString())

    }

    fun setMode(args: Array<String>) {
        if (args[2] in validModes) {
            mode = args[2]
            UChat.chat("Dev tool mode set to $mode")
        } else {
            UChat.chat("Invalid mode $mode")
            UChat.chat("Valid modes are: ${validModes.toString()}")
        }
    }

    fun getToggle(toggle: String): Boolean {
        return if (allToggle) allToggle else toggles.getOrDefault(toggle.lowercase(), false)
    }

    fun toggle(toggle: String) {
        if (toggle.lowercase() == "all") {
            allToggle = !allToggle
            return
        }
        toggles[toggle.lowercase()]?.let {
            toggles[toggle.lowercase()] = !it
        } ?: kotlin.run {
            toggles[toggle.lowercase()] = true
        }
    }

}

fun printDevMessage(string: String, toggle: String) {
    if (DevTools.getToggle(toggle)) UChat.chat(string)
}

fun printDevMessage(string: String, vararg toggles: String) {
    if (toggles.any { DevTools.getToggle(it) }) UChat.chat(string)
}