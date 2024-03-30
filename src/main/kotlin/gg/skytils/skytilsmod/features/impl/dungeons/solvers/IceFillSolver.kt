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

import gg.essential.universal.UChat
import gg.essential.universal.UKeyboard
import gg.essential.universal.UMatrixStack
import gg.skytils.skytilsmod.Skytils
import gg.skytils.skytilsmod.Skytils.Companion.mc
import gg.skytils.skytilsmod.core.tickTimer
import gg.skytils.skytilsmod.features.impl.misc.Funny
import gg.skytils.skytilsmod.utils.RenderUtil
import gg.skytils.skytilsmod.utils.SuperSecretSettings
import gg.skytils.skytilsmod.utils.Utils
import gg.skytils.skytilsmod.utils.ifNull
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
        //TODO: DEBUG
        tickTimer(4, repeats = true) {
            if (UKeyboard.isAltKeyDown()) {
                job = null
                puzzles = null
            }
        }
        tickTimer(20, repeats = true) {
            //TODO: DEBUG
            if (/*!Utils.inDungeons || !Skytils.config.iceFillSolver || "Ice Fill" !in DungeonListener.missingPuzzles ||*/ puzzles != null || job?.isActive == true) return@tickTimer
            val player = mc.thePlayer ?: return@tickTimer
            val world = mc.theWorld ?: return@tickTimer

            job = Skytils.launch {
                world.loadedTileEntityList.filterIsInstance<TileEntityChest>().filter {
                    it.numPlayersUsing == 0 && it.pos.y == 75 && it.getDistanceSq(
                        player.posX, player.posY, player.posZ
                    ) < 750
                }.firstNotNullOfOrNull { chest ->
                    val pos = chest.pos

                    EnumFacing.HORIZONTALS.firstOrNull {
                        world.getBlockState(pos.down()).block == Blocks.stone && world.getBlockState(pos.offset(it)).block == Blocks.cobblestone && world.getBlockState(
                            pos.offset(it.opposite, 2)
                        ).block == Blocks.iron_bars && world.getBlockState(
                            pos.offset(
                                it.rotateY(), 2
                            )
                        ).block == Blocks.torch && world.getBlockState(
                            pos.offset(
                                it.rotateYCCW(), 2
                            )
                        ).block == Blocks.torch && world.getBlockState(
                            pos.offset(it.opposite).down(2)
                        ).block == Blocks.stone_brick_stairs
                    }?.let {
                        //chest: -11 75 -89; direction: east

                        val starts = Triple(
                            //three: -33 70 -89
                            pos.down(5).offset(it.opposite, 22),
                            //five: -28 71 -89
                            pos.down(4).offset(it.opposite, 17),
                            //seven: -21 72 -89
                            pos.down(3).offset(it.opposite, 10),
                        )
                        val ends = Triple(
                            //three: -29 70 -89
                            starts.first.offset(it, 3),
                            //five: -23 71 -89
                            starts.second.offset(it, 5),
                            //seven: -14 72 -89
                            starts.third.offset(it, 7),
                        )

                        puzzles = Triple(
                            IceFillPuzzle(pos, world, starts.first, ends.first, it),
                            IceFillPuzzle(pos, world, starts.second, ends.second, it),
                            IceFillPuzzle(pos, world, starts.third, ends.third, it)
                        )
                    }
                }
            }
        }
    }

    @SubscribeEvent
    fun onWorldRender(event: RenderWorldLastEvent) {
        //TODO: DEBUG
        //if (!Utils.inDungeons || !Skytils.config.iceFillSolver || "Ice Fill" !in DungeonListener.missingPuzzles) return
        val (three, five, seven) = puzzles ?: return
        val matrixStack = UMatrixStack.Compat.get()
        three.draw(matrixStack, event.partialTicks)
        five.draw(matrixStack, event.partialTicks)
        seven.draw(matrixStack, event.partialTicks)
    }

    @SubscribeEvent
    fun onWorldChange(event: WorldEvent.Unload) {
        job = null //TODO: IceFillPuzzle would need to check whether it got cancelled or not
        puzzles = null
    }

    private class IceFillPuzzle(
        val chest: BlockPos, val world: World, val start: BlockPos, val end: BlockPos, val facing: EnumFacing
    ) {
        private val optimal = SuperSecretSettings.azooPuzzoo
        private var path: List<Vec3>? = null

        init {
            Skytils.launch {
                path = findPath().ifNull {
                    UChat.chat("${Skytils.failPrefix} §cFailed to find a solution for Ice Fill. Please report this on our Discord at discord.gg/skytils.")
                    println("Ice Fill Data: chest=$chest, start=$start, end=$end, facing=$facing, optimal=$optimal")
                }
            }
        }

        private fun findPath(): List<Vec3>? {
            val spaces = getSpaces()

            val moves = spaces.associate {
                val neighbors = EnumFacing.HORIZONTALS.associateBy { direction -> it.offset(direction) }
                    .filterKeys { spot -> spot in spaces }
                    .mapKeys { (pos, _) -> spaces.indexOf(pos) }
                Pair(spaces.indexOf(it), neighbors)
            }

            val startIndex = spaces.indexOf(start)
            val n = spaces.size
            val visited = BooleanArray(n).also { it[startIndex] = true }
            val startPath = IntArray(n) { -1 }.also { it[0] = startIndex }

            if (optimal) {
                return getOptimalPath(
                    //TODO: I hate !!
                    Array(n) { moves[it]!! }, n, startIndex, visited, startPath, 1, facing, 0, Int.MAX_VALUE
                )?.first?.map { Vec3(spaces.elementAt(it)).addVector(0.5, 0.01, 0.5) }
            } else {
                val fixed = moves.mapValues { (_, y) -> y.map { it.key } }

                return getFirstPath(
                    //TODO: I hate !!
                    Array(n) { fixed[it]!! }, n, startIndex, visited, startPath, 1
                )?.map { Vec3(spaces.elementAt(it)).addVector(0.5, 0.01, 0.5) }
            }
        }

        fun draw(matrixStack: UMatrixStack, partialTicks: Float) {
            GlStateManager.pushMatrix()
            GlStateManager.disableCull()

            path?.zipWithNext { first, second ->
                RenderUtil.draw3DLine(first, second, 5, Color.MAGENTA, partialTicks, matrixStack, Funny.alphaMult)
            }

            GlStateManager.popMatrix()
        }

        private fun getFirstPath(
            moves: Array<List<Int>>,
            n: Int,
            visiting: Int,
            visited: BooleanArray,
            path: IntArray,
            depth: Int,
        ): List<Int>? {
            if (depth == n) {
                return path.toList()
            }

            val move = moves[visiting]

            for (index in move) {
                if (!visited[index]) {
                    visited[index] = true
                    path[depth] = index

                    val foundPath = getFirstPath(moves, n, index, visited, path, depth + 1)
                    if (foundPath != null) return foundPath

                    visited[index] = false
                }
            }

            return null
        }

        //TODO: Maybe make it only return the path and not the corners
        private fun getOptimalPath(
            moves: Array<Map<Int, EnumFacing>>,
            n: Int,
            visiting: Int,
            visited: BooleanArray,
            path: IntArray,
            depth: Int,
            lastDirection: EnumFacing,
            corners: Int,
            knownLeastCorners: Int
        ): Pair<List<Int>, Int>? {
            if (depth == n) {
                return Pair(path.toList(), corners)
            }


            var bestPath: List<Int>? = null
            var leastCorners = knownLeastCorners

            if (leastCorners > corners) {
                val move = moves[visiting]

                for ((index, direction) in move) {
                    if (!visited[index]) { //TODO: Last slow part, the rest of performance is just recursion issues?
                        visited[index] = true
                        path[depth] = index

                        val newCorners = if (lastDirection != direction) corners + 1 else corners
                        //TODO: Depth check and corner check should be inside here to remove the amount of function calls

                        val newPath = getOptimalPath(
                            moves, n, index, visited, path, depth + 1, direction, newCorners, leastCorners
                        )
                        if (newPath != null) {
                            bestPath = newPath.first
                            leastCorners = newPath.second
                        }

                        visited[index] = false
                    }
                }
            }

            val best = bestPath
            return if (best != null) Pair(best, leastCorners) else null
        }

        private fun getSpaces(): List<BlockPos> {
            val spaces = mutableListOf(start)
            val queue = mutableListOf(start)

            while (queue.isNotEmpty()) {
                val current = queue.removeLast()
                EnumFacing.HORIZONTALS.forEach { direction ->
                    val next = current.offset(direction)
                    if (next !in spaces && world.getBlockState(next).block === Blocks.air && Utils.equalsOneOf(
                            world.getBlockState(
                                next.down()
                            ).block, Blocks.ice, Blocks.packed_ice
                        )
                    ) {
                        spaces.add(next)
                        queue.add(next)
                    }
                }
            }

            return spaces
        }
    }
}