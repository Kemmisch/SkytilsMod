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
package gg.skytils.skytilsmod.features.impl.farming

import gg.essential.universal.UChat
import gg.essential.universal.UMatrixStack
import gg.skytils.skytilsmod.Skytils
import gg.skytils.skytilsmod.Skytils.Companion.failPrefix
import gg.skytils.skytilsmod.Skytils.Companion.mc
import gg.skytils.skytilsmod.Skytils.Companion.prefix
import gg.skytils.skytilsmod.Skytils.Companion.successPrefix
import gg.skytils.skytilsmod.core.DataFetcher
import gg.skytils.skytilsmod.core.SoundQueue
import gg.skytils.skytilsmod.core.tickTimer
import gg.skytils.skytilsmod.events.impl.PacketEvent.ReceiveEvent
import gg.skytils.skytilsmod.features.impl.handlers.MayorInfo
import gg.skytils.skytilsmod.features.impl.misc.QuickWarp
import gg.skytils.skytilsmod.utils.*
import net.minecraft.client.gui.GuiChat
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.item.EntityArmorStand
import net.minecraft.network.play.server.S45PacketTitle
import net.minecraft.util.ChatComponentText
import net.minecraft.util.Vec3
import net.minecraftforge.client.event.ClientChatReceivedEvent
import net.minecraftforge.client.event.GuiScreenEvent
import net.minecraftforge.client.event.RenderLivingEvent
import net.minecraftforge.client.event.RenderWorldLastEvent
import net.minecraftforge.event.world.WorldEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import org.lwjgl.input.Mouse
import java.awt.Color
import kotlin.math.*

object FarmingFeatures {
    val hungerHikerItems = LinkedHashMap<String, String>()
    var trapperCooldownExpire = -1L
    var animalFound = false
    var animalType: String? = null
    var animalRarity: String? = null
    var animalLocation: String? = null
    var timeAlive = -1L
    private var mobCoords: Vec3? = null
    private var theodoliteUsed = false
    private const val theodoliteError = 2.5
    private val mobRegex =
        Regex("§8\\[§7Lv\\d\\d?§8] §c(§.)?(?<rarity>\\w{1,13}) (?<type>\\w{1,10})§r §.[\\d,]+§f/§.[\\d,]+§.❤")
    private val trevorRegex =
        Regex("§e\\[NPC] Trevor§f: §rYou can find your §.§l(?<rarity>TRACKABLE|UNTRACKABLE|UNDETECTED|ENDANGERED|ELUSIVE) §fanimal near the (§.)?(?<location>(Desert Settlement|Oasis|Desert Mountain|Overgrown Mushroom Cave|Glowing Mushroom Cave|Mushroom Gorge))§f.§r")
    var acceptTrapperCommand = ""

    private val targetHeightRegex =
        Regex("^The target is around (?<blocks>\\d+) blocks (?<type>above|below), at a (?<angle>\\d+) degrees angle!$")
    var targetMinY = 0
    var targetMaxY = 0

    var c = mutableListOf<Quad>()

    data class Quad(
        val c1: Vec3,
        val c2: Vec3,
        val c3: Vec3,
        val c4: Vec3,
        val width: Int,
        var color: Color,
        var matrixStack: UMatrixStack,
        var alphaMultiplier: Float = 1F
    )

    private fun rotatex2d(theta: Float, px: Double, pz: Double, ox: Double, oz: Double): Double {
        return cos(theta) * (px - ox) - sin(theta) * (pz - oz) + ox
    }

    private fun rotatez2d(theta: Float, px: Double, pz: Double, ox: Double, oz: Double): Double {
        return sin(theta) * (px - ox) - cos(theta) * (pz - oz) + oz
    }


    @SubscribeEvent(receiveCanceled = true)
    fun onChat(event: ClientChatReceivedEvent) {
        if (!Utils.inSkyblock || SBInfo.mode != "farming_1" || event.type == 2.toByte()) return

        val formatted = event.message.formattedText
        val unformatted = event.message.unformattedText.stripControlCodes()

        if (Skytils.config.acceptTrapperTask) {
            if (formatted.contains("§a§l[YES]")) {
                val listOfSiblings = event.message.siblings
                acceptTrapperCommand =
                    listOfSiblings.find { it.unformattedText.contains("[YES]") }?.chatStyle?.chatClickEvent?.value ?: ""
                UChat.chat("$prefix §bOpen chat then click anywhere on screen to accept the task.")
            }
        }


        if (Skytils.config.talbotsTheodoliteHelper || Skytils.config.trapperPing || Skytils.config.trapperSolver) {

            val mobMatch = trevorRegex.find(formatted)
            if (mobMatch != null) {
                //UChat.chat("Mob Found.")
                timeAlive = System.currentTimeMillis()
                animalFound = false


                if (Skytils.config.trapperPing) {
                    trapperCooldownExpire =
                        System.currentTimeMillis() + if (MayorInfo.currentMayor == "Finnegan") 30000 else 60000
                }
                if (Skytils.config.talbotsTheodoliteHelper) {
                    targetMinY = -1
                    targetMaxY = -1
                }
                if (Skytils.config.trapperSolver || Skytils.config.trapperInfo) {
                    animalRarity = mobMatch.groups["rarity"]!!.value.toTitleCase()
                }
                if (Skytils.config.trapperQuickWarp) {
                    animalLocation = mobMatch.groups["location"]!!.value
                    if (animalLocation == "Desert Settlement") {
                        QuickWarp.pushWarp(
                            QuickWarp.Warp(
                                "desert",
                                "farming_1",
                                System.currentTimeMillis(),
                                5000,
                                "desert",
                                "§eDesert Settlement"
                            )
                        )
                        //UChat.chat("Warp pushed. - settlement")
                    } else if (animalLocation == "Oasis") {
                        QuickWarp.pushWarp(
                            QuickWarp.Warp(
                                "desert",
                                "farming_1",
                                System.currentTimeMillis(),
                                10000,
                                "desert",
                                "§bOasis"
                            )
                        )
                        //UChat.chat("Warp pushed. - oasis")
                    }
                }
            }

            if (Skytils.config.trapperSolver || Skytils.config.trapperInfo) {
                if (unformatted.startsWith("§r§cThe animal sent you flying!")) {
                    animalType = "Sheep"
                } else if (unformatted.startsWith("§r§cThe pig made a stench, you feel nauseous!")) {
                    animalType = "Pig"
                } else if (unformatted.startsWith("§r§cThe animal went invisible because you got too close!")) {
                    animalType = "Cow"
                }
            }

            if (unformatted.startsWith("You are at the exact height!") && Skytils.config.talbotsTheodoliteHelper) {
                event.isCanceled = true
                UChat.chat("You are at the exact height! (${mc.thePlayer.posY.toInt()}")
            }

            if (unformatted.startsWith("Return to the Trapper soon to get a new animal to hunt!")) {
                if (trapperCooldownExpire > 0 && System.currentTimeMillis() > trapperCooldownExpire && Skytils.config.trapperPing) {
                    Utils.playLoudSound("note.pling", 1.0)
                    UChat.chat("$prefix §bTrapper cooldown has already expired!")
                    trapperCooldownExpire = -1
                }

                if (Skytils.config.trapperQuickWarp) {
                    QuickWarp.pushWarp(
                        QuickWarp.Warp(
                            "trapper",
                            "farming_1",
                            System.currentTimeMillis(),
                            5000,
                            "trevor",
                            "§2Trevor"
                        )
                    )
                    //UChat.chat("Warp pushed. - trevor")
                }

                animalFound = true
                animalLocation = null
                animalRarity = null
                animalType = null
                mobCoords = null
                theodoliteUsed = false
                c.clear()
            }

            if (Skytils.config.talbotsTheodoliteHelper) {
                val match = targetHeightRegex.find(unformatted)
                if (match != null) {
                    val angle = match.groups["angle"]!!.value.toInt()
                    val blocks = match.groups["blocks"]!!.value.toInt()
                    val below = match.groups["type"]!!.value == "below"
                    val y = mc.thePlayer.posY
                    val targetY = y.toInt() + if (below) -blocks else blocks
                    val minTargetY = targetY - theodoliteError
                    val maxTargetY = targetY + theodoliteError
                    val minY = blocks - theodoliteError
                    val maxY = blocks + theodoliteError
                    var passes = try {
                        Skytils.config.trapperSolverPasses.toInt()
                    } catch (e: NumberFormatException) {
                        4
                    }
                    if (0 >= passes) passes = 16


                    event.isCanceled = true
                    UChat.chat("§r§aThe target is at §6Y §r§e$minTargetY-$maxTargetY §7($blocks blocks ${if (below) "below" else "above"}, $angle angle)")
                    if (Skytils.config.trapperSolver) {

                        c.clear()
                        val x = mc.thePlayer.posX
                        val z = mc.thePlayer.posZ

                        val minAngle =
                            Math.toRadians(if (below) (90 - angle + theodoliteError) else (90 - angle - theodoliteError))
                        val maxAngle =
                            Math.toRadians(if (below) (90 - angle - theodoliteError) else (90 - angle + theodoliteError))

                        val x1 = (x + (tan(minAngle) * (maxY)))
                        val x2 = (x + (tan(maxAngle) * (maxY)))
                        val x3 = (x + (tan(maxAngle) * (minY)))
                        val x4 = (x + (tan(minAngle) * (minY)))

                        for (i in 1..passes) {
                            val R = Math.toRadians((360 * i / passes).toDouble()).toFloat()

                            val c1 = Vec3(
                                rotatex2d(R, x1, z, x, z),
                                if (below) minTargetY else maxTargetY,
                                rotatez2d(R, x1, z, x, z)
                            )
                            val c2 = Vec3(
                                rotatex2d(R, x2, z, x, z),
                                if (below) minTargetY else maxTargetY,
                                rotatez2d(R, x2, z, x, z)
                            )
                            val c3 = Vec3(
                                rotatex2d(R, x3, z, x, z),
                                if (!below) minTargetY else maxTargetY,
                                rotatez2d(R, x3, z, x, z)
                            )
                            val c4 = Vec3(
                                rotatex2d(R, x4, z, x, z),
                                if (!below) minTargetY else maxTargetY,
                                rotatez2d(R, x4, z, x, z)
                            )

                            c.add(Quad(c1, c2, c3, c4, 3, Skytils.config.trapperSolverColor, UMatrixStack(), 0.5F))
                        }
                        theodoliteUsed = true
                    }
                }
            }
        }

        if (Skytils.config.hungryHikerSolver && formatted.startsWith("§e[NPC] Hungry Hiker§f: ")) {
            if (hungerHikerItems.isEmpty()) {
                UChat.chat("$failPrefix §cSkytils did not load any solutions.")
                DataFetcher.reloadData()
                return
            }
            val solution = hungerHikerItems.getOrDefault(hungerHikerItems.keys.find { s: String ->
                unformatted.contains(s)
            }, null)
            tickTimer(4) {
                if (solution != null) {
                    UChat.chat("$successPrefix §aThe Hiker needs: §l§2 $solution§a!")
                } else {
                    if (unformatted.contains("I asked for") || unformatted.contains("The food I want")) {
                        println("Missing Hiker item: $unformatted")
                        mc.thePlayer.addChatMessage(
                            ChatComponentText(
                                String.format(
                                    "$failPrefix §cSkytils couldn't determine the Hiker item. There were %s solutions loaded.",
                                    hungerHikerItems.size
                                )
                            )
                        )
                    }
                }
            }
        }
    }

    @SubscribeEvent
    fun onReceivePacket(event: ReceiveEvent) {
        if (!Utils.inSkyblock) return
        if (event.packet is S45PacketTitle) {
            val packet = event.packet
            if (packet.message != null) {
                val unformatted = packet.message.unformattedText.stripControlCodes()
                if (Skytils.config.hideFarmingRNGTitles && unformatted.contains("DROP!")) {
                    event.isCanceled = true
                }
            }
        }
    }

    @SubscribeEvent
    fun onTick(event: TickEvent.ClientTickEvent) {
        if (!Utils.inSkyblock || !Skytils.config.trapperPing || event.phase != TickEvent.Phase.START) return
        if (trapperCooldownExpire > 0 && mc.thePlayer != null) {
            if (System.currentTimeMillis() > trapperCooldownExpire && animalFound) {
                trapperCooldownExpire = -1
                UChat.chat("$prefix §bTrapper cooldown has now expired!")
                for (i in 0..4) {
                    SoundQueue.addToQueue(SoundQueue.QueuedSound("note.pling", 1f, ticks = i * 4, isLoud = true))
                }
            }
        }
    }

    @SubscribeEvent
    fun onWorldChange(event: WorldEvent.Unload) {
        trapperCooldownExpire = -1
        animalType = null
        animalFound = false
        animalRarity = null
        animalLocation = null
        timeAlive = -1L
        c.clear()
    }

    @SubscribeEvent
    fun onMouseInputPost(event: GuiScreenEvent.MouseInputEvent.Post) {
        if (!Utils.inSkyblock) return
        if (Mouse.getEventButton() == 0 && event.gui is GuiChat) {
            if (Skytils.config.acceptTrapperTask && acceptTrapperCommand.isNotBlank()) {
                Skytils.sendMessageQueue.add(acceptTrapperCommand)
                acceptTrapperCommand = ""
            }
        }
    }

    @SubscribeEvent
    fun onRenderLivingPre(event: RenderLivingEvent.Pre<EntityLivingBase>) {
        if (animalFound || !Utils.inSkyblock || SBInfo.mode != "farming_1" || !Skytils.config.trapperSolver || !event.entity.hasCustomName() || event.entity !is EntityArmorStand) return
        val entity = event.entity as EntityArmorStand
        val mobMatch = mobRegex.find(entity.customNameTag) ?: return
        if (entity.isInvisible) animalType = null
        if ((mobMatch.groups.get("rarity")?.value
                ?: return) != animalRarity || mobMatch.groups.get("type")?.value != (animalType
                ?: mobMatch.groups.get("type")?.value) || !entity.customNameTag.containsAny("/", "\\")
        ) return
        animalType = mobMatch.groups.get("type")?.value ?: return
        val (x, y, z) = RenderUtil.fixRenderPos(event.x, event.y, event.z)
        mobCoords = Vec3(x, y, z)
        RenderUtil.draw3DLine(
            mobCoords ?: return,
            mc.thePlayer.getPositionEyes(RenderUtil.getPartialTicks()),
            2,
            Skytils.config.trapperSolverColor,
            RenderUtil.getPartialTicks(),
            UMatrixStack()
        )
    }

    @SubscribeEvent
    fun onWorldRender(event: RenderWorldLastEvent) {
        if (c.isEmpty()) return
        for (quad in c) {
            RenderUtil.drawQuad(
                quad.c1,
                quad.c2,
                quad.c3,
                quad.c4,
                quad.width,
                quad.color,
                event.partialTicks,
                quad.matrixStack,
                quad.alphaMultiplier
            )
        }


    }


}