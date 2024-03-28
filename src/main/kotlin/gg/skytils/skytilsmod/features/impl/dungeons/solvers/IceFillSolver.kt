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
import gg.skytils.skytilsmod.listeners.DungeonListener
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
import java.util.*
import kotlin.math.abs

object IceFillSolver {
    private var puzzles: Triple<IceFillPuzzle, IceFillPuzzle, IceFillPuzzle>? = null
    private var job: Job? = null

    init {
        tickTimer(20, repeats = true) {
            //TODO: DEBUG
            if (UKeyboard.isAltKeyDown() && UKeyboard.isKeyDown(UKeyboard.KEY_B)) {
                job = null
                puzzles = null
            }
            //TODO: DEBUG
            if (!Utils.inDungeons || !Skytils.config.iceFillSolver || "Ice Fill" !in DungeonListener.missingPuzzles ||
                puzzles != null || job?.isActive == true) return@tickTimer
            val player = mc.thePlayer ?: return@tickTimer
            val world = mc.theWorld ?: return@tickTimer

            job = Skytils.launch {
                world.loadedTileEntityList.filterIsInstance<TileEntityChest>().filter {
                    it.numPlayersUsing == 0 && it.pos.y == 75 && it.getDistanceSq(
                        player.posX, player.posY, player.posZ
                    ) < 750
                }.map { chest ->
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
                        Pair(chest, it)
                    }
                }.firstOrNull()?.let {
                    //chest: -11 75 -89; direction: east
                    val (chest, direction) = it
                    val pos = chest.pos

                    val starts = Triple(
                        //three: -33 70 -89
                        pos.down(5).offset(direction.opposite, 22),
                        //five: -28 71 -89
                        pos.down(4).offset(direction.opposite, 17),
                        //seven: -21 72 -89
                        pos.down(3).offset(direction.opposite, 10),
                    )
                    val ends = Triple(
                        //three: -29 70 -89
                        starts.first.offset(direction, 3),
                        //five: -23 71 -89
                        starts.second.offset(direction, 5),
                        //seven: -14 72 -89
                        starts.third.offset(direction, 7),
                    )

                    puzzles = Triple(
                        IceFillPuzzle(world, starts.first, ends.first, direction),
                        IceFillPuzzle(world, starts.second, ends.second, direction),
                        IceFillPuzzle(world, starts.third, ends.third, direction)
                    )
                }
            }
        }
    }

    @SubscribeEvent
    fun onWorldRender(event: RenderWorldLastEvent) {
        //TODO: DEBUG
        if (!Utils.inDungeons || !Skytils.config.iceFillSolver || "Ice Fill" !in DungeonListener.missingPuzzles) return
        val (three, five, seven) = puzzles ?: return
        val matrixStack = UMatrixStack.Compat.get()
        three.draw(matrixStack, event.partialTicks)
        five.draw(matrixStack, event.partialTicks)
        seven.draw(matrixStack, event.partialTicks)
    }

    @SubscribeEvent
    fun onWorldChange(event: WorldEvent.Unload) {
        job = null //TODO: Does not stop getPaths method while running
        puzzles = null
    }

    private class IceFillPuzzle(val world: World, val start: BlockPos, val end: BlockPos, val facing: EnumFacing) {
        private var path: List<BlockPos>? = null

        init {
            Skytils.launch {
                val start = System.currentTimeMillis()
                // Maybe just make it a setting
                path = findPath(SuperSecretSettings.azooPuzzoo).ifNull {
                    UChat.chat("${Skytils.failPrefix} §cFailed to find a solution for Ice Fill.")
                    println("Ice Fill Data: start=$start, end=$end, facing=$facing")
                }
                //TODO: DEBUG

            }
        }

        fun draw(matrixStack: UMatrixStack, partialTicks: Float) {
            GlStateManager.pushMatrix()
            GlStateManager.disableCull()
            path?.zipWithNext { first, second ->
                RenderUtil.draw3DLine(
                    Vec3(first).addVector(0.5, 0.01, 0.5),
                    Vec3(second).addVector(0.5, 0.01, 0.5),
                    5,
                    Color.GREEN,
                    partialTicks,
                    matrixStack,
                    Funny.alphaMult
                )
            }
            GlStateManager.popMatrix()
        }

        private fun findPath(shouldBeOptimal: Boolean): List<BlockPos>? {
            val spaces = getSpaces()
            val moves = spaces.associateWith {
                EnumFacing.HORIZONTALS.associateBy { direction ->
                    it.offset(direction)
                }.filterKeys { spot -> spot in spaces }
            }

            //TODO: TESTING
            //return getOptimalPathIterative(
            //    moves,
            //    spaces.size,
            //    start,
            //    hashSetOf(start),
            //    mutableListOf(start),
            //    1,
            //    facing,
            //    0,
            //    Int.MAX_VALUE
            //)

            return if (shouldBeOptimal) {
                getOptimalPath(
                    moves,
                    spaces.size,
                    start,
                    hashSetOf(start),
                    mutableListOf(start),
                    1,
                    facing,
                    0,
                    Int.MAX_VALUE
                )?.first
            } else {
                getFirstPath(
                    moves,
                    start,
                    hashSetOf(start),
                    mutableListOf(start),
                    1,
                    spaces.size
                )
            }
        }

        // TODO: VERY SLOW (~60s)
        private fun getOptimalPathIterative(
            moves: Map<BlockPos, Map<BlockPos, EnumFacing>>,
            n: Int,
            visiting: BlockPos,
            visited: HashSet<BlockPos>,
            path: MutableList<BlockPos>,
            depth: Int,
            lastDirection: EnumFacing,
            corners: Int,
            knownLeastCorners: Int
        ): List<BlockPos>? {
            data class StackNode(
                val visiting: BlockPos,
                val visited: HashSet<BlockPos>,
                val path: MutableList<BlockPos>,
                val depth: Int,
                val lastDirection: EnumFacing,
                val corners: Int
            )

            val stack = Stack<StackNode>()
            stack.push(StackNode(visiting, visited, path, depth, lastDirection, corners))
            var bestPath: List<BlockPos>? = null
            var leastCorners = knownLeastCorners

            while (stack.isNotEmpty()) {
                val (visiting, visited, path, depth, lastDirection, corners) = stack.pop()

                if (depth == n && visiting == end) {
                    bestPath = path.toList()
                    leastCorners = corners
                }

                val move = moves[visiting] ?: return null

                if (end in move && depth != n - 1) {
                    continue
                }

                if (leastCorners > corners) {
                    move.forEach { (pos, direction) ->
                        if (pos !in visited) {
                            val newVisited = HashSet(visited)
                            newVisited.add(pos)

                            val newPath = path.toMutableList()
                            newPath.add(pos)

                            val newCorners = if (lastDirection != direction) corners + 1 else corners

                            stack.push(StackNode(pos, newVisited, newPath, depth + 1, direction, newCorners))
                        }
                    }
                }
            }

            return bestPath
        }

        //TODO: KINDA SLOW (~10s)
        private fun getOptimalPath(
            moves: Map<BlockPos, Map<BlockPos, EnumFacing>>,
            n: Int,
            visiting: BlockPos,
            visited: HashSet<BlockPos>,
            path: MutableList<BlockPos>,
            depth: Int,
            lastDirection: EnumFacing,
            corners: Int,
            knownLeastCorners: Int
        ): Pair<List<BlockPos>, Int>? {
            if (depth == n && visiting == end) {
                return Pair(path.toList(), corners)
            }

            val move = moves[visiting] ?: return null

            if (end in move && depth != n - 1) {
                return null
            }

            var newPath: List<BlockPos>? = null
            var leastCorners = knownLeastCorners

            if (leastCorners > corners) {
                move.forEach { (pos, direction) ->
                    if (pos !in visited) {
                        visited.add(pos)
                        path.add(pos)

                        val newCorners = if (lastDirection != direction) corners + 1 else corners

                        getOptimalPath(
                            moves,
                            n,
                            pos,
                            visited,
                            path,
                            depth + 1,
                            direction,
                            newCorners,
                            leastCorners
                        )?.let {
                            newPath = it.first
                            leastCorners = it.second
                        }

                        visited.remove(pos)
                        path.removeLast()
                    }
                }
            }

            return newPath?.let { Pair(it, leastCorners) }
        }

        //TODO: FAST-ish (~1s)
        private fun getFirstPath(
            moves: Map<BlockPos, Map<BlockPos, EnumFacing>>,
            visiting: BlockPos,
            visited: HashSet<BlockPos>,
            path: MutableList<BlockPos>,
            depth: Int,
            n: Int,
        ): List<BlockPos>? {
            if (depth == n && visiting == end) {
                return path.toList()
            }

            val move = moves[visiting] ?: return null

            if (end in move && depth != n - 1) {
                return null
            }

            if (abs(visiting.x - end.x) + abs(visiting.z - end.z) > n - depth) {
                return null
            }

            move.forEach { (pos) ->
                if (pos !in visited) {
                    visited.add(pos)
                    path.add(pos)

                    if (getFirstPath(moves, pos, visited, path, depth + 1, n) != null) {
                        return path.toList()
                    }

                    visited.remove(pos)
                    path.removeLast()
                }
            }

            return null
        }

        private fun getSpaces(): Set<BlockPos> {
            val spaces = hashSetOf(start)
            val queue = Stack<BlockPos>()
            queue.add(start)

            while (queue.isNotEmpty()) {
                val current = queue.pop()
                EnumFacing.HORIZONTALS.forEach { direction ->
                    val next = current.offset(direction)
                    if (next !in spaces && Utils.equalsOneOf(
                            world.getBlockState(next.down()).block, Blocks.ice, Blocks.packed_ice
                        ) && world.getBlockState(next).block === Blocks.air
                    ) {
                        spaces.add(next)
                        queue.add(next)
                    }
                }
            }

            return spaces
        }

        //TODO: This is a mess but faster than iterative (30s)
        private fun getAllPaths(
            moves: HashMap<BlockPos, Map<BlockPos, EnumFacing>>,
            visiting: BlockPos,
            visited: HashSet<BlockPos>,
            path: MutableList<BlockPos>,
            depth: Int,
            n: Int,
            lastDirection: EnumFacing,
            corners: Int
        ): List<Pair<List<BlockPos>, Int>> {
            if (depth == n && visiting == end) {
                return listOf(Pair(path.toList(), corners))
            }

            val move = moves[visiting] ?: return emptyList()

            if (end in move && depth != n - 1) {
                return emptyList()
            }

            val paths = mutableListOf<Pair<List<BlockPos>, Int>>()

            move.forEach { (pos, direction) ->
                if (pos !in visited) {
                    visited.add(pos)
                    path.add(pos)

                    val newCorners = if (lastDirection != direction) corners + 1 else corners

                    paths.addAll(getAllPaths(moves, pos, visited, path, depth + 1, n, direction, newCorners))

                    visited.remove(pos)
                    path.removeLast()
                }
            }

            return paths
        }
    }
}