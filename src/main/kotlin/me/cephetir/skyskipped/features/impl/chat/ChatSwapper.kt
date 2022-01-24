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

package me.cephetir.skyskipped.features.impl.chat

import gg.essential.api.EssentialAPI
import me.cephetir.skyskipped.config.Cache
import me.cephetir.skyskipped.config.Config
import me.cephetir.skyskipped.features.Feature
import net.minecraft.client.Minecraft
import net.minecraftforge.client.event.ClientChatReceivedEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

class ChatSwapper : Feature() {

    @SubscribeEvent
    fun onChat(event: ClientChatReceivedEvent) {
        if (!EssentialAPI.getMinecraftUtil().isHypixel()) return
        if ((event.message.unformattedText.startsWith("You have been kicked from the party") || event.message.unformattedText.contains("has disbanded") || event.message.unformattedText.startsWith("You left the party") || event.message.unformattedText.contains("was disbanded")) && Cache.inParty) {
            Minecraft.getMinecraft().thePlayer.sendChatMessage("/chat all")
            Cache.inParty = false
        } else if ((event.message.unformattedText.startsWith("You have joined") || event.message.unformattedText.startsWith("Party Members") || event.message.unformattedText.contains("joined the ")) && !Cache.inParty) {
            Minecraft.getMinecraft().thePlayer.sendChatMessage("/chat p")
            Cache.inParty = true
        }
    }

    override fun isEnabled(): Boolean {
        return Config.chatSwapper
    }
}