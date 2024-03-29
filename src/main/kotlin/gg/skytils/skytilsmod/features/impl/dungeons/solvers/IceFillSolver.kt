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

object IceFillSolver {
    private var puzzles: Triple<IceFillPuzzle, IceFillPuzzle, IceFillPuzzle>? = null
    private var job: Job? = null

    init {
        tickTimer(20, repeats = true) {
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
                        IceFillPuzzle(chest.pos, world, starts.first, ends.first, direction),
                        IceFillPuzzle(chest.pos, world, starts.second, ends.second, direction),
                        IceFillPuzzle(chest.pos, world, starts.third, ends.third, direction)
                    )
                }
            }
        }
    }

    @SubscribeEvent
    fun onWorldRender(event: RenderWorldLastEvent) {
        if (!Utils.inDungeons || !Skytils.config.iceFillSolver || "Ice Fill" !in DungeonListener.missingPuzzles) return
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
        val chest: BlockPos,
        val world: World,
        val start: BlockPos,
        val end: BlockPos,
        val facing: EnumFacing
    ) {
        private val optimal = SuperSecretSettings.azooPuzzoo
        private var path: List<BlockPos>? = null

        init {
            Skytils.launch {
                path = findPath().ifNull {
                    UChat.chat("${Skytils.failPrefix} §cFailed to find a solution for Ice Fill. Please report this on our Discord at discord.gg/skytils.")
                    println("Ice Fill Data: chest=$chest, start=$start, end=$end, facing=$facing, optimal=$optimal")
                }
            }
        }

        private fun findPath(): List<BlockPos>? {
            val spaces = getSpaces()

            //TODO: Make this not cancer
            val moves = spaces.associateWith {
                EnumFacing.HORIZONTALS
                    .associateBy { direction -> it.offset(direction) }
                    .filterKeys { spot -> spot in spaces }
                    .map { (spot, direction) -> Pair(spaces.indexOf(spot), direction) }
            }.mapKeys { (pos, _) -> spaces.indexOf(pos) }

            val visited = BooleanArray(spaces.size).also { it[spaces.indexOf(start)] = true }
            val n = spaces.size
            val startIndex = spaces.indexOf(start)

            if (optimal) {
                return getOptimalPath(
                    //TODO: Technically not possible to have a null value here, but I don't wanna use !!
                    Array(spaces.size) { moves[it] ?: emptyList() },
                    n,
                    startIndex,
                    visited.clone(),
                    mutableListOf(startIndex),
                    1,
                    facing
                )?.first?.map { spaces.elementAt(it) }
            } else {
                val fixed = moves.mapValues { (_, y) -> y.map { it.first } }

                return getFirstPath(
                    //TODO: Technically not possible to have a null value here, but I don't wanna use !!
                    Array(spaces.size) { fixed[it] ?: emptyList() },
                    n,
                    startIndex,
                    visited.clone(),
                    mutableListOf(startIndex),
                    1
                )?.map { spaces.elementAt(it) }
            }
        }

        fun draw(matrixStack: UMatrixStack, partialTicks: Float) {
            GlStateManager.pushMatrix()
            GlStateManager.disableCull()

            //TODO: Probably just have path be a list of Vec3s
            path?.zipWithNext { first, second ->
                RenderUtil.draw3DLine(
                    Vec3(first).addVector(0.5, 0.01, 0.5),
                    Vec3(second).addVector(0.5, 0.01, 0.5),
                    5,
                    Color.MAGENTA,
                    partialTicks,
                    matrixStack,
                    Funny.alphaMult
                )
            }

            GlStateManager.popMatrix()
        }

        private fun getFirstPath(
            moves: Array<List<Int>>,
            n: Int,
            visiting: Int,
            visited: BooleanArray,
            path: MutableList<Int>,
            depth: Int,
        ): List<Int>? {
            if (depth == n) {
                return path.toList()
            }

            val move = moves[visiting]

            for (index in move) {
                if (!visited[index]) {
                    visited[index] = true
                    path.add(index)

                    if (getFirstPath(moves, n, index, visited, path, depth + 1) != null) {
                        return path.toList()
                    }

                    visited[index] = false
                    path.removeLast()
                }
            }

            return null
        }

        //TODO: Maybe make it only return the path and not the corners
        private fun getOptimalPath(
            moves: Array<List<Pair<Int, EnumFacing>>>,
            n: Int,
            visiting: Int,
            visited: BooleanArray,
            path: MutableList<Int>,
            depth: Int,
            lastDirection: EnumFacing,
            corners: Int = 0,
            knownLeastCorners: Int = Int.MAX_VALUE
        ): Pair<List<Int>, Int>? {
            if (depth == n) {
                return Pair(path.toList(), corners)
            }

            val move = moves[visiting]

            var newPath: List<Int>? = null
            var leastCorners = knownLeastCorners

            if (leastCorners > corners) {
                for ((index, direction) in move) {
                    if (!visited[index]) {
                        visited[index] = true
                        path.add(index) //TODO: SLOW

                        val newCorners = if (lastDirection != direction) corners + 1 else corners

                        getOptimalPath(
                            moves,
                            n,
                            index,
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

                        visited[index] = false
                        path.removeAt(path.size - 1) //TODO: SLOW
                    }
                }
            }

            return newPath?.let { Pair(it, leastCorners) } //TODO: VERY VERY SLOW
        }

        private fun getSpaces(): List<BlockPos> {
            val spaces = mutableListOf(start)
            val queue = mutableListOf(start)

            while (queue.isNotEmpty()) {
                val current = queue.removeLast()
                EnumFacing.HORIZONTALS.forEach { direction ->
                    val next = current.offset(direction)
                    if (next !in spaces && world.getBlockState(next).block === Blocks.air &&
                        Utils.equalsOneOf(world.getBlockState(next.down()).block, Blocks.ice, Blocks.packed_ice)
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