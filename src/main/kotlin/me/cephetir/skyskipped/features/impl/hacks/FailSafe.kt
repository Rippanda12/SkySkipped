/*
 *   
 * DO WHAT THE FUCK YOU WANT TO PUBLIC LICENSE
 * Version 2, December 2004
 *  
 * Copyright (C) 2022 Cephetir
 *
 * Everyone is permitted to copy and distribute verbatim or modified
 * copies of this license document, and changing it is allowed as long
 * as the name is changed.
 *  
 * DO WHAT THE FUCK YOU WANT TO PUBLIC LICENSE
 * TERMS AND CONDITIONS FOR COPYING, DISTRIBUTION AND MODIFICATION
 *
 *  0. You just DO WHAT THE FUCK YOU WANT TO.
 */

package me.cephetir.skyskipped.features.impl.hacks

import gg.essential.universal.UChat
import me.cephetir.skyskipped.config.Cache
import me.cephetir.skyskipped.config.Config
import me.cephetir.skyskipped.event.events.PacketReceive
import me.cephetir.skyskipped.features.Feature
import me.cephetir.skyskipped.mixins.IMixinSugarCaneMacro
import me.cephetir.skyskipped.utils.InventoryUtils
import me.cephetir.skyskipped.utils.TextUtils.keepScoreboardCharacters
import me.cephetir.skyskipped.utils.TextUtils.stripColor
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.inventory.GuiChest
import net.minecraft.client.gui.inventory.GuiInventory
import net.minecraft.client.settings.KeyBinding
import net.minecraft.init.Items
import net.minecraft.inventory.ContainerChest
import net.minecraft.inventory.ContainerPlayer
import net.minecraft.inventory.Slot
import net.minecraft.network.play.server.S3EPacketTeams
import net.minecraft.util.BlockPos
import net.minecraft.util.MathHelper
import net.minecraftforge.client.event.ClientChatReceivedEvent
import net.minecraftforge.fml.common.Loader
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent
import qolskyblockmod.pizzaclient.features.macros.builder.MacroBuilder
import qolskyblockmod.pizzaclient.features.macros.builder.macros.FarmingMacro
import qolskyblockmod.pizzaclient.features.macros.farming.SugarCaneMacro
import xyz.apfelmus.cf4m.CF4M
import xyz.apfelmus.cheeto.client.modules.world.AutoFarm
import java.lang.reflect.Field


class FailSafe : Feature() {
    companion object {
        var stuck = false
        var desynced = false

        var timer = System.currentTimeMillis()

        var called4 = false
    }

    private var pizza = false
    private var cheeto = false
    private var updateTicks = 0

    private var ticksWarpStuck = 0
    private var ticks = 0
    private var lastPos: BlockPos? = null
    private var called = false

    private var ticksWarpDesync = 0
    private var ticks2 = 0
    private var lastCount = 0
    private var called2 = false
    private var trigger = false

    private var lastState = false
    private var lastDirection: Any? = null

    private var called3 = false

    private var lastMacro = true

    private var ticks5 = 0
    private var called5 = false

    @SubscribeEvent
    fun onTick(event: ClientTickEvent) {
        if (updateTicks++ >= 20) update()
    }

    @SubscribeEvent
    fun unstuck(event: ClientTickEvent) {
        if (!Config.failSafe) ticks = 0
        if (!Config.failSafe || event.phase != TickEvent.Phase.START || mc.thePlayer == null || mc.theWorld == null) return
        if (called2 || called3 || called5) return

        if (pizza || cheeto) {
            if(ticksWarpStuck >= 0) {
                ticksWarpStuck--
                return
            }

            if (lastPos != null) {
                if (checkPos(mc.thePlayer.position)) {
                    ticks++
                    if (ticks >= 10) stuck = true
                } else {
                    lastPos = mc.thePlayer.position
                    ticks = 0
                    stuck = false
                }
            } else lastPos = mc.thePlayer.position

            if (ticks >= 60 && !called) {
                called = true
                Thread {
                    try {
                        val pizza = pizza
                        val cheeto = cheeto
                        UChat.chat("§cSkySkipped §f:: §eYou got stuck! Trying to prevent that...")
                        if (pizza) MacroBuilder.onKey()
                        else if (cheeto) CF4M.INSTANCE.moduleManager.toggle("AutoFarm")

                        if (Config.failsafeJump) KeyBinding.setKeyBindState(
                            mc.gameSettings.keyBindJump.keyCode,
                            true
                        )
                        Thread.sleep(100)

                        // back
                        KeyBinding.setKeyBindState(
                            mc.gameSettings.keyBindBack.keyCode,
                            true
                        )
                        Thread.sleep(300)
                        KeyBinding.setKeyBindState(
                            mc.gameSettings.keyBindBack.keyCode,
                            false
                        )
                        Thread.sleep(100)

                        // left
                        KeyBinding.setKeyBindState(
                            mc.gameSettings.keyBindLeft.keyCode,
                            true
                        )
                        Thread.sleep(300)
                        KeyBinding.setKeyBindState(
                            mc.gameSettings.keyBindLeft.keyCode,
                            false
                        )
                        Thread.sleep(100)

                        // right
                        KeyBinding.setKeyBindState(
                            mc.gameSettings.keyBindRight.keyCode,
                            true
                        )
                        Thread.sleep(300)
                        KeyBinding.setKeyBindState(
                            mc.gameSettings.keyBindRight.keyCode,
                            false
                        )
                        Thread.sleep(100)
                        if (Config.failsafeJump) KeyBinding.setKeyBindState(
                            mc.gameSettings.keyBindJump.keyCode,
                            false
                        )

                        if (pizza) MacroBuilder.onKey()
                        else if (cheeto) CF4M.INSTANCE.moduleManager.toggle("AutoFarm")

                        called = false
                        ticks = 0
                        ticksWarpStuck = 60
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                }.start()
            }
        }
    }

    @SubscribeEvent
    fun jacob(event: PacketReceive) {
        if (
            !Cache.inSkyblock ||
            event.packet !is S3EPacketTeams ||
            !Config.failSafeJacob
        ) return
        if (event.packet.action != 2) return
        val line = event.packet.players.joinToString(
            " ",
            prefix = event.packet.prefix,
            postfix = event.packet.suffix
        ).stripColor().trim()

        if (!Cache.isJacob || !(pizza || cheeto)) return
        if (!line.contains("with")) return
        val split = line.split(" ")
        if (split.size != 3) return
        val number = split[2].replace(",", "").toInt()
        printdev("Jacob crop amount $number")
        if (number >= Config.failSafeJacobNumber) {
            printdev("Jacob detected!")
            UChat.chat("§cSkySkipped §f:: §eJacob event failsafe triggered! Stopping macro...")
            if (pizza) {
                MacroBuilder.onKey()
                lastMacro = true
            } else if (cheeto) {
                CF4M.INSTANCE.moduleManager.toggle("AutoFarm")
                lastMacro = false
            }
            called4 = true
        }
    }

    @SubscribeEvent
    fun onChat(event: ClientChatReceivedEvent) {
        if (!called4) return
        if (!event.message.unformattedText.stripColor().keepScoreboardCharacters().contains("Come see me in the Hub", true)) return
        printdev("Detected jacob msg in chat")
        UChat.chat("§cSkySkipped §f:: §eJacob event ended! Starting macro again...")
        if (lastMacro) MacroBuilder.onKey()
        else CF4M.INSTANCE.moduleManager.toggle("AutoFarm")
        called4 = false
    }

    @SubscribeEvent
    fun desync(event: ClientTickEvent) {
        if (called || called2 || called3 || called5) return
        if (!Config.failSafeDesync) ticks2 = 0
        if (!Config.failSafeDesync || event.phase != TickEvent.Phase.START || mc.thePlayer == null || mc.theWorld == null) return

        if (pizza || cheeto) {
            if(ticksWarpDesync >= 0) {
                ticksWarpDesync--
                return
            }

            val ticksTimeout = Config.failSafeDesyncTime * 20
            val stack = Minecraft.getMinecraft().thePlayer.heldItem
            if (stack == null || !stack.hasTagCompound() || !stack.tagCompound.hasKey(
                    "ExtraAttributes",
                    10
                )
            ) return
            var newCount = -1
            val tag = stack.tagCompound
            if (tag.hasKey("ExtraAttributes", 10)) {
                val ea = tag.getCompoundTag("ExtraAttributes")
                if (ea.hasKey("mined_crops", 99))
                    newCount = ea.getInteger("mined_crops")
                else if (ea.hasKey("farmed_cultivating", 99))
                    newCount = ea.getInteger("farmed_cultivating")
            }
            printdev("Current counter: $newCount")
            if (newCount != -1 && newCount > lastCount) {
                lastCount = newCount
                ticks2 = 0
                desynced = false
            } else {
                ticks2++
                if (ticks2 >= ticksTimeout / 3) desynced = true
                if (ticks2 >= ticksTimeout) trigger = true
            }

            if (trigger && !called2) {
                printdev("Triggered desync failsafe!")
                called2 = true
                Thread {
                    try {
                        val extraDelay = Config.failSafeGlobalTime.toLong()
                        val pizza = pizza
                        val cheeto = cheeto
                        UChat.chat("§cSkySkipped §f:: §eDesync detected! Swapping lobbies...")
                        if (pizza) MacroBuilder.onKey()
                        else if (cheeto) CF4M.INSTANCE.moduleManager.toggle("AutoFarm")

                        val yaw = MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw)
                        val pitch = MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationPitch)

                        Thread.sleep(extraDelay)
                        mc.thePlayer.sendChatMessage("/hub")
                        Thread.sleep(5000L + extraDelay)
                        mc.thePlayer.sendChatMessage("/is")
                        Thread.sleep(2500L + extraDelay)
                        mc.thePlayer.rotationYaw += yaw - MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw)
                        mc.thePlayer.rotationPitch = pitch

                        Thread.sleep(100L + extraDelay)
                        if (pizza) MacroBuilder.onKey()
                        else if (cheeto) CF4M.INSTANCE.moduleManager.toggle("AutoFarm")

                        called2 = false
                        ticks2 = 0
                        trigger = false
                        ticksWarpDesync = 100
                        printdev("Ended resync process!")
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                }.start()
            }
        }
    }

    @SubscribeEvent
    fun autoSetSpawn(event: ClientTickEvent) {
        if (stuck || desynced) return
        if (!Config.failSafeSpawn || event.phase != TickEvent.Phase.START || mc.thePlayer == null || mc.theWorld == null) return

        if (pizza && MacroBuilder.currentMacro is SugarCaneMacro) {
            var switch = false
            if (lastState != (MacroBuilder.currentMacro as IMixinSugarCaneMacro).state) {
                lastState = (MacroBuilder.currentMacro as IMixinSugarCaneMacro).state
                switch = true
            }

            if (switch && System.currentTimeMillis() - timer > 500) {
                UChat.chat("§cSkySkipped §f:: §eSetting spawnpoint...")
                mc.thePlayer.sendChatMessage("/sethome")
                timer = System.currentTimeMillis()
            }
        } else if (cheeto) {
            var switch = false
            val f: Field = AutoFarm::class.java.getDeclaredField("farmingDirection")
            f.isAccessible = true
            val value = f.get(CF4M.INSTANCE.moduleManager.getModule("AutoFarm"))
            if (lastDirection == null) lastDirection = value
            else if (lastDirection != value) {
                lastDirection = value
                switch = true
            }

            if (switch && System.currentTimeMillis() - timer > 500) {
                UChat.chat("§cSkySkipped §f:: §eSetting spawnpoint...")
                mc.thePlayer.sendChatMessage("/sethome")
                timer = System.currentTimeMillis()
            }
        }
    }

    @SubscribeEvent
    fun autoWarpBack(event: ClientTickEvent) {
        if (called2 || called3 || called5) return
        if (!Config.failSafeIsland || event.phase != TickEvent.Phase.START || mc.thePlayer == null || mc.theWorld == null) return
        if (Cache.onIsland) return

        if (pizza || cheeto) {
            called3 = true
            Thread {
                try {
                    val pizza = pizza
                    val cheeto = cheeto
                    if (pizza) MacroBuilder.onKey()
                    else if (cheeto) CF4M.INSTANCE.moduleManager.toggle("AutoFarm")

                    val delay = Config.failSafeIslandDelay.toLong() * 1000L
                    val extraDelay = Config.failSafeGlobalTime.toLong()
                    Thread.sleep(delay + extraDelay)

                    if (Cache.onIsland) {
                        called3 = false
                        return@Thread
                    }

                    if (Cache.inSkyblock) {
                        UChat.chat("§cSkySkipped §f:: §eDetected hub! Warping back...")
                        mc.thePlayer.sendChatMessage("/is")
                    } else {
                        UChat.chat("§cSkySkipped §f:: §eDetected other lobby! Warping back...")
                        mc.thePlayer.sendChatMessage("/l")
                        Thread.sleep(delay + extraDelay)
                        mc.thePlayer.sendChatMessage("/play sb")
                        Thread.sleep(delay + extraDelay)
                        mc.thePlayer.sendChatMessage("/is")
                    }

                    Thread.sleep(delay + extraDelay)
                    if (pizza) MacroBuilder.onKey()
                    else if (cheeto) CF4M.INSTANCE.moduleManager.toggle("AutoFarm")

                    called3 = false
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }.start()
        }
    }

    @SubscribeEvent
    fun fullInv(event: ClientTickEvent) {
        if (called || called2 || called3 || called5 || desynced) return
        if (!Config.failSafeInv) ticks2 = 0
        if (!Config.failSafeInv || event.phase != TickEvent.Phase.START || mc.thePlayer == null || mc.theWorld == null) return
        if (!pizza && !cheeto) return

        if (InventoryUtils.isFull()) {
            printdev("Inventory is full!")
            ticks5++
        } else ticks5 = 0

        if (!called5 && ticks5 >= 50) {
            printdev("Triggering full invenory failsafe!")
            called5 = true
            Thread {
                try {
                    val pizza = pizza
                    val cheeto = cheeto
                    val extraDelay = Config.failSafeGlobalTime.toLong() / 2
                    UChat.chat("§cSkySkipped §f:: §eInventory is full! Cleanning...")
                    if (pizza) MacroBuilder.onKey()
                    else if (cheeto) CF4M.INSTANCE.moduleManager.toggle("AutoFarm")
                    Thread.sleep(1000L)

                    mc.displayGuiScreen(GuiInventory(mc.thePlayer))
                    Thread.sleep(1000L + extraDelay)

                    if (mc.currentScreen !is GuiInventory) {
                        printdev("Invenory closed")
                        called5 = false
                        ticks5 = 0
                        mc.thePlayer.closeScreen()
                        if (pizza) MacroBuilder.onKey()
                        else if (cheeto) CF4M.INSTANCE.moduleManager.toggle("AutoFarm")
                        return@Thread
                    }
                    val inv = ((mc.currentScreen as GuiInventory).inventorySlots as ContainerPlayer).inventorySlots
                    val stoneSlots = inv.filter {
                        it.hasStack && it.stack.displayName.stripColor().keepScoreboardCharacters().contains("Stone", true)
                    }
                    if (stoneSlots.isNotEmpty()) {
                        printdev("Stone detected!")
                        for (slot in stoneSlots) {
                            if (mc.currentScreen !is GuiInventory) {
                                printdev("Invenory closed")
                                called5 = false
                                ticks5 = 0
                                mc.thePlayer.closeScreen()
                                if (pizza) MacroBuilder.onKey()
                                else if (cheeto) CF4M.INSTANCE.moduleManager.toggle("AutoFarm")
                                return@Thread
                            }
                            mc.playerController.windowClick(
                                (mc.currentScreen as GuiInventory).inventorySlots.windowId,
                                slot.slotNumber, 0, 0, mc.thePlayer
                            )
                            mc.playerController.windowClick(
                                (mc.currentScreen as GuiInventory).inventorySlots.windowId,
                                -999, 0, 0, mc.thePlayer
                            )
                            Thread.sleep(500L + extraDelay)
                        }
                    } else printdev("no stone")
                    val crops = inv.filter {
                        it.hasStack && when (it.stack.item) {
                            Items.nether_wart -> true
                            Items.reeds -> true
                            Items.potato -> true
                            Items.carrot -> true
                            Items.melon -> true
                            else -> it.stack.displayName.contains("mushroom")
                        }
                    }
                    mc.thePlayer.closeScreen()
                    if (crops.isEmpty()) {
                        printdev("no crops")
                        called5 = false
                        ticks5 = 0
                        mc.thePlayer.closeScreen()
                        if (pizza) MacroBuilder.onKey()
                        else if (cheeto) CF4M.INSTANCE.moduleManager.toggle("AutoFarm")
                        return@Thread
                    }

                    Thread.sleep(1000L + extraDelay)
                    mc.thePlayer.sendChatMessage("/sbmenu")
                    Thread.sleep(1000L + extraDelay)

                    val startTime = System.currentTimeMillis()
                    var exit = false
                    var trades: Slot? = null
                    while (!exit) {
                        if (System.currentTimeMillis() - startTime >= 5000L) {
                            printdev("Cant find trades button")
                            called5 = false
                            ticks5 = 0
                            mc.thePlayer.closeScreen()
                            if (pizza) MacroBuilder.onKey()
                            else if (cheeto) CF4M.INSTANCE.moduleManager.toggle("AutoFarm")
                            return@Thread
                        }
                        Thread.sleep(100L)
                        exit = mc.currentScreen != null && mc.currentScreen is GuiChest
                        if (exit) {
                            trades = (mc.currentScreen as GuiChest).inventorySlots.inventorySlots.find {
                                it.hasStack && it.stack.displayName.stripColor() == "Trades"
                            }
                            exit = trades != null
                        }
                    }
                    mc.playerController.windowClick(
                        (mc.currentScreen as GuiChest).inventorySlots.windowId,
                        trades!!.slotIndex, 0, 0, mc.thePlayer
                    )
                    Thread.sleep(500L + extraDelay)
                    printdev("clicked trades")

                    val startTime2 = System.currentTimeMillis()
                    var sell: Slot? = null
                    while (sell == null) {
                        if (System.currentTimeMillis() - startTime2 >= 5000L || mc.currentScreen !is GuiChest) {
                            printdev("Cant find sell button")
                            called5 = false
                            ticks5 = 0
                            mc.thePlayer.closeScreen()
                            if (pizza) MacroBuilder.onKey()
                            else if (cheeto) CF4M.INSTANCE.moduleManager.toggle("AutoFarm")
                            return@Thread
                        }
                        Thread.sleep(100L)
                        sell = (mc.currentScreen as GuiChest).inventorySlots.inventorySlots.find {
                            it.hasStack && it.stack.item.unlocalizedName.contains("hopper")
                        }
                    }
                    printdev("found sell")

                    val inventory = ((mc.currentScreen as GuiChest).inventorySlots as ContainerChest).inventorySlots
                    val cropss = inventory.filter {
                        it.hasStack && when (it.stack.item) {
                            Items.nether_wart -> true
                            Items.reeds -> true
                            Items.potato -> true
                            Items.carrot -> true
                            Items.melon -> true
                            else -> it.stack.displayName.contains("mushroom")
                        }
                    }
                    if (cropss.isNotEmpty()) {
                        printdev("Crops found!")
                        for (slot in cropss) {
                            if (mc.currentScreen !is GuiChest) {
                                printdev("Invenory closed")
                                called5 = false
                                ticks5 = 0
                                mc.thePlayer.closeScreen()
                                if (pizza) MacroBuilder.onKey()
                                else if (cheeto) CF4M.INSTANCE.moduleManager.toggle("AutoFarm")
                                return@Thread
                            }
                            mc.playerController.windowClick(
                                (mc.currentScreen as GuiChest).inventorySlots.windowId,
                                slot.slotNumber, 0, 0, mc.thePlayer
                            )
                            Thread.sleep(500L + extraDelay)
                        }
                    } else printdev("no crops")

                    if (pizza) MacroBuilder.onKey()
                    else if (cheeto) CF4M.INSTANCE.moduleManager.toggle("AutoFarm")
                    called5 = false
                    ticks5 = 0
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }.start()
        }
    }

    private fun update() {
        pizza = Loader.isModLoaded("pizzaclient") && MacroBuilder.toggled && MacroBuilder.currentMacro is FarmingMacro
        cheeto = Loader.isModLoaded("ChromaHUD") && CF4M.INSTANCE.moduleManager.isEnabled("AutoFarm")
    }

    private fun checkPos(player: BlockPos): Boolean =
        lastPos!!.x - player.x <= 1 && lastPos!!.x - player.x >= -1 && lastPos!!.y - player.y <= 1 && lastPos!!.y - player.y >= -1 && lastPos!!.z - player.z <= 1 && lastPos!!.z - player.z >= -1
}