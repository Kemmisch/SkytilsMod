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

package gg.skytils.skytilsmod.features.impl.misc

import gg.essential.universal.UChat
import gg.essential.universal.utils.MCClickEventAction
import gg.essential.universal.wrappers.message.UMessage
import gg.essential.universal.wrappers.message.UTextComponent
import gg.skytils.skytilsmod.Skytils
import gg.skytils.skytilsmod.Skytils.Companion.mc
import gg.skytils.skytilsmod.commands.impl.JoinCommand.processCommand
import gg.skytils.skytilsmod.events.impl.SendChatMessageEvent
import gg.skytils.skytilsmod.utils.Utils
import gg.skytils.skytilsmod.utils.append
import gg.skytils.skytilsmod.utils.printDevMessage
import gg.skytils.skytilsmod.utils.setHoverText
import net.minecraftforge.client.event.ClientChatReceivedEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import org.apache.http.util.Args
import kotlin.random.Random.Default.nextBoolean
import kotlin.random.Random.Default.nextInt

/**
 * Inspired by https://www.chattriggers.com/modules/v/HypixelUtilities
 */
object PartyAddons {

    private val youJoinedPartyRegex = Regex("§eYou have joined §r(?<rank>§.(\\S*)?) ?(?<name>[^']*)'s §r§eparty!§r")
    private val receievedMessageRegex = Regex("§dFrom §?r?(?<rank>§.(\\S*)?) ?(?<name>[^§]*)§r§7: §r§7(?<text>[^§]*)§r")
    private val otherJoinedPartyRegex = Regex("(?<rank>§.(\\S*)?) ?(?<name>\\w*) §r§ejoined the party.§r")
    private val transferRegex = Regex("§eThe party was transferred to §r(?<rankTo>§.(\\S*)?) ?(?<nameTo>\\w) §r§eby §r(?<rankFrom>§.(\\S*)?) ?(?<nameFrom>[^§]*)§r")
    private val demoteRegex = Regex("(?<rankDemoter>§.(\\S*)?) ?(?<demoter>[^§]*)§r§e has demoted §?.?(?<rankDemotee>§.(\\S*)?) ?(?<demotee>[^§]*)§r§eto Party (?<newRole>[^§]*)§r")
    private val promoteRegex = Regex("(?<rankPromoter>§.(\\S*)?) ?(?<promoter>[^§]*)§r§e has promoted §?.?(?<rankPromotee>§.(\\S*)?) ?(?<promotee>[^§]*)§r§eto Party (?<newRole>[^§]*)§r")
    private val youLeftPartyString = "§eYou left the party.§r"
    private val emptyPartyDisbandString = "§cThe party was disbanded because all invites expired and the party was empty.§r"
    private val forceDisbandRegex = Regex("(?<rank>§.(\\S*)?) ?(?<name>\\w) §r§ehas disbanded the party!§r")
    private val partyMessageRegex = Regex("§r§9Party §8> (?<rank>§.(\\S*)?) ?(?<name>[^§]*)§f: §r(?<text>[^§]*)§r")
    private val partyFinderRegex = Regex("^Party Finder > (?<name>\\w+) joined the dungeon group! \\((?<class>Archer|Berserk|Mage|Healer|Tank) Level (?<classLevel>\\d+)\\)$")
    private val inviteOtherRegex = Regex("(?<rankInviter>§.(\\S*)?) ?(?<nameInviter>\\w) §r§einvited §r(?<rankInvited>§.(\\S*)?) ?(?<nameInvited>\\w) §r§eto the party! They have §r§c60 §r§eseconds to accept.§r")

    private var inParty = false
    private var isLeader = false
    private var isMod = false

    // party commands to add
    // !inv <name>, !kick <name>, !ptme, !pt <name>, !<instance>


    private val partyStartPattern = Regex("^§6Party Members \\((\\d+)\\)§r$")
    private val playerPattern = Regex("(?<rank>§r§.(?:\\[.+?] )?)(?<name>\\w+) ?§r(?<status>§a|§c) ?● ?")
    private val party = mutableListOf<PartyMember>()
    private val partyCommands = setOf("/pl", "/party list", "/p list", "/party l")

    //0 = not awaiting, 1 = awaiting 2nd delimiter, 2 = awaiting 1st delimiter
    private var awaitingDelimiter = 0

    @SubscribeEvent
    fun onCommandRun(event: SendChatMessageEvent) {
        if (!Utils.isOnHypixel || !Skytils.config.partyAddons) return
        if (event.message in partyCommands) {
            awaitingDelimiter = 2
        }
    }

    @SubscribeEvent
    fun onChat(event: ClientChatReceivedEvent) {
        if (!Utils.isOnHypixel || event.type == 2.toByte() || !Skytils.config.partyAddons) return
        val message = event.message.formattedText

        if (message == "§f§r" && awaitingDelimiter != 0) {
            event.isCanceled = true
        } else if (partyStartPattern.matches(message)) {
            party.clear()
            event.isCanceled = true
        } else if (message.startsWith("§eParty ")) {
            val playerType = when {
                message.startsWith("§eParty Leader: ") -> PartyMemberType.LEADER
                message.startsWith("§eParty Moderators: ") -> PartyMemberType.MODERATOR
                message.startsWith("§eParty Members: ") -> PartyMemberType.MEMBER
                else -> return
            }
            playerPattern.findAll(message.substringAfter(": ")).forEach {
                it.destructured.let { (rank, name, status) ->
                    printDevMessage("Found Party Member: rank=$rank, name=$name, status=$status", "PartyAddons")
                    party.add(
                        PartyMember(
                            name,
                            playerType,
                            status,
                            rank
                        )
                    )
                }
            }
            event.isCanceled = true
        } else if (message.startsWith("§cYou are not currently in a party.") && awaitingDelimiter != 0) {
            party.clear()
        } else if (event.message.unformattedText.startsWith("-----") && awaitingDelimiter != 0) {
            awaitingDelimiter--
            if (awaitingDelimiter == 1 || party.isEmpty()) return

            val component = UMessage("§aParty members (${party.size})\n")

            val self = party.first { it.name == mc.thePlayer.name }

            if (self.type == PartyMemberType.LEADER) {
                isLeader = true
                inParty = true
                component.append(
                    createButton(
                        "§9[Warp] ",
                        "/p warp",
                        "§9Click to warp the party."
                    )
                ).append(
                    createButton(
                        "§e[All Invite] ",
                        "/p settings allinvite",
                        "§eClick to toggle all invite."
                    )
                ).append(
                    createButton(
                        "§6[Mute]\n",
                        "/p mute",
                        "§6Click to toggle mute."
                    )
                ).append(
                    createButton(
                        "§c[Kick Offline] ",
                        "/p kickoffline",
                        "§cClick to kick offline members."
                    )
                ).append(
                    createButton(
                        "§4[Disband]\n",
                        "/p disband",
                        "§4Click to disband the party."
                    )
                )
            }

            val partyLeader = party.first { it.type == PartyMemberType.LEADER }
            component.append(
                "\n§eLeader:§r ${partyLeader.status}➡§r ${partyLeader.rank}${partyLeader.name}\n"
            )

            val mods = party.filter { it.type == PartyMemberType.MODERATOR }
            if (mods.isNotEmpty()) {

                component.append("\n§eMods\n")
                mods.forEach {

                    if (mc.thePlayer.name == it.name) {
                        isMod = true
                        inParty = true
                        isLeader = false
                    }

                    if (self.type == PartyMemberType.LEADER) {
                        component.append(
                            createButton(
                                "§9[⋀] ",
                                "/p transfer ${it.name}",
                                "§9Transfer ${it.name}"
                            )
                        ).append(
                            createButton(
                                "§c[⋁] ",
                                "/p demote ${it.name}",
                                "§cDemote ${it.name}"
                            )
                        ).append(
                            createButton(
                                "§4[✖]  ",
                                "/p kick ${it.name}",
                                "§4Kick ${it.name}"
                            )
                        )
                    }

                    component.append(
                        "${it.status}➡§r ${it.rank}${it.name}\n"
                    )
                }
            }

            val members = party.filter { it.type == PartyMemberType.MEMBER }
            if (members.isNotEmpty()) {

                component.append("\n§eMembers\n")
                members.forEach {

                    if (mc.thePlayer.name == it.name) {
                        inParty = true
                        isLeader = false
                        isMod = false
                    }

                    if (self.type == PartyMemberType.LEADER) {
                        component.append(
                            createButton(
                                "§9[⋀] ",
                                "/p transfer ${it.name}",
                                "§9Transfer ${it.name}"
                            )
                        ).append(
                            createButton(
                                "§a[⋀] ",
                                "/p promote ${it.name}",
                                "§aPromote ${it.name}"
                            )
                        ).append(
                            createButton(
                                "§4[✖]  ",
                                "/p kick ${it.name}",
                                "§4Kick ${it.name}"
                            )
                        )
                    }

                    component.append(
                        "${it.status}➡§r ${it.rank}${it.name}\n"
                    )
                }
            }
            component.chat()
        } else if (message.matches(youJoinedPartyRegex)) {
            inParty = true
        } else if (message == youLeftPartyString || message.matches(forceDisbandRegex) || message == emptyPartyDisbandString) {
            inParty = false
        } else if (message.matches(otherJoinedPartyRegex) && !inParty) {
            inParty = true
            isLeader = true
        } else if (message.matches(transferRegex)) {
            val transferMessage = transferRegex.find(message)
            if (transferMessage?.groups?.get("nameTo")?.value == mc.thePlayer.name) {
                inParty = true
                isLeader = true
            } else if (transferMessage?.groups?.get("nameFrom")?.value == mc.thePlayer.name) {
                inParty = true
                isLeader = false
                isMod = true
            }
        } else if (message.matches(demoteRegex)) {
            val demoteMessage = demoteRegex.find(message)
            if (demoteMessage?.groups?.get("demotee")?.value == mc.thePlayer.name) {
                inParty = true
                isLeader = false
                isMod = false
            }
        } else if (message.matches(promoteRegex)) {
            val promoteMessage = promoteRegex.find(message)
            if (promoteMessage?.groups?.get("promotee")?.value == mc.thePlayer.name) {
                inParty = true
                if (promoteMessage?.groups?.get("newRole")?.value == "Leader") {
                    isLeader = true
                    isMod = false
                } else {
                    isLeader = false
                    isMod = true
                }
            } else if (promoteMessage?.groups?.get("promotee")?.value == mc.thePlayer.name && promoteMessage?.groups?.get("newRole")?.value == "Leader") {
                isLeader = false
                isMod = true
            }
        } else if (message.matches(inviteOtherRegex)) {
            val inviteMessage = inviteOtherRegex.find(message)
            if (inviteMessage?.groups?.get("nameInviter")?.value == mc.thePlayer.name) {
                if (!inParty)
                inParty = true
                isLeader = true
            }

        } else if (message.matches(receievedMessageRegex)) {
            var text = receievedMessageRegex.find(message)?.groups?.get("text")?.value ?: return
            val name = receievedMessageRegex.find(message)?.groups?.get("name")?.value ?: return
            if (text.indexOf("!") != 0) return
            text = text.slice(1..<text.length)
            val args = if (text.contains(" ")) text.split(" ") else listOf(text)
            handlePartyCommand(args,name)

        } else if (message.matches(partyMessageRegex)) {
            var text = partyMessageRegex.find(message)?.groups?.get("text")?.value ?: return
            val name = partyMessageRegex.find(message)?.groups?.get("name")?.value ?: return
            if (text[0] != '!') return
            text = text.slice(1..<text.length)
            val args = if (text.contains(" ")) text.split(" ") else listOf(text)
            handlePartyCommand(args,name)
        } else if (message.matches(partyFinderRegex)) {
            if ((partyFinderRegex.find(message)?.groups?.get("name")?.value ?: return) == mc.thePlayer.name) inParty = true
        }
    }

    private fun createButton(text: String, command: String, hoverText: String): UTextComponent {
        return UTextComponent(text).setClick(MCClickEventAction.RUN_COMMAND, command).setHoverText(hoverText)
    }

    private fun handlePartyCommand(args: List<String>, name: String) {
        if (args[0] == "cf" && inParty) {
            Skytils.sendMessageQueue.add("/pc $name flipped ${if (nextBoolean()) "Heads" else "Tails"}")
        } else if (args[0] == "roll" && inParty) {
            if (args.size == 1) {
                Skytils.sendMessageQueue.add("/pc $name rolled a ${nextInt(1,6)}")
            } else if (args.size == 2) {
                Skytils.sendMessageQueue.add("/pc $name rolled a ${nextInt(1,args[1].toIntOrNull() ?: return)}")
            }
        } else if (args[0] == "inv") {
            if (!(isMod || isLeader || !inParty)) return
            if (args.size == 1) {
                Skytils.sendMessageQueue.add("/p invite $name")
            } else if (args.size == 2) {
                Skytils.sendMessageQueue.add("/p invite ${args[1]}")
            }
        } else if (args[0] == "ptme" && inParty && isLeader) {
            Skytils.sendMessageQueue.add("/p transfer $name")
        } else if (args[0] == "pt" && inParty && isLeader) {
            if (args.size == 2) {
                Skytils.sendMessageQueue.add("/p transfer ${args[1]}")
            }
        } else if (args[0] == "kick" && inParty && isLeader) {
            if (args.size == 1) {
                Skytils.sendMessageQueue.add("/p kick $name")
            } else if (args.size == 2) {
                Skytils.sendMessageQueue.add("/p kick ${args[1]}")
            }
        } else if (args[0] == "allinv" && inParty && isLeader) {
            Skytils.sendMessageQueue.add("/p settings allinvite")
        } else if (args[0] == "warp" && inParty && isLeader) {
            Skytils.sendMessageQueue.add("/p warp")
        } else if (args[0] == "kickoffline" && inParty && isLeader) {
            Skytils.sendMessageQueue.add("/p kickoffline")
        } else if (args[0] == "lbsw" && inParty && isLeader) {
            Skytils.sendMessageQueue.add("/play solo_insane_lucky")
        } else if (args[0] == "play" && inParty && isLeader && args.size == 2) {
            Skytils.sendMessageQueue.add("/play ${args[1]}")
        } else {
            if (!isLeader || !inParty) return
            processCommand(mc.thePlayer, args.toTypedArray())
        }
    }

    private data class PartyMember(
        val name: String,
        val type: PartyMemberType,
        val status: String,
        val rank: String
    )

    private enum class PartyMemberType {
        LEADER,
        MODERATOR,
        MEMBER
    }
}
