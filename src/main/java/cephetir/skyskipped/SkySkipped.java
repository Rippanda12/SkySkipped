/*
 *
 * DO WHAT THE FUCK YOU WANT TO PUBLIC LICENSE
 * Version 2, December 2004
 *
 * Copyright (C) 2021 Cephetir
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

package cephetir.skyskipped;

import cephetir.skyskipped.Features.Features;
import cephetir.skyskipped.Features.impl.discordrpc.Client;
import cephetir.skyskipped.Features.impl.fragrun.LeaveCommand;
import cephetir.skyskipped.Features.impl.fragrun.PartyCommand;
import cephetir.skyskipped.commands.SkySkippedCommand;
import cephetir.skyskipped.config.Config;
import cephetir.skyskipped.listeners.Status;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLModDisabledEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

@Mod(modid = SkySkipped.MODID, name = SkySkipped.MOD_NAME, version = SkySkipped.VERSION, acceptedMinecraftVersions = "[1.8.9]", clientSideOnly = true)
public class SkySkipped {
    public static final String MODID = "skyskipped";
    public static final String MOD_NAME = "SkySkipped";
    public static final String VERSION = "1.9";
    public static final Minecraft mc = Minecraft.getMinecraft();
    public static Config config = new Config();
    public static Features features = new Features();

    @SuppressWarnings("unused")
    @Mod.Instance(MODID)
    public static SkySkipped Instance;

    @Mod.EventHandler()
    public void onPreInit(FMLPreInitializationEvent event) {
        System.out.println("Starting SkySkipped...");
        config.preload();
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Mod.EventHandler
    public void onInit(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new Client());
        MinecraftForge.EVENT_BUS.register(new Status());
        //MinecraftForge.EVENT_BUS.register(new RenderUtils());
        features.register();
        ClientCommandHandler.instance.registerCommand(new SkySkippedCommand());
        ClientCommandHandler.instance.registerCommand(new LeaveCommand());
        ClientCommandHandler.instance.registerCommand(new PartyCommand());
    }

    @Mod.EventHandler
    public void stop(FMLModDisabledEvent event) {
        Client.getINSTANCE().shutdown();
    }
}