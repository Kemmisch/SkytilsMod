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
import gg.skytils.skytilsmod.events.impl.MainReceivePacketEvent
import gg.skytils.skytilsmod.events.impl.PacketEvent
import gg.skytils.skytilsmod.features.impl.handlers.MayorInfo
import gg.skytils.skytilsmod.utils.*
import gg.skytils.skytilsmod.utils.Utils.isMytho
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.init.Blocks
import net.minecraft.item.ItemStack
import net.minecraft.network.play.client.C07PacketPlayerDigging
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement
import net.minecraft.network.play.server.S2APacketParticles
import net.minecraft.util.AxisAlignedBB
import net.minecraft.util.BlockPos
import net.minecraft.util.EnumParticleTypes
import net.minecraft.util.Vec3i
import net.minecraft.util.Vec3
import net.minecraftforge.client.event.ClientChatReceivedEvent
import net.minecraftforge.client.event.RenderWorldLastEvent
import net.minecraftforge.event.world.WorldEvent
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent
import java.awt.Color

object GriffinBurrows {
    val particleBurrows = hashMapOf<BlockPos, ParticleBurrow>()
    var lastDugParticleBurrow: BlockPos? = null
    val recentlyDugParticleBurrows: EvictingQueue<BlockPos> = EvictingQueue.create(5)
    val inquisitorRegex = Regex("§9Party §8> (?<rank>§.\\[\\S{3,13}] |§7)(?<name>[^§]{1,13})§f: §?r?x:? ?(?<x>[^, y]{0,4}),? ?y:? ?(?<y>[^, z]{0,4}),? ?z:? ?(?<z>[^, §r]{0,4}) §?r?")
    var hasSpadeInHotbar = false
    data class Inquisitor(var coords: Vec3, val spawnTime: Long, val spawner: String)
    var lastInq = Inquisitor(Vec3(-2.5, 70.0, -69.5), 0,"null")

    @SubscribeEvent
    fun onTick(event: ClientTickEvent) {
        if (event.phase != TickEvent.Phase.START) return
        hasSpadeInHotbar = mc.thePlayer != null && Utils.inSkyblock && (0..7).any {
            mc.thePlayer.inventory.getStackInSlot(it).isSpade
        }
    }

    @SubscribeEvent(receiveCanceled = true, priority = EventPriority.HIGHEST)
    fun onChat(event: ClientChatReceivedEvent) {
        if (event.type == 2.toByte() || SBInfo.mode != SkyblockIsland.Hub.mode || !isMytho) {return}
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
        if (event.message.unformattedText.contains("\$INQ\$") || event.message.unformattedText.contains("§r§eYou dug out ") && event.message.unformattedText.contains("Inquis") && Skytils.config.sendInquisitorCoords) {
            Skytils.sendMessageQueue.add("/pc x: ${mc.thePlayer.posX.toInt()} y: ${mc.thePlayer.posY.toInt()} z: ${mc.thePlayer.posZ.toInt()}")
        }
        if (Skytils.config.drawInquisitorCoords && inquisitorRegex.matches(event.message.unformattedText)) {
            val inqPartyMessage = inquisitorRegex.find(event.message.unformattedText)
            val rank = inqPartyMessage?.groups?.get("rank")?.value?.trim().toString()
            val spawnerName = inqPartyMessage?.groups?.get("name")?.value?.trim().toString()
            lastInq = Inquisitor(Vec3(inqPartyMessage?.groups?.get("x")?.value?.trim()?.toDoubleOrNull() ?: return,inqPartyMessage?.groups?.get("y")?.value?.trim()?.toDoubleOrNull() ?: return,inqPartyMessage?.groups?.get("z")?.value?.trim()?.toDoubleOrNull() ?: return),System.currentTimeMillis(),spawnerName)
            UChat.chat("§6Inquisitor §ffound by $rank $spawnerName§r")
            GuiManager.createTitle("§6[§r§b§kr§r§6]§r §3$spawnerName's§r §6Inquisitor [§r§b§kr§r§6]§r",200)

        }
    }

    @SubscribeEvent
    fun onSendPacket(event: PacketEvent.SendEvent) {
        if (!Utils.inSkyblock || !Skytils.config.showGriffinBurrows || mc.theWorld == null || mc.thePlayer == null || SBInfo.mode != SkyblockIsland.Hub.mode || !isMytho) return
        val pos =
            when {
                event.packet is C07PacketPlayerDigging && event.packet.status == C07PacketPlayerDigging.Action.START_DESTROY_BLOCK -> {
                    event.packet.position
                }
                event.packet is C08PacketPlayerBlockPlacement && event.packet.stack != null -> event.packet.position
                else -> return
            }
        if (mc.thePlayer.heldItem?.isSpade != true || mc.theWorld.getBlockState(pos).block !== Blocks.grass) return
        particleBurrows[pos]?.blockPos?.let {
            printDevMessage("Clicked on $it", "griffin")
            lastDugParticleBurrow = it
        }
    }

    @SubscribeEvent
    fun onWorldRender(event: RenderWorldLastEvent) {
        if (SBInfo.mode != SkyblockIsland.Hub.mode || !isMytho) {return}
        if (Skytils.config.showGriffinBurrows) {
            val matrixStack = UMatrixStack()
            for (pb in particleBurrows.values) {
                if (pb.hasEnchant && pb.hasFootstep && pb.type != -1) {
                    pb.drawWaypoint(event.partialTicks, matrixStack)
                }
            }
        }
        if (Skytils.config.drawInquisitorCoords && (System.currentTimeMillis() - lastInq.spawnTime) < 30000 && lastInq.spawner!=mc.thePlayer.name && mc.thePlayer.position.distanceSq(Vec3i(
                lastInq.coords.x.toInt(),lastInq.coords.y.toInt(),lastInq.coords.z.toInt())) >= 144) {
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
        if (!Utils.inSkyblock || SBInfo.mode != SkyblockIsland.Hub.mode) return
        if (Skytils.config.showGriffinBurrows && hasSpadeInHotbar && event.packet is S2APacketParticles) {
            if (SBInfo.mode != SkyblockIsland.Hub.mode || !isMytho) return
            event.packet.apply {
                val type = ParticleType.getParticleType(this) ?: return
                val pos = BlockPos(x, y, z).down()
                if (recentlyDugParticleBurrows.contains(pos)) return
                val burrow = particleBurrows.getOrPut(pos) {
                    ParticleBurrow(pos, hasFootstep = false, hasEnchant = false, type = -1)
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

    abstract class Diggable {
        abstract val x: Int
        abstract val y: Int
        abstract val z: Int
        abstract var type: Int
        val blockPos: BlockPos by lazy {
            BlockPos(x, y, z)
        }

        protected abstract val waypointText: String
        protected abstract val color: Color
        fun drawWaypoint(partialTicks: Float, matrixStack: UMatrixStack) {
            val (viewerX, viewerY, viewerZ) = RenderUtil.getViewerPos(partialTicks)
            val pos = blockPos
            val x = pos.x - viewerX
            val y = pos.y - viewerY
            val z = pos.z - viewerZ
            val distSq = x * x + y * y + z * z
            GlStateManager.disableDepth()
            GlStateManager.disableCull()
            RenderUtil.drawFilledBoundingBox(
                matrixStack,
                AxisAlignedBB(x, y, z, x + 1, y + 1, z + 1).expandBlock(),
                this.color,
                (0.1f + 0.005f * distSq.toFloat()).coerceAtLeast(0.2f)
            )
            GlStateManager.disableTexture2D()
            if (distSq > 5 * 5) RenderUtil.renderBeaconBeam(x, y + 1, z, this.color.rgb, 1.0f, partialTicks)
            RenderUtil.renderWaypointText(
                waypointText,
                blockPos.x + 0.5,
                blockPos.y + 5.0,
                blockPos.z + 0.5,
                partialTicks,
                matrixStack
            )
            GlStateManager.disableLighting()
            GlStateManager.enableTexture2D()
            GlStateManager.enableDepth()
            GlStateManager.enableCull()
        }
    }

    data class ParticleBurrow(
        override val x: Int,
        override val y: Int,
        override val z: Int,
        var hasFootstep: Boolean,
        var hasEnchant: Boolean,
        override var type: Int
    ) : Diggable() {
        constructor(vec3: Vec3i, hasFootstep: Boolean, hasEnchant: Boolean, type: Int) : this(
            vec3.x,
            vec3.y,
            vec3.z,
            hasFootstep,
            hasEnchant,
            type
        )

        override val waypointText: String
            get() {
                var type = "Burrow"
                when (this.type) {
                    0 -> type = "§aStart"
                    1 -> type = "§cMob"
                    2 -> type = "§6Treasure"
                }
                return "$type §a(Particle)"
            }
        override val color: Color
            get() {
                return when (this.type) {
                    0 -> Skytils.config.emptyBurrowColor
                    1 -> Skytils.config.mobBurrowColor
                    2 -> Skytils.config.treasureBurrowColor
                    else -> Color.WHITE
                }
            }
    }

    private val ItemStack?.isSpade
        get() = ItemUtil.getSkyBlockItemID(this) == "ANCESTRAL_SPADE"

    private enum class ParticleType(val check: S2APacketParticles.() -> Boolean) {
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
        }),
        ENCHANT({
            type == EnumParticleTypes.ENCHANTMENT_TABLE && count == 5 && speed == 0.05f && xOffset == 0.5f && yOffset == 0.4f && zOffset == 0.5f
        });

        companion object {
            fun getParticleType(packet: S2APacketParticles): ParticleType? {
                if (!packet.isLongDistance) return null
                for (type in entries) {
                    if (type.check(packet)) {
                        return type
                    }
                }
                return null
            }
        }
    }
}