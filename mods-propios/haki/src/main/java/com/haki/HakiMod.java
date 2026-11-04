package com.haki;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@Mod(HakiMod.MODID)
public class HakiMod {
    public static final String MODID = "haki";
    public static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    public HakiMod(IEventBus modBus, ModContainer container) {
        HakiEffects.EFFECTS.register(modBus);
        HakiSounds.SOUNDS.register(modBus);
        modBus.addListener(this::registerPayloads);
        NeoForge.EVENT_BUS.register(HakiServer.class);
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(HakiKeyPayload.TYPE, HakiKeyPayload.CODEC, HakiServer::onKey);
        // Las lambdas evitan cargar clases de cliente en un servidor dedicado
        registrar.playToClient(HakiStatePayload.TYPE, HakiStatePayload.CODEC,
            (payload, ctx) -> ctx.enqueueWork(() -> com.haki.client.HakiClientFx.onState(payload)));
        registrar.playToClient(HakiAffectedPayload.TYPE, HakiAffectedPayload.CODEC,
            (payload, ctx) -> ctx.enqueueWork(() -> com.haki.client.HakiClientFx.onAffected(payload)));
    }
}
