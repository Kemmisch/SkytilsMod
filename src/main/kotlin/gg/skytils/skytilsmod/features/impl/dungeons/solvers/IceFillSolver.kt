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
package gg.skytils.skytilsmod.features.impl.dungeons.solvers

import gg.essential.universal.UMatrixStack
import gg.skytils.skytilsmod.Skytils
import gg.skytils.skytilsmod.Skytils.Companion.mc
import gg.skytils.skytilsmod.core.tickTimer
import gg.skytils.skytilsmod.features.impl.misc.Funny
import gg.skytils.skytilsmod.listeners.DungeonListener
import gg.skytils.skytilsmod.utils.RenderUtil
import gg.skytils.skytilsmod.utils.Utils
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.init.Blocks
import net.minecraft.tileentity.TileEntityChest
import net.minecraft.util.BlockPos
import net.minecraft.util.EnumFacing
import net.minecraft.util.Vec3
import net.minecraft.world.World
import net.minecraftforge.client.event.RenderWorldLastEvent
import net.minecraftforge.event.world.WorldEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import java.awt.Color

object IceFillSolver {
    private var puzzles: Triple<IceFillPuzzle, IceFillPuzzle, IceFillPuzzle>? = null
    private var job: Job? = null

    init {
        tickTimer(20, repeats = true) {
            if (!Utils.inDungeons || !Skytils.config.iceFillSolver || "Ice Fill" !in DungeonListener.missingPuzzles ||
                puzzles != null || job?.isActive != true) return@tickTimer
            val player = mc.thePlayer ?: return@tickTimer
            val world = mc.theWorld ?: return@tickTimer

            job = Skytils.launch {
                world.loadedTileEntityList.filterIsInstance<TileEntityChest>().filter {
                    it.numPlayersUsing == 0 && it.pos.y == 75 && it.getDistanceSq(player.posX, player.posY, player.posZ) > 750
                }.map { chest ->
                    val pos = chest.pos
                    EnumFacing.HORIZONTALS.firstOrNull {
                        world.getBlockState(pos.down()).block == Blocks.stone &&
                                world.getBlockState(pos.offset(it)).block == Blocks.cobblestone &&
                                world.getBlockState(pos.offset(it.opposite, 2)).block == Blocks.iron_bars &&
                                world.getBlockState(pos.offset(it.rotateY(), 2)).block == Blocks.torch &&
                                world.getBlockState(pos.offset(it.rotateYCCW(), 2)).block == Blocks.torch &&
                                world.getBlockState(pos.offset(it.opposite).down(2)).block == Blocks.stone_brick_stairs
                    }?.let {
                        Pair(chest, it)
                    }
                }.firstOrNull()?.let {
                    //chest: BlockPos{x=-11, y=75, z=-89}; direction: east
                    val (chest, direction) = it
                    val pos = chest.pos

                    val starts = Triple(
                        //three: BlockPos{x=-33, y=70, z=-89}
                        pos.down(5).offset(direction.opposite, 22),
                        //five: BlockPos{x=-28, y=71, z=-89}
                        pos.down(4).offset(direction.opposite, 17),
                        //seven: BlockPos{x=-21, y=72, z=-89}
                        pos.down(3).offset(direction.opposite, 10),
                    )
                    puzzles = Triple(
                        IceFillPuzzle(world, starts.first, direction),
                        IceFillPuzzle(world, starts.second, direction),
                        IceFillPuzzle(world, starts.third, direction)
                    )
                    println("Ice fill chest is at $pos and is facing $direction")
                }
            }
        }
    }

    @SubscribeEvent
    fun onWorldRender(event: RenderWorldLastEvent) {
        if (!Skytils.config.iceFillSolver || "Ice Fill" !in DungeonListener.missingPuzzles) return
        val (three, five, seven) = puzzles ?: return
        val matrixStack = UMatrixStack.Compat.get()
        three.draw(matrixStack, event.partialTicks)
        five.draw(matrixStack, event.partialTicks)
        seven.draw(matrixStack, event.partialTicks)
    }

    @SubscribeEvent
    fun onWorldChange(event: WorldEvent.Unload) {
        job = null
        puzzles = null
    }

    private class IceFillPuzzle(val world: World, val start: BlockPos, val facing: EnumFacing) {
        private val path = run {
            //TODO: Probably have a method to find all connected air blocks with ice underneath for this
            val spaces = Utils.getBlocksWithinRangeAtSameY(start.offset(facing, 3), 4, start.y).filter { pos ->
                Utils.equalsOneOf(world.getBlockState(pos.down()).block, Blocks.ice, Blocks.packed_ice) &&
                        world.getBlockState(pos).block === Blocks.air
            }
            val moves = spaces.flatMap { pos -> getPossibleMoves(pos).map { Pair(pos, it) } }
            val adjList: Map<BlockPos, Collection<BlockPos>> = buildMap {
                moves.forEach { (source, dest) ->
                    this[source] = getPossibleMoves(source)
                    this[dest] = getPossibleMoves(dest)
                }
            }

            val paths = getPaths(adjList, start, mutableSetOf(start), mutableListOf(start), spaces.size)

            paths.minByOrNull {
                it.zipWithNext { first, second ->
                    val diff = first.subtract(second)
                    EnumFacing.getFacingFromVector(diff.x.toFloat(), diff.y.toFloat(), diff.z.toFloat())
                }.zipWithNext { first, second ->
                    if (first != second) 1 else 0
                }.sum()
            }
        }

        fun draw(matrixStack: UMatrixStack, partialTicks: Float) {
            GlStateManager.pushMatrix()
            GlStateManager.disableCull()
            path?.zipWithNext { first, second ->
                RenderUtil.draw3DLine(
                    Vec3(first).addVector(0.6, 0.01, 0.6),
                    Vec3(second).addVector(0.6, 0.01, 0.6),
                    5,
                    Color.GREEN,
                    partialTicks,
                    matrixStack,
                    Funny.alphaMult
                )
            }
            GlStateManager.popMatrix()
        }

        //TODO: Rework this method or replace entirely
        private fun getPaths(
            adjList: Map<BlockPos, Collection<BlockPos>>,
            v: BlockPos,
            visited: MutableSet<BlockPos>,
            path: MutableList<BlockPos>,
            n: Int
        ): Set<List<BlockPos>> {
            val paths = mutableSetOf<List<BlockPos>>()

            fun getPaths(
                adjList: Map<BlockPos, Collection<BlockPos>>,
                v: BlockPos,
                visited: MutableSet<BlockPos>,
                path: MutableList<BlockPos>,
                n: Int
            ) {
                if (path.size == n) {
                    val newPath: List<BlockPos> = path.toList()
                    paths.add(newPath)
                    return
                } else {

                    // Check if every move starting from position `v` leads
                    // to a solution or not
                    adjList[v]?.forEach { w ->
                        // Only check if we haven't been there before
                        if (!visited.contains(w)) {
                            visited.add(w)
                            path.add(w)

                            // Continue checking down this path
                            getPaths(adjList, w, visited, path, n)

                            // backtrack
                            visited.remove(w)
                            path.remove(w)
                        }
                    }
                }
            }

            getPaths(adjList, v, visited, path, n)
            return paths
        }

        private fun getPossibleMoves(pos: BlockPos): List<BlockPos> {
            return EnumFacing.HORIZONTALS.map { pos.offset(it) }.filter { spot ->
                Utils.equalsOneOf(world.getBlockState(spot.down()).block, Blocks.ice, Blocks.packed_ice) &&
                        world.getBlockState(spot).block != Blocks.stone
            }
        }
    }
}