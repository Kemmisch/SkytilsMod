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
package gg.skytils.skytilsmod.features.impl.events

import com.google.common.collect.EvictingQueue
import gg.essential.universal.UChat
import gg.essential.universal.UMatrixStack
import gg.skytils.skytilsmod.Skytils
import gg.skytils.skytilsmod.Skytils.Companion.mc
import gg.skytils.skytilsmod.core.GuiManager
import gg.skytils.skytilsmod.core.SoundQueue
import gg.skytils.skytilsmod.core.structure.GuiElement
import gg.skytils.skytilsmod.events.impl.MainReceivePacketEvent
import gg.skytils.skytilsmod.events.impl.PacketEvent
import gg.skytils.skytilsmod.features.impl.events.GriffinBurrows.BurrowEstimation.lastParticleTrail
import gg.skytils.skytilsmod.features.impl.events.GriffinBurrows.BurrowEstimation.lastSoundTrail
import gg.skytils.skytilsmod.features.impl.events.GriffinBurrows.BurrowEstimation.otherGrassData
import gg.skytils.skytilsmod.features.impl.misc.QuickWarp
import gg.skytils.skytilsmod.features.impl.misc.QuickWarp.pushWarp
import gg.skytils.skytilsmod.utils.*
import gg.skytils.skytilsmod.utils.ItemUtil.getItemLore
import gg.skytils.skytilsmod.utils.graphics.ScreenRenderer
import net.minecraft.client.gui.inventory.GuiChest
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.entity.item.EntityArmorStand
import net.minecraft.init.Blocks
import net.minecraft.init.Items
import net.minecraft.inventory.ContainerChest
import net.minecraft.item.ItemStack
import net.minecraft.network.play.client.C07PacketPlayerDigging
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement
import net.minecraft.network.play.server.S04PacketEntityEquipment
import net.minecraft.network.play.server.S29PacketSoundEffect
import net.minecraft.network.play.server.S2APacketParticles
import net.minecraft.util.*
import net.minecraftforge.client.event.ClientChatReceivedEvent
import net.minecraftforge.client.event.GuiOpenEvent
import net.minecraftforge.client.event.RenderWorldLastEvent
import net.minecraftforge.event.world.WorldEvent
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent
import java.awt.Color
import java.time.Duration
import java.time.Instant
import kotlin.math.*
import kotlin.time.Duration.Companion.milliseconds

object GriffinBurrows {

    data class Inquisitor(var coords: Vec3, val spawnTime: Long, val spawner: String)

    val particleBurrows = hashMapOf<BlockPos, ParticleBurrow>()
    var lastDugParticleBurrow: BlockPos? = null
    val recentlyDugParticleBurrows = EvictingQueue.create<BlockPos>(5)
    val coordsRegex = Regex("§r§9Party §8> (?<rank>§.(\\[.*])?) ?(?<name>[^ §]{2,16})§f: §r[ xX:]{0,4}(?<x>[\\d-.]{1,7})[, yY:]{1,5}(?<y>[\\d-.]{1,7})[, zZ:]{1,5}(?<z>[\\d-.]{1,7}),? ?§?r?")

    var hasSpadeInHotbar = false
    var lastSpadeUse = -1L
    var lastInq = Inquisitor(Vec3(-2.5, 70.0, -69.5), 0,"null")
    var allowedWarps = linkedMapOf(Pair("Hub",true),Pair("Castle",false),Pair("Crypts",false),Pair("Museum",false),Pair("Wizard",false),Pair("Sirius",false))
    var menuOpened = false

    enum class WarpLocations (val coords: Vec3, val warpName: String, val displayName: String) {
        HUB( Vec3(-3.5, 70.5, -70.5),"hub","§bHub"),
        CASTLE( Vec3(-250.5, 130.5, 45.5), "castle", "§8Castle"),
        CRYPTS( Vec3(-162.5, 61.5, -100.5), "crypts", "§cCrypts"),
        MUSEUM( Vec3(-76.5, 76.5, 80.5), "museum", "§9Museum"),
        WIZARD( Vec3(42.5, 122.5, 69.5), "wizard", "§dWizard"),
        SIRIUS( Vec3(91.5, 75.5, 173.5), "da", "§5Sirius");

        companion object {
            fun getClosest (guess: Vec3,player: Vec3, allowedWarps: LinkedHashMap<String, Boolean>): WarpLocations? {
                var closest = HUB
                for (warp in WarpLocations.entries) {
                    //UChat.chat("compared")
                    if ((allowedWarps[warp.displayName] == true) && closest != warp && (closest.coords.getXZDistSq(guess) > warp.coords.getXZDistSq(guess)))
                        closest = warp
                }
                UChat.chat(closest.coords.getXZDistSq(guess) > guess.getXZDistSq(player))
                return if (closest.coords.getXZDistSq(guess) > guess.getXZDistSq(player)) null else closest
            }

            fun getFromDisplayName(name: String): WarpLocations? {
                return WarpLocations.entries.find {it.displayName == name}
            }
        }
    }


    init {
        MythoMobDisplay()
    }


    object BurrowEstimation {
        val arrows = mutableMapOf<Arrow, Instant>()
        val guesses = mutableMapOf<BurrowGuess, Instant>()
        val lastParticleTrail = mutableListOf<Vec3>()
        val lastSoundTrail = linkedSetOf<Pair<Vec3, Double>>()
        var lastTrailCreated = -1L

        fun getDistanceFromPitch(pitch: Double) =
            2805 * pitch - 1375

        val grassData by lazy {
            this::class.java.getResource("/assets/skytils/grassdata.txt")!!.readBytes()
        }

        val otherGrassData by lazy {
            this::class.java.getResource("/assets/skytils/hub_grass_heights.bin")!!.readBytes()
        }

        class Arrow(val directionVector: Vec3, val pos: Vec3)
    }

    @SubscribeEvent
    fun onGuiOpen(event: GuiOpenEvent) {
        if (event.gui !is GuiChest || !Utils.inSkyblock || !(Skytils.config.inquisitorQuickWarp || Skytils.config.burrowEstimationQuickWarp) || mc.thePlayer == null) return else {
            val container = (event.gui as GuiChest).inventorySlots as ContainerChest
            val containerName = container.lowerChestInventory.displayName.unformattedText
            //Code borrowed from SoopyV2 by @Soopyboo32
            if (containerName.startsWith("Hub Warps")) {
                for (item in container.inventoryItemStacks) {
                    val lore = getItemLore(item ?: continue)
                    if (lore.size < 2) continue
                    val warpLine = lore[1].stripControlCodes()
                    if (warpLine.startsWith("/warp")) {
                        warpLine.replace("/warp","")
                        if (lore.find {it.contains("Click to warp!")} != null) {
                            allowedWarps[warpLine] = true
                        }
                    }
                }
                menuOpened = true
                //allowedWarps.forEach {UChat.chat(it.toString())}
            }
        }
    }

    @SubscribeEvent
    fun onTick(event: ClientTickEvent) {
        if (event.phase != TickEvent.Phase.START) return
        hasSpadeInHotbar = mc.thePlayer != null && Utils.inSkyblock && (0..7).any {
            mc.thePlayer.inventory.getStackInSlot(it).isSpade
        }
        if (!Skytils.config.burrowEstimation) return
        BurrowEstimation.guesses.entries.removeIf { (_, instant) ->
            Duration.between(instant, Instant.now()).toMinutes() > 1
        }
        BurrowEstimation.arrows.entries.removeIf { (_, instant) ->
            Duration.between(instant, Instant.now()).toMillis() > 30_000L
        }
        if (!Skytils.config.experimentBurrowEstimation) return
        if (lastSoundTrail.size >= 2 && lastParticleTrail.size >= 2 && System.currentTimeMillis() - BurrowEstimation.lastTrailCreated > 1000) {
            printDevMessage("Trail found $lastParticleTrail", "griffinguess")
            printDevMessage("Sound trail $lastSoundTrail", "griffinguess")

            // chat did I get a 5 on the exam?
            // https://apcentral.collegeboard.org/media/pdf/statistics-formula-sheet-and-tables-2020.pdf
            val pitches = lastSoundTrail.map { it.second }
            val xMean = (lastSoundTrail.size - 1) / 2.0
            val xStd = sqrt(lastSoundTrail.indices.sumOf {
                (it - xMean) * (it - xMean)
            }) / (lastSoundTrail.size - 1)

            val yMean = pitches.average()
            val yStd = sqrt(pitches.sumOf {
                (it - yMean) * (it - yMean)
            }) / (pitches.size - 1)

            val numerator = lastSoundTrail.withIndex().sumOf { (i, pair) ->
                (i - xMean) * (pair.second - yMean)
            }

            val denominatorX = sqrt(lastSoundTrail.indices.sumOf { (it - xMean) * (it - xMean) })
            val denominatorY = sqrt(pitches.sumOf { (it - yMean) * (it - yMean) })

            val r = numerator / (denominatorX * denominatorY)

            val slope = r * yStd / xStd

            if (r < 0.95) UChat.chat("${Skytils.failPrefix} §cWarning: low correlation, r = $r. Burrow guess may be incorrect.")

            printDevMessage("Slope $slope, xbar $xMean, sx $xStd, ybar $yMean, sy $yStd, r $r", "griffinguess")

            val trail = lastParticleTrail.asReversed()

            // formula for distance guess comes from soopyboo32
            val distanceGuess = E / slope
            printDevMessage("Distance guess $distanceGuess", "griffinguess")

            val directionVector = trail[0].subtract(trail[1]).normalize()
            printDevMessage("Direction vector $directionVector", "griffinguess")

            val guessPos = trail.last().add(
                directionVector * distanceGuess
            )
            printDevMessage("Guess pos $guessPos", "griffinguess")

            // offset of 300 blocks for both x and z
            // x ranges from 195 to -281
            // z ranges from 207 to -233

            fun getIndex(x: Int, z: Int) = (x - -281) + (z - -233) * (195 - -281 + 1)

            val guess = BurrowGuess(guessPos.x.toInt(), otherGrassData.getOrNull(getIndex(guessPos.x.toInt(), guessPos.z.toInt()))?.toInt() ?: 0, guessPos.z.toInt())
            BurrowEstimation.guesses[guess] = Instant.now()

            lastParticleTrail.clear()
            BurrowEstimation.lastTrailCreated = -1
            lastSoundTrail.clear()

            if (Skytils.config.burrowEstimationQuickWarp) {
                if (!menuOpened) UChat.chat("${Skytils.failPrefix} Open the hub warp menu for burrow warps to work!")
                else {
                    UChat.chat("Pushed warp?")
                    val warpLoc = WarpLocations.getClosest(Vec3(guessPos.x, 0.0,guessPos.z),mc.thePlayer.positionVector, allowedWarps) ?: return
                    pushWarp(
                        QuickWarp.Warp(
                            warpLoc.warpName,
                            SkyblockIsland.Hub.mode,
                            System.currentTimeMillis(),
                            5000,
                            "burrow",
                            warpLoc.displayName
                        )
                    )
                    UChat.chat("Warp Pushed.")
                }
            }

        }
    }

    @SubscribeEvent(receiveCanceled = true, priority = EventPriority.HIGHEST)
    fun onChat(event: ClientChatReceivedEvent) {
        if (event.type == 2.toByte()) return
        val unformatted = event.message.unformattedText.stripControlCodes()
        if (Skytils.config.showGriffinBurrows &&
            (unformatted.startsWith("You died") || unformatted.startsWith("☠ You were killed") ||
                    unformatted.startsWith("You dug out a Griffin Burrow! (") ||
                    unformatted == "You finished the Griffin burrow chain! (4/4)")
        ) {
            if (lastDugParticleBurrow != null) {
                val particleBurrow =
                    particleBurrows[lastDugParticleBurrow] ?: return
                recentlyDugParticleBurrows.add(lastDugParticleBurrow)
                particleBurrows.remove(particleBurrow.blockPos)
                printDevMessage("Removed $particleBurrow", "griffin")
                lastDugParticleBurrow = null
            }
        }
        if (event.message.formattedText == "§r§6Poof! §r§eYou have cleared your griffin burrows!§r") {
            particleBurrows.clear()
            recentlyDugParticleBurrows.clear()
            lastDugParticleBurrow = null
            BurrowEstimation.guesses.clear()
            BurrowEstimation.arrows.clear()
            lastParticleTrail.clear()
            BurrowEstimation.lastTrailCreated = -1
            lastSpadeUse = -1
            lastSoundTrail.clear()
        }
        if (coordsRegex.matches(event.message.formattedText)) {
            val coordsPartyMessage = coordsRegex.find(event.message.formattedText)
            val senderName = coordsPartyMessage?.groups?.get("name")?.value?.trim() ?: return
            val senderRank = coordsPartyMessage.groups.get("rank")!!.value.trim()

            lastInq = Inquisitor(Vec3(coordsPartyMessage.groups.get("x")?.value?.trim()?.toDoubleOrNull() ?: return,
                coordsPartyMessage.groups.get("y")?.value?.trim()?.toDoubleOrNull() ?: return,
                coordsPartyMessage.groups.get("z")?.value?.trim()?.toDoubleOrNull() ?: return),System.currentTimeMillis(),senderName)
            UChat.chat("§6Inquisitor §ffound by $senderRank $senderName§r")
            if (senderName == mc.thePlayer.name || !Skytils.config.drawInquisitorCoords) return
            if (Skytils.config.inquisitorQuickWarp) {
                if (Skytils.config.burrowEstimationQuickWarp) {
                    if (!menuOpened) UChat.chat("${Skytils.failPrefix} Open the hub warp menu for burrow warps to work!")
                    else {
                        val warpLoc = WarpLocations.getClosest(Vec3(lastInq.coords.x, 0.0,lastInq.coords.z),mc.thePlayer.positionVector, allowedWarps) ?: return
                        UChat.chat("Pushed warp?")
                        pushWarp(
                            QuickWarp.Warp(
                                warpLoc.warpName,
                                SkyblockIsland.Hub.mode,
                                System.currentTimeMillis(),
                                5000,
                                "inq",
                                warpLoc.displayName
                            )
                        )
                        UChat.chat("Warp pushed.")
                    }
                }
            }
            GuiManager.createTitle("§6[§r§b§kr§r§6]§r §3$senderName's§r §6Inquisitor [§r§b§kr§r§6]§r",100)
        }
        if (Skytils.config.sendInquisitorCoords && (event.message.formattedText.contains("\$INQ\$") || (event.message.formattedText.contains("§r§eYou dug out ") && event.message.unformattedText.contains("Inquis")))) {
            Skytils.sendMessageQueue.add("/pc x: ${mc.thePlayer.posX.toInt()}, y: ${mc.thePlayer.posY.toInt()}, z: ${mc.thePlayer.posZ.toInt()}")
        }
    }

    @SubscribeEvent
    fun onSendPacket(event: PacketEvent.SendEvent) {
        if (!Utils.inSkyblock || !Skytils.config.showGriffinBurrows || mc.thePlayer == null || SBInfo.mode != SkyblockIsland.Hub.mode) return
        if (mc.thePlayer.heldItem?.isSpade != true) return

        if (event.packet is C08PacketPlayerBlockPlacement && event.packet.position.y == -1) {
            lastSpadeUse = System.currentTimeMillis()
            lastParticleTrail.clear()
            BurrowEstimation.lastTrailCreated = -1
            lastSoundTrail.clear()
            printDevMessage("Spade used", "griffinguess")
        } else {
            val pos =
                when {
                    event.packet is C07PacketPlayerDigging && event.packet.status == C07PacketPlayerDigging.Action.START_DESTROY_BLOCK -> {
                        event.packet.position
                    }
                    event.packet is C08PacketPlayerBlockPlacement && event.packet.stack != null -> event.packet.position
                    else -> return
                }
            if (mc.theWorld.getBlockState(pos).block !== Blocks.grass) return
            particleBurrows[pos]?.blockPos?.let {
                printDevMessage("Clicked on $it", "griffin")
                lastDugParticleBurrow = it
            }
        }
    }

    @SubscribeEvent
    fun onWorldRender(event: RenderWorldLastEvent) {
        if (!Utils.inSkyblock || SBInfo.mode != SkyblockIsland.Hub.mode) return
        if (Skytils.config.showGriffinBurrows) {
            val matrixStack = UMatrixStack()
            for (pb in particleBurrows.values) {
                if (pb.hasEnchant && pb.hasFootstep && pb.type != -1) {
                    pb.drawWaypoint(event.partialTicks, matrixStack)
                }
            }
            if (Skytils.config.burrowEstimation) {
                for (bg in BurrowEstimation.guesses.keys) {
                    bg.drawWaypoint(event.partialTicks, matrixStack)
                }
                for (arrow in BurrowEstimation.arrows.keys) {
                    RenderUtil.drawCircle(
                        matrixStack,
                        arrow.pos.x,
                        arrow.pos.y + 0.2,
                        arrow.pos.z,
                        event.partialTicks,
                        5.0,
                        100,
                        255,
                        128,
                        0,
                    )
                }
            }
        }
        if (lastInq.spawner != mc.thePlayer.name && Skytils.config.drawInquisitorCoords && (System.currentTimeMillis() - lastInq.spawnTime) < 30000 && mc.thePlayer.position.distanceSq(Vec3i(
                    lastInq.coords.x.toInt(), lastInq.coords.y.toInt(), lastInq.coords.z.toInt())) >= 144) {
            val matrixStack = UMatrixStack()
            RenderUtil.draw3DLine(
                lastInq.coords,
                mc.thePlayer.getPositionEyes(event.partialTicks),
                2,
                Color.cyan,
                event.partialTicks,
                matrixStack
            )
        }
    }

    @SubscribeEvent
    fun onWorldChange(event: WorldEvent.Unload) {
        particleBurrows.clear()
        recentlyDugParticleBurrows.clear()
    }

    @SubscribeEvent
    fun onReceivePacket(event: MainReceivePacketEvent<*, *>) {
        if (!Utils.inSkyblock) return
        when (event.packet) {
            is S2APacketParticles -> {
                if (Skytils.config.showGriffinBurrows && hasSpadeInHotbar) {
                    if (SBInfo.mode != SkyblockIsland.Hub.mode) return
                    event.packet.apply {
                        if (type == EnumParticleTypes.DRIP_LAVA && count == 2 && speed == -.5f && xOffset == 0f && yOffset == 0f && zOffset == 0f && isLongDistance) {
                            lastParticleTrail.add(vec3)
                            BurrowEstimation.lastTrailCreated = System.currentTimeMillis()
                            printDevMessage("Found trail point $x $y $z", "griffinguess")
                        } else {
                            val type = ParticleType.getParticleType(this) ?: return
                            val pos = BlockPos(x, y, z).down()
                            if (recentlyDugParticleBurrows.contains(pos)) return
                            BurrowEstimation.guesses.keys.associateWith { guess ->
                                (pos.x - guess.x) * (pos.x - guess.x) + (pos.z - guess.z) * (pos.z - guess.z)
                            }.minByOrNull { it.value }?.let { (guess, distance) ->
                                printDevMessage("Nearest guess is $distance blocks^2 away", "griffin", "griffinguess")
                                if (distance <= 25 * 25) {
                                    BurrowEstimation.guesses.remove(guess)
                                }
                            }
                            val burrow = particleBurrows.getOrPut(pos) {
                                ParticleBurrow(pos, hasFootstep = false, hasEnchant = false)
                            }
                            if (burrow.type == -1 && type.isBurrowType) {
                                if (Skytils.config.pingNearbyBurrow) {
                                    SoundQueue.addToQueue("random.orb", 0.8f, 1f, 0, true)
                                }
                            }
                            when (type) {
                                ParticleType.FOOTSTEP -> burrow.hasFootstep = true
                                ParticleType.ENCHANT -> burrow.hasEnchant = true
                                ParticleType.EMPTY -> burrow.type = 0
                                ParticleType.MOB -> burrow.type = 1
                                ParticleType.TREASURE -> burrow.type = 2
                            }
                        }
                    }
                }
            }
            is S04PacketEntityEquipment -> {
                if (!Skytils.config.burrowEstimation || SBInfo.mode != SkyblockIsland.Hub.mode || Skytils.config.experimentBurrowEstimation) return
                val entity = mc.theWorld?.getEntityByID(event.packet.entityID)
                (entity as? EntityArmorStand)?.let { armorStand ->
                    if (event.packet.itemStack?.item != Items.arrow) return
                    if (armorStand.getDistanceSq(mc.thePlayer?.position) >= 27) return
                    printDevMessage("Found armor stand with arrow", "griffin", "griffinguess")
                    val yaw = Math.toRadians(armorStand.rotationYaw.toDouble())
                    val lookVec = Vec3(
                        -sin(yaw),
                        0.0,
                        cos(yaw)
                    )
                    val offset = Vec3(-sin(yaw + PI/2), 0.0, cos(yaw + PI/2)) * 0.9
                    val origin = armorStand.positionVector.add(offset)
                    BurrowEstimation.arrows.put(BurrowEstimation.Arrow(lookVec, origin), Instant.now())
                }
            }
            is S29PacketSoundEffect -> {
                if (!Skytils.config.burrowEstimation || SBInfo.mode != SkyblockIsland.Hub.mode) return
                if (event.packet.soundName != "note.harp" || event.packet.volume != 1f) return
                printDevMessage("Found note harp sound ${event.packet.pitch} ${event.packet.volume} ${event.packet.x} ${event.packet.y} ${event.packet.z}", "griffinguess")
                if (lastSpadeUse != -1L && System.currentTimeMillis() - lastSpadeUse < 1000) {
                    lastSoundTrail.add(Vec3(event.packet.x, event.packet.y, event.packet.z) to event.packet.pitch.toDouble())
                }
                if (Skytils.config.experimentBurrowEstimation) return
                val (arrow, distance) = BurrowEstimation.arrows.keys
                    .associateWith { arrow ->
                        arrow.pos.squareDistanceTo(event.packet.x, event.packet.y, event.packet.z)
                    }.minByOrNull { it.value } ?: return
                printDevMessage("Nearest arrow is $distance blocks away ${arrow.pos}", "griffin", "griffinguess")
                if (distance > 25) return
                val guessPos = arrow.pos.add(
                    arrow.directionVector * BurrowEstimation.getDistanceFromPitch(event.packet.pitch.toDouble())
                )

                var y: Int
                var x = guessPos.x.toInt()
                var z = guessPos.z.toInt()
                // offset of 300 blocks for both x and z
                // x ranges from 195 to -281
                // z ranges from 207 to -233
                do {
                    y = BurrowEstimation.grassData.getOrNull((x++ % 507) * 507 + (z++ % 495))?.toInt() ?: 0
                } while (y < 2)
                val guess = BurrowGuess(guessPos.x.toInt(), y, guessPos.z.toInt())
                BurrowEstimation.arrows.remove(arrow)
                BurrowEstimation.guesses[guess] = Instant.now()
                if (Skytils.config.burrowEstimationQuickWarp) {
                    if (!menuOpened) UChat.chat("${Skytils.failPrefix} Open the hub warp menu for burrow warps to work!")
                    else {
                        UChat.chat("Pushed warp?")
                        val warpLoc = WarpLocations.getClosest(Vec3(guessPos.x, 0.0,guessPos.z),mc.thePlayer.positionVector, allowedWarps) ?: return
                        pushWarp(
                            QuickWarp.Warp(
                                warpLoc.warpName,
                                SkyblockIsland.Hub.mode,
                                System.currentTimeMillis(),
                                5000,
                                "burrow",
                                warpLoc.displayName
                            )
                        )
                        UChat.chat("Warp pushed.")
                    }
                }
            }
        }
    }

    abstract class Diggable {
        abstract val x: Int
        abstract val y: Int
        abstract val z: Int
        val blockPos: BlockPos by lazy {
            BlockPos(x, y, z)
        }

        abstract val waypointText: String
        abstract val color: Color
        fun drawWaypoint(partialTicks: Float, matrixStack: UMatrixStack) {
            val (viewerX, viewerY, viewerZ) = RenderUtil.getViewerPos(partialTicks)
            val renderX = this.x - viewerX
            val renderY = this.y - viewerY
            val renderZ = this.z - viewerZ
            val distSq = renderX * renderX + renderY * renderY + renderZ * renderZ
            GlStateManager.disableDepth()
            GlStateManager.disableCull()
            RenderUtil.drawFilledBoundingBox(
                matrixStack,
                AxisAlignedBB(renderX, renderY, renderZ, renderX + 1, renderY + 1, renderZ + 1).expandBlock(),
                this.color,
                (0.1f + 0.005f * distSq.toFloat()).coerceAtLeast(0.2f)
            )
            GlStateManager.disableTexture2D()
            if (distSq > 5 * 5) RenderUtil.renderBeaconBeam(renderX, renderY + 1, renderZ, this.color.rgb, 1.0f, partialTicks)
            RenderUtil.renderWaypointText(
                waypointText,
                x + 0.5,
                y + 5.0,
                z + 0.5,
                partialTicks,
                matrixStack
            )
            GlStateManager.disableLighting()
            GlStateManager.enableTexture2D()
            GlStateManager.enableDepth()
            GlStateManager.enableCull()
        }
    }

    data class BurrowGuess(
        override val x: Int,
        override val y: Int,
        override val z: Int
    ) : Diggable() {
        override val waypointText = "§aBurrow §6(Guess)"
        override val color = Color.ORANGE
    }

    data class ParticleBurrow(
        override val x: Int,
        override val y: Int,
        override val z: Int,
        var hasFootstep: Boolean,
        var hasEnchant: Boolean
    ) : Diggable() {
        constructor(vec3: Vec3i, hasFootstep: Boolean, hasEnchant: Boolean) : this(
            vec3.x,
            vec3.y,
            vec3.z,
            hasFootstep,
            hasEnchant
        )

        var type = -1
            set(value) {
                field = value
                when (value) {
                    0 -> {
                        waypointText = "§aStart §a(Particle)"
                        color = Skytils.config.emptyBurrowColor
                    }
                    1 -> {
                        waypointText = "§cMob §a(Particle)"
                        color = Skytils.config.mobBurrowColor
                    }
                    2 -> {
                        waypointText = "§6Treasure §a(Particle)"
                        color = Skytils.config.treasureBurrowColor
                    }
                }
            }

        override var waypointText = "§7Unknown §a(Particle)"
            private set

        override var color = Color.WHITE
            private set
    }
    private val ItemStack?.isSpade
        get() = ItemUtil.getSkyBlockItemID(this) == "ANCESTRAL_SPADE"

    private enum class ParticleType(val check: S2APacketParticles.() -> Boolean, val isBurrowType: Boolean = true) {
        EMPTY({
            type == EnumParticleTypes.CRIT_MAGIC && count == 4 && speed == 0.01f && xOffset == 0.5f && yOffset == 0.1f && zOffset == 0.5f
        }),
        MOB({
            type == EnumParticleTypes.CRIT && count == 3 && speed == 0.01f && xOffset == 0.5f && yOffset == 0.1f && zOffset == 0.5f

        }),
        TREASURE({
            type == EnumParticleTypes.DRIP_LAVA && count == 2 && speed == 0.01f && xOffset == 0.35f && yOffset == 0.1f && zOffset == 0.35f
        }),
        FOOTSTEP({
            type == EnumParticleTypes.FOOTSTEP && count == 1 && speed == 0.0f && xOffset == 0.05f && yOffset == 0.0f && zOffset == 0.05f
        }, false),
        ENCHANT({
            type == EnumParticleTypes.ENCHANTMENT_TABLE && count == 5 && speed == 0.05f && xOffset == 0.5f && yOffset == 0.4f && zOffset == 0.5f
        }, false);

        companion object {
            fun getParticleType(packet: S2APacketParticles): ParticleType? {
                if (!packet.isLongDistance) return null
                return entries.find {
                    it.check(packet)
                }
            }
        }
    }

    class MythoMobDisplay : GuiElement("Mytho Mob Health Display",x = 0.5F,y = 0.5F) {
        override fun render() {
            if (toggled && Utils.inSkyblock && SBInfo.mode == SkyblockIsland.Hub.mode) {
                val text = ArrayList<String>()
                if (Skytils.config.lastInqInfo && System.currentTimeMillis() - lastInq.spawnTime < 1000*60*60*3) {
                    text.add("Last Inq: §b${lastInq.spawner} §8@ ${(-lastInq.spawnTime+System.currentTimeMillis()).milliseconds.toComponents { hours, minutes, seconds, _ ->
                        "§6${hours}h${minutes}m${seconds}s"
                    }} §8ago.")
                } else {
                    text.add("§8No recent Inquisitors.")
                }

                if (Utils.inSkyblock && SBInfo.mode == SkyblockIsland.Hub.mode && Skytils.config.mythoMobHealth && mc.thePlayer != null && mc.theWorld != null) {
                    for (entity in mc.theWorld.loadedEntityList) {
                        if (!entity.isDead && entity is EntityArmorStand && (entity.name.containsAny("Exalted","Stalwart","Minos","Bagheera","Azrael"))) {
                            text.add(entity.name)
                        }
                    }

                }

                RenderUtil.drawAllInList(this, if (text.isNotEmpty()) text else return)

            }
        }

        override fun demoRender() {
            val demoText = ArrayList<String>()
            demoText.add("§6Last Inq: §bsongreaver §8@ §60h10m15s §8ago")
            demoText.add("§8[§7Lv210§8] §c§2Exalted Minotaur§r §e0§f/§a2.5M§c❤")
            RenderUtil.drawAllInList(this, demoText)
        }


        override val height: Int
            get() = ScreenRenderer.fontRenderer.FONT_HEIGHT
        override val width: Int
            get() = ScreenRenderer.fontRenderer.getStringWidth("§8[§7Lv210§8] §c§2Exalted Minotaur§r §e0§f/§a2.5M§c❤")
        override val toggled: Boolean
            get() = Skytils.config.mythoMobInfo

        init {
            Skytils.guiManager.registerElement(this)
        }
    }


}