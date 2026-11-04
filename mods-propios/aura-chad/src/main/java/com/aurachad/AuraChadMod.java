package com.aurachad;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@Mod(AuraChadMod.MODID)
public class AuraChadMod {
    public static final String MODID = "aurachad";
    public static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    public AuraChadMod(IEventBus modBus, ModContainer container) {
        ChadSounds.SOUNDS.register(modBus);
        ChadParticles.PARTICLES.register(modBus);
        modBus.addListener(this::registerPayloads);
        NeoForge.EVENT_BUS.register(ChadServer.class);
        container.registerConfig(ModConfig.Type.CLIENT, ChadConfig.SPEC);
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(ChadKeyPayload.TYPE, ChadKeyPayload.CODEC, ChadServer::onKey);
        // Lambda para no cargar clases de cliente en un servidor dedicado
        registrar.playToClient(ChadStatePayload.TYPE, ChadStatePayload.CODEC,
            (payload, ctx) -> ctx.enqueueWork(() -> com.aurachad.client.ChadClient.onState(payload)));
    }
}
