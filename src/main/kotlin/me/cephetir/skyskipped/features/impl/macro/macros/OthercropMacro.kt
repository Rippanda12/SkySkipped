/*
 * SkySkipped - Hypixel Skyblock QOL mod
 * Copyright (C) 2023  Cephetir
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

package me.cephetir.skyskipped.features.impl.macro.macros

import gg.essential.api.utils.Multithreading
import gg.essential.universal.UChat
import me.cephetir.bladecore.core.event.BladeEventBus
import me.cephetir.bladecore.core.listeners.SkyblockIsland
import me.cephetir.bladecore.core.listeners.SkyblockListener
import me.cephetir.bladecore.utils.HttpUtils
import me.cephetir.bladecore.utils.TextUtils.keepScoreboardCharacters
import me.cephetir.bladecore.utils.TextUtils.stripColor
import me.cephetir.bladecore.utils.minecraft.skyblock.ScoreboardUtils
import me.cephetir.bladecore.utils.player
import me.cephetir.bladecore.utils.threading.safeListener
import me.cephetir.skyskipped.config.Cache
import me.cephetir.skyskipped.config.Config
import me.cephetir.skyskipped.features.impl.macro.Macro
import me.cephetir.skyskipped.features.impl.macro.MacroManager
import me.cephetir.skyskipped.features.impl.macro.failsafes.Failsafes
import me.cephetir.skyskipped.utils.InventoryUtils
import me.cephetir.skyskipped.utils.RotationClass
import net.minecraft.block.Block
import net.minecraft.client.gui.GuiChat
import net.minecraft.client.gui.GuiDisconnected
import net.minecraft.client.gui.GuiMainMenu
import net.minecraft.client.gui.GuiMultiplayer
import net.minecraft.client.gui.inventory.GuiInventory
import net.minecraft.client.multiplayer.GuiConnecting
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.settings.KeyBinding
import net.minecraft.init.Blocks
import net.minecraft.init.Items
import net.minecraft.util.BlockPos
import net.minecraft.util.IChatComponent
import net.minecraftforge.client.event.ClientChatReceivedEvent
import net.minecraftforge.fml.common.ObfuscationReflectionHelper
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase
import kotlin.math.*

class OthercropMacro : Macro("OtherCrops") {
    private val events = Events(this)

    // States
    private var farmDirection = FarmDirection.NORTH
    private var farmType = FarmType.DROPDOWN

    private var movementDirection = MovementDirection.LEFT
    private var farmingState = FarmingState.SETUP

    private var lastFps = 60
    private var lastDist = 8

    // Temp data
    private var rotating: RotationClass? = null
    private var rotated = false
    private var forward = false
    private var lastyaw = -1f
    private var lastpitch = -1f
    private var spawnTimer = 0L
    private var lastY = -1
    private var dced = false
    private var banwave = false

    //
    // Failsafes
    //

    // Jacob Event
    private var stoppedForEvent = false

    // Ban wave checker
    private var checkerTicks = 0
    private var checkerStopped = false

    class Events(private val macro: OthercropMacro) {
        init {
            safeListener<ClientTickEvent> { macro.onTick(it) }
            safeListener<ClientChatReceivedEvent> { macro.onChat(it.message.unformattedText.stripColor().keepScoreboardCharacters()) }
        }
    }

    override fun info() = "Macro: Other crops Macro, Settings: ${farmDirection.name}, ${farmType.name}, State: ${farmingState.name}"

    override fun isBanwave(): String = if (banwave) "False" else "True"
    override fun banwaveCheckIn(): Long = checkerTicks / 20 * 1000L

    override fun toggle() {
        enabled = !enabled
        if (enabled) onEnable()
        else onDisable()
    }

    private fun onEnable() {
        reset()
        unpressKeys()
        BladeEventBus.subscribe(events)
        UChat.chat("§cSkySkipped §f:: §eOther Crops Macro §aEnabled§e! Settings: ${farmDirection.name}, ${farmType.name}")
        mc.thePlayer.inventory.currentItem = Config.autoPickSlot.value.toInt() - 1
    }

    private fun onDisable() {
        if (Config.macroCpuSaver.value) {
            mc.gameSettings.limitFramerate = lastFps
            mc.gameSettings.renderDistanceChunks = lastDist
        }

        BladeEventBus.unsubscribe(events)
        unpressKeys()
        reset()
        UChat.chat("§cSkySkipped §f:: §eOther Crops Macro §cDisabled§e!")
    }

    private fun reset() {
        farmDirection = FarmDirection.values()[Config.otherCropDirection.value]
        farmType = FarmType.values()[Config.otherCropType.value]
        movementDirection = MovementDirection.LEFT
        farmingState = FarmingState.SETUP
        inGarden = SkyblockListener.island == SkyblockIsland.Garden

        rotating = null
        rotated = false
        forward = false
        lastyaw = -1f
        lastpitch = -1f
        spawnTimer = 0L
        lastY = -1
        dced = false
        banwave = false

        stoppedForEvent = false

        checkerTicks = 0
        checkerStopped = false

        banwave = false
        hotbarTicks = 0
        rotationTicks = 0

        Failsafes.reset()
    }

    fun onTick(event: ClientTickEvent) {
        if ((mc.thePlayer == null || mc.theWorld == null) && !dced) return checkBan()
        when (event.phase) {
            Phase.START -> onTickPre()
            Phase.END -> onTickPost()
            null -> return
        }
    }

    fun onChat(message: String) {
        if (farmingState == FarmingState.FARM) {
            if (message.startsWith("From"))
                sendWebhook("Received Message", message, false)
            else if (message.contains("is visiting Your Island"))
                sendWebhook("Somebody is visiting you", message, false)
            else if (message.contains("has invited you to join their party!"))
                sendWebhook("Received Party Request", message, false)
            else if (message.startsWith("[Important] This server will restart soon:")) {
                unpressKeys()
                mc.thePlayer.sendChatMessage("/setspawn")
                printdev("Detected server reboot")
                sendWebhook("Server Reboot", message, false)
                farmingState = FarmingState.DESYNED
                Failsafes.desyncWaitTimer = System.currentTimeMillis()
                Failsafes.desyncF = true
            }

            return
        }

        if (!stoppedForEvent) return
        if (!message.contains("The Farming Contest is over", true)) return
        printdev("Detected jacob msg in chat")
        UChat.chat("§cSkySkipped §f:: §eJacob event ended! Starting macro again...")
        farmingState = FarmingState.SETUP
        stoppedForEvent = false
    }

    private fun onTickPre() {
        when (farmingState) {
            FarmingState.SETUP -> setup()
            FarmingState.FARM -> {
                if (applyFailsafes()) return
                checkDirection()
                checkRotation()
            }

            FarmingState.CLIMB -> climb()
            FarmingState.STUCK -> Failsafes.stuck { farmingState = FarmingState.SETUP }
            FarmingState.DESYNED -> Failsafes.desynced(true) { farmingState = FarmingState.SETUP }
            FarmingState.WARPED -> Failsafes.warpBack { farmingState = FarmingState.SETUP }
            FarmingState.CLEAR_INV -> Failsafes.clearInv { farmingState = FarmingState.SETUP }
            FarmingState.BEDROCK_CAGE -> Failsafes.bedrockCage { farmingState = FarmingState.IDLE }
            FarmingState.IDLE -> {
                unpressKeys()
                if (dced) {
                    if (mc.thePlayer == null || mc.theWorld == null) return
                    farmingState = FarmingState.SETUP
                }
            }
        }
    }

    private fun setup() {
        //unpressKeys()
        mc.displayGuiScreen(null)
        if (!Cache.onIsland && Config.warpBackFailsafe.value) {
            farmingState = FarmingState.WARPED
            return
        }
        if (rotating == null) {
            val ya = try {
                Config.customYaw.value.toFloat()
            } catch (ex: NumberFormatException) {
                ex.printStackTrace()
                69420f
            }
            val pi = try {
                Config.customPitch.value.toFloat()
            } catch (ex: NumberFormatException) {
                ex.printStackTrace()
                69420f
            }
            val yaw = if (Config.customYawToggle.value && ya != 69420f) ya
            else when (farmDirection) {
                FarmDirection.NORTH -> 180f
                FarmDirection.SOUTH -> 0f
                FarmDirection.WEST -> 90f
                FarmDirection.EAST -> -90f
            }
            val pitch = if (Config.customPitchToggle.value && pi != 69420f) pi else 5f
            printdev("Rotate yaw and pitch: $yaw $pitch")
            rotating = RotationClass(RotationClass.Rotation(yaw, pitch), if (yaw > 80 || pitch > 80) 1500L else 750L)
        }
        if (rotating!!.done) {
            printdev("Finished rotating")

            if (Config.macroCpuSaver.value) {
                lastFps = mc.gameSettings.limitFramerate
                mc.gameSettings.limitFramerate = 30
                lastDist = mc.gameSettings.renderDistanceChunks
                mc.gameSettings.renderDistanceChunks = 2
            }

            farmingState = FarmingState.FARM
            rotating = null
            rotated = true
            lastY = ceil(mc.thePlayer.posY).roundToInt()
        }
    }

    private fun climb() {
        unpressKeys()
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.keyCode, true)

        val ladderBlock = mc.theWorld.getBlockState(BlockPos(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ))
        if (ladderBlock.block != Blocks.ladder) {
            printdev("Finished climbing")
            farmDirection = when (farmDirection) {
                FarmDirection.SOUTH -> FarmDirection.NORTH
                FarmDirection.WEST -> FarmDirection.EAST
                FarmDirection.NORTH -> FarmDirection.SOUTH
                FarmDirection.EAST -> FarmDirection.WEST
            }
            farmingState = FarmingState.SETUP
            rotating = null
        }
    }

    private fun onTickPost() {
        if (farmingState != FarmingState.FARM) return
        unpressKeys()
        if (mc.currentScreen != null && mc.currentScreen !is GuiChat) return

        KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.keyCode, true)
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.keyCode, forward)
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.keyCode, false)
        val flag = movementDirection == MovementDirection.LEFT
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindLeft.keyCode, flag && !forward)
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.keyCode, !flag && !forward)
    }

    override fun stopAndOpenInv() {
        unpressKeys()
        farmingState = FarmingState.IDLE
        mc.displayGuiScreen(GuiInventory(mc.thePlayer))
    }

    override fun closeInvAndReturn() {
        unpressKeys()
        mc.displayGuiScreen(null)
        farmingState = FarmingState.SETUP
    }

    private fun unpressKeys() {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.keyCode, false)
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.keyCode, false)
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.keyCode, false)
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindLeft.keyCode, false)
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.keyCode, false)
    }

    private val ignoreBlocks = listOf<Block>(
        Blocks.air,
        Blocks.water,
        Blocks.flowing_water,
        Blocks.wall_sign,
        Blocks.ladder,
        Blocks.trapdoor,
        Blocks.iron_trapdoor,
        Blocks.pumpkin_stem,
        Blocks.melon_stem,
    )

    private fun checkDirection() {
        var x = 0
        var x2 = 0
        var z = 0
        var z2 = 0
        when (farmDirection) {
            FarmDirection.SOUTH -> {
                x = 1
                z2 = 1
            }

            FarmDirection.WEST -> {
                z = 1
                x2 = -1
            }

            FarmDirection.NORTH -> {
                x = -1
                z2 = -1
            }

            FarmDirection.EAST -> {
                z = -1
                x2 = 1
            }
        }
        val y = ceil(mc.thePlayer.posY)

        val side =
            if (movementDirection == MovementDirection.LEFT) BlockPos(
                floor(mc.thePlayer.posX) + x,
                y,
                floor(mc.thePlayer.posZ) + z
            )
            else BlockPos(
                floor(mc.thePlayer.posX) - x,
                y,
                floor(mc.thePlayer.posZ) - z
            )

        val frwrd = BlockPos(
            floor(mc.thePlayer.posX) + x2,
            y,
            floor(mc.thePlayer.posZ) + z2
        )

        val sideBlock = mc.theWorld.getBlockState(side)
        val frwrdBlock = mc.theWorld.getBlockState(frwrd)

        // if (farmType == FarmType.LADDERS) {
        //     val ladderBlock = mc.theWorld.getBlockState(BlockPos(mc.thePlayer.posX, y, mc.thePlayer.posZ))
        //     if (ladderBlock.block == Blocks.ladder) {
        //         printdev("Detected ladder")
        //         farmingState = FarmingState.CLIMB
        //         return
        //     }
        // }

        if (farmType == FarmType.DROPDOWN) {
            if (lastY == -1) lastY = y.roundToInt()
            else if (abs(y - lastY) >= 2) {
                printdev("Detected Y change!")
                // farmDirection = when (farmDirection) {
                //     FarmDirection.SOUTH -> FarmDirection.NORTH
                //     FarmDirection.WEST -> FarmDirection.EAST
                //     FarmDirection.NORTH -> FarmDirection.SOUTH
                //     FarmDirection.EAST -> FarmDirection.WEST
                // }
                farmingState = FarmingState.SETUP
                // rotating = null
                return
            }
        }

        printdev("Checking direction")
        val motion = when (farmDirection) {
            FarmDirection.NORTH, FarmDirection.SOUTH -> mc.thePlayer.motionZ
            FarmDirection.WEST, FarmDirection.EAST -> mc.thePlayer.motionX
        }
        val moving = round(abs(motion) % 1.0 * 10000.0) / 10000.0

        printdev("Checking velo")
        if (moving != 0.0 && Config.macroLagbackFix.value) return
        printdev("Checking side block ${sideBlock.block.localizedName}")
        if (ignoreBlocks.contains(sideBlock.block)) return

        if (ignoreBlocks.contains(frwrdBlock.block)) {
            forward = true
            printdev("Going forward")
            return
        }
        forward = false

        movementDirection =
            if (movementDirection == MovementDirection.LEFT) MovementDirection.RIGHT
            else MovementDirection.LEFT
        printdev("Changing direction to ${movementDirection.name}")

        if (Config.otherCropSetSpawn.value && System.currentTimeMillis() - spawnTimer >= 1000) {
            UChat.chat("§cSkySkipped §f:: §eSetting spawnpoint...")
            mc.thePlayer.sendChatMessage("/sethome")
            spawnTimer = System.currentTimeMillis()
        }
    }

    private var rotationTicks = 0
    private fun checkRotation() {
        if (lastyaw == -1f || lastpitch == -1f || rotated) {
            lastyaw = mc.thePlayer.rotationYaw
            lastpitch = mc.thePlayer.rotationPitch
            rotated = false
            return
        }

        val yaw = mc.thePlayer.rotationYaw
        val pitch = mc.thePlayer.rotationPitch
        val check = (lastyaw !in (yaw - Config.rotationDiff.value)..(yaw + Config.rotationDiff.value) ||
                lastpitch !in (pitch - Config.rotationDiff.value)..(pitch + Config.rotationDiff.value))
        if (check && Config.soundFailsafes.value) {
            UChat.chat("§cSkySkipped §f:: §eRotation failsafe triggered!")
            player!!.playSound("random.anvil_land", 3f, 1f)
        } else if (check && ++rotationTicks > 40) {
            printdev("Detected rotation change")
            farmingState = FarmingState.SETUP
            rotating = null
            rotationTicks = 0
        }
    }

    private var hotbarTicks = 0
    private fun applyFailsafes(): Boolean {
        if (bedrockFailsafe()) {
            UChat.chat("§cSkySkipped §f:: §eBedrock detected! Applying cage failsafe...")
            sendWebhook("Bedrock Cage", "Found bedrock around player! Applying failsafe...", true)
            if (Config.soundFailsafes.value) {
                player!!.playSound("random.anvil_land", 3f, 1f)
                return false
            }
            farmingState = FarmingState.BEDROCK_CAGE
            Failsafes.bedrockTimer = System.currentTimeMillis() + 3500L
            return true
        }
        if (warpBackFailsafe()) return true
        if (captchaFailsafe()) return true
        if (fullInvFailsafe()) return true

        if (Config.autoWarpGarden.value) {
            val coords = runCatching { Config.autoWarpGardenCoords.value.split(",").map { it.trim().toInt() } }.getOrNull()
            if (coords == null || coords.size < 3) {
                UChat.chat("§cSkySkipped §f:: §4Failed to parse warp garden coords!")
                MacroManager.toggle()
                return true
            }
            if (coords[0] == truncate(player!!.posX).toInt() && coords[1] == truncate(player!!.posY).toInt() && coords[2] == truncate(player!!.posZ).toInt())
                player!!.sendChatMessage("/warp garden")
        }

        val unstuck = unstuckFailsafe()
        if (unstuck == 2) return true
        else if (unstuck == 1) return false

        val desync = desyncFailsafe()
        if (desync == 2) return true
        else if (desync == 1) return false

        if (jacobFailsafe()) return true
        banwaveChecker()

        if (mc.thePlayer.inventory.currentItem != Config.autoPickSlot.value.toInt() - 1 && ++hotbarTicks > 40) {
            UChat.chat("§cSkySkipped §f:: §eHotbar failsafe triggered!")
            if (Config.soundFailsafes.value) {
                player!!.playSound("random.anvil_land", 3f, 1f)
                return false
            }
            mc.thePlayer.inventory.currentItem = Config.autoPickSlot.value.toInt() - 1
            hotbarTicks = 20
        }
        return false
    }

    private fun bedrockFailsafe(): Boolean {
        if (!Config.otherCropBedrock.value) return false
        val pos1 = mc.thePlayer.position.add(-2, -2, -2)
        val pos2 = mc.thePlayer.position.add(2, 2, 2)

        for (pos in BlockPos.getAllInBox(pos1, pos2))
            if (mc.theWorld.getBlockState(pos).block == Blocks.bedrock)
                return true

        return false
    }

    private fun unstuckFailsafe(): Int {
        if (mc.currentScreen != null && mc.currentScreen !is GuiChat) return 0
        var stuck = 0
        if (!Config.otherCropStuck.value) return 0
        if (Failsafes.lastPos == null) Failsafes.lastPos = mc.thePlayer.position
        else {
            if (checkPos(mc.thePlayer.position)) {
                Failsafes.ticksStuck++
                if (Failsafes.ticksStuck >= 10) stuck = 1
            } else {
                Failsafes.lastPos = mc.thePlayer.position
                Failsafes.ticksStuck = 0
            }
        }

        if (Failsafes.ticksStuck >= 60) {
            printdev("Detected stuck")
            if (Config.soundFailsafes.value) {
                UChat.chat("§cSkySkipped §f:: §eUnstuck failsafe triggered!")
                player!!.playSound("random.anvil_land", 3f, 1f)
                return 0
            }
            farmingState = FarmingState.STUCK
            Failsafes.stuckSteps = Failsafes.StuckSteps.SETUP
            stuck = 2
        }
        return stuck
    }

    private fun desyncFailsafe(): Int {
        if (mc.currentScreen != null && mc.currentScreen !is GuiChat) return 0
        var desynced = 0
        if (!Config.otherCropDesync.value) return 0
        if (Failsafes.ticksWarpDesync >= 0) {
            Failsafes.ticksWarpDesync--
            return 0
        }

        val ticksTimeout = Config.otherCropDesyncTime.value * 20
        val stack = mc.thePlayer.heldItem
        if (stack == null ||
            !stack.hasTagCompound() ||
            !stack.tagCompound.hasKey("ExtraAttributes", 10)
        ) return 0
        var newCount = -1L
        val tag = stack.tagCompound
        if (tag.hasKey("ExtraAttributes", 10)) {
            val ea = tag.getCompoundTag("ExtraAttributes")
            if (ea.hasKey("mined_crops", 99))
                newCount = ea.getLong("mined_crops")
            else if (ea.hasKey("farmed_cultivating", 99))
                newCount = ea.getLong("farmed_cultivating")
        }
        if (newCount == -1L) return 0
        if (newCount > Failsafes.lastCount) {
            if (Failsafes.startCount == -1L) Failsafes.startCount = newCount
            Failsafes.lastCount = newCount
            Failsafes.ticksDesync = 0
        } else {
            Failsafes.ticksDesync++
            if (Failsafes.ticksDesync >= ticksTimeout / 3) desynced = 1
        }

        if (Failsafes.ticksDesync >= ticksTimeout) {
            printdev("Detected desync")
            if (Config.soundFailsafes.value) {
                UChat.chat("§cSkySkipped §f:: §eDesync failsafe triggered!")
                player!!.playSound("random.anvil_land", 3f, 1f)
                return 0
            }
            farmingState = FarmingState.DESYNED
            Failsafes.desyncedSteps = Failsafes.DesyncedSteps.SETUP
            desynced = 2
        }
        return desynced
    }

    private fun warpBackFailsafe(): Boolean {
        if (Cache.onIsland || !Config.warpBackFailsafe.value) return false
        printdev("Detected not on island")
        farmingState = FarmingState.WARPED

        UChat.chat("§cSkySkipped §f:: §eDetected not on island! Warping back...")
        sendWebhook("Warp failsafe", "Detected not on island! Warping back...", false)
        return true
    }

    private fun jacobFailsafe(): Boolean {
        if (!Config.otherCropJacob.value) return false
        if (!Cache.isJacob) return false
        printdev("Jacob event is on!")

        val lines = ScoreboardUtils.sidebarLines.map { it.stripColor().keepScoreboardCharacters().trim() }
        for (line in lines) {
            if (!line.contains("with")) continue
            val split = line.split(" ")
            if (split.size != 3) return false
            val number = split[2].replace(",", "").toInt()
            printdev("Jacob crop amount $number")
            if (number >= Config.otherCropJacobNumber.value) {
                printdev("Jacob detected!")
                UChat.chat("§cSkySkipped §f:: §eJacob event started! Stopping macro...")
                sendWebhook("Jacob event", "Jacob event started! Stopping macro...", false)
                if (Config.soundFailsafes.value) {
                    player!!.playSound("random.anvil_land", 3f, 1f)
                    return false
                }
                farmingState = FarmingState.IDLE
                stoppedForEvent = true
                return true
            }
            return false
        }
        printdev("Cant find funny numbers line :crying:")
        return false
    }

    private fun fullInvFailsafe(): Boolean {
        if (mc.currentScreen != null && mc.currentScreen !is GuiChat) return false
        if (!Config.otherCropFullInv.value) return false

        if (InventoryUtils.isFull()) {
            printdev("Inventory is full!")
            Failsafes.fullInvTicks++
        } else Failsafes.fullInvTicks--

        if (Failsafes.fullInvTicks >= 50) {
            printdev("Triggering full invenory failsafe!")
            farmingState = FarmingState.CLEAR_INV
            return true
        }

        return false
    }

    private fun banwaveChecker() {
        if (!Config.otherCropBanWaveChecker.value) return
        if (checkerTicks++ < Config.otherCropBanWaveCheckerTimer.value * 60 * 20) return

        Multithreading.runAsync {
            val status = HttpUtils.sendGet(
                "https://api.snipes.wtf/bancheck",
                mapOf("Content-Type" to "application/json")
            )
            if (status == "Nah") {
                banwave = false
                UChat.chat("§cSkySkipped §f:: §eBanwave: §aFalse")
                if (Config.otherCropBanWaveCheckerDisable.value && checkerStopped) {
                    UChat.chat("§cSkySkipped §f:: §eReenbabling macro...")
                    sendWebhook("Ban Wave Checker", "Ban Wave ended, reenabling macro...", false)
                    farmingState = FarmingState.IDLE
                    checkerStopped = false
                }
            } else if (status == "disconnect:all") {
                banwave = true
                UChat.chat("§cSkySkipped §f:: §eBanwave: §cTrue")
                if (Config.otherCropBanWaveCheckerDisable.value && !checkerStopped) {
                    UChat.chat("§cSkySkipped §f:: §eDisabling macro...")
                    sendWebhook("Ban Wave Checker", "Ban Wave started, disabling macro...", false)
                    farmingState = FarmingState.IDLE
                    checkerStopped = true
                }
            } else UChat.chat("§cSkySkipped §f:: §cCouldn't check current banwave status!")
        }
        checkerTicks = 0
    }

    private fun captchaFailsafe(): Boolean {
        val item = mc.thePlayer.heldItem?.item ?: return false
        if (item == Items.map || item == Items.filled_map) {
            farmingState = FarmingState.IDLE
            sendWebhook("Captcha failsafe", "Detected map in hands! Recommend solving it asap", true)
            return true
        }
        return false
    }

    private fun checkBan() {
        if (mc.currentScreen is GuiDisconnected) {
            if (Config.webhook.value) {
                val message = ObfuscationReflectionHelper.getPrivateValue<IChatComponent, GuiDisconnected>(
                    GuiDisconnected::class.java, mc.currentScreen as GuiDisconnected,
                    "message", "field_146304_f"
                )
                val reason = StringBuilder()
                for (line in message.siblings) reason.append(line.unformattedText)
                val rsn = reason.toString().replace("\r", "\\r").replace("\n", "\\n")
                sendWebhook("Disconnected", "You got disconnected with reason:\\n$rsn", true)
            }

            if (Config.otherCropReconnect.value) {
                farmingState = FarmingState.IDLE
                dced = true
                mc.displayGuiScreen(
                    GuiConnecting(
                        GuiMultiplayer(GuiMainMenu()),
                        mc,
                        ServerData(Cache.prevName, Cache.prevIP, Cache.prevIsLan)
                    )
                )
            } else MacroManager.toggle()
        }
    }

    private fun checkPos(player: BlockPos): Boolean =
        abs(Failsafes.lastPos!!.x - player.x) <= 2 && abs(Failsafes.lastPos!!.y - player.y) <= 2 && abs(Failsafes.lastPos!!.z - player.z) <= 2

    private enum class MovementDirection {
        LEFT,
        RIGHT
    }

    private enum class FarmingState {
        SETUP,
        FARM,
        CLIMB,
        STUCK,
        DESYNED,
        WARPED,
        CLEAR_INV,
        BEDROCK_CAGE,
        IDLE
    }

    private enum class FarmDirection {
        NORTH,
        EAST,
        WEST,
        SOUTH
    }

    private enum class FarmType {
        DROPDOWN
    }
}