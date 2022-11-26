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

package me.cephetir.skyskipped.features.impl.dugeons

import me.cephetir.bladecore.core.event.listener.listener
import me.cephetir.bladecore.utils.TextUtils.stripColor
import me.cephetir.skyskipped.config.Cache
import me.cephetir.skyskipped.config.Config
import me.cephetir.skyskipped.features.Feature
import me.cephetir.skyskipped.utils.skyblock.PingUtils
import me.cephetir.skyskipped.utils.skyblock.Queues
import net.minecraft.entity.monster.EntityZombie
import net.minecraftforge.client.event.ClientChatReceivedEvent
import net.minecraftforge.event.entity.living.LivingDeathEvent
import net.minecraftforge.event.world.WorldEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import kotlin.math.max
import kotlin.math.roundToInt

class Pings : Feature() {
    private var ffTimer = -1L

    init {
        listener<ClientChatReceivedEvent> {
            if (!Cache.inDungeon || !Config.fireFreezePing) return@listener
            val msg = it.message.unformattedText.stripColor()
            if (msg.startsWith("[BOSS] The Professor: Oh? You found my Guardians one weakness?")) {
                ffTimer = System.currentTimeMillis() + 5000L
                PingUtils(110, "", false) {
                    val seconds = max(0.0, ((this.ffTimer - System.currentTimeMillis()) / 10.0).roundToInt() / 100.0)
                    "§4Use §c§lFire §3§lFreeze §4in: §c${seconds}s"
                }
            }
        }
    }

    private var rabPing = false
    private var mimicPing = false

    @SubscribeEvent
    fun onWorldLoad(event: WorldEvent.Load) {
        rabPing = false
        mimicPing = false
    }

    @SubscribeEvent
    fun onChat(event: ClientChatReceivedEvent) {
        if (!Cache.inDungeon || !Config.rabbitPing || rabPing) return
        if (event.message.unformattedText.stripColor().contains("You have proven yourself. You may pass")) {
            PingUtils(50, "Rabbit Hat!")
            rabPing = true
        }
    }

    @SubscribeEvent
    fun onEntityDeath(event: LivingDeathEvent) {
        if (!Cache.inDungeon || !Config.mimic || mimicPing) return
        if (event.entity is EntityZombie) {
            val entity = event.entity as EntityZombie
            if (entity.isChild &&
                entity.getCurrentArmor(0) == null &&
                entity.getCurrentArmor(1) == null &&
                entity.getCurrentArmor(2) == null &&
                entity.getCurrentArmor(3) == null
            ) {
                Queues.sendMessage("/pc " + Config.mimicText)
                mimicPing = true
            }
        }
    }
}
