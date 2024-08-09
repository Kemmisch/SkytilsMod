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

import gg.essential.universal.UChat
import gg.skytils.skytilsmod.Skytils
import gg.skytils.skytilsmod.Skytils.Companion.mc
import gg.skytils.skytilsmod.utils.Utils
import net.minecraft.client.settings.KeyBinding
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.InputEvent
import org.lwjgl.input.Keyboard
import gg.skytils.skytilsmod.utils.ItemUtil.getSkyBlockItemID
import org.lwjgl.input.Mouse

object QuickBind {
    var quickBindKey = KeyBinding("Quick Bind", -97,"Skytils")
    var slot: Int? = null

    @SubscribeEvent
    fun onKeyInput(event: InputEvent.KeyInputEvent) {
        if (Keyboard.getEventKey() == quickBindKey.keyCode && !Keyboard.isRepeatEvent() && Keyboard.getEventKeyState()) handleInput()
    }

    @SubscribeEvent
    fun onMouseInput(event: InputEvent.MouseInputEvent) {
        if (Mouse.getEventButton()-100 == quickBindKey.keyCode && Mouse.getEventButtonState()) handleInput()

    }

    private fun handleInput() {
        slot = null
        if (!Utils.inSkyblock || Skytils.config.quickBindRegex == "") return
        mc.thePlayer.inventory.mainInventory.slice(0..7).forEach {
            if (getSkyBlockItemID(it)?.matches(Regex(Skytils.config.quickBindRegex)) == true) {
                slot = mc.thePlayer.inventory.mainInventory.slice(0..7).indexOf(it)
            }
        }
        KeyBinding.onTick(mc.gameSettings.keyBindsHotbar[slot ?: return].keyCode)
    }
}