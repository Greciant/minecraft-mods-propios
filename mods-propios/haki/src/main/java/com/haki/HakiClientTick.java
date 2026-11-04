package com.haki;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Envia al servidor cuando la tecla del Haki se pulsa o se suelta. */
@EventBusSubscriber(modid = HakiMod.MODID, value = Dist.CLIENT)
public class HakiClientTick {
    private static boolean wasDown = false;

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        boolean down = mc.player != null && mc.screen == null && HakiClient.HAKI_KEY.isDown();
        if (down != wasDown) {
            wasDown = down;
            PacketDistributor.sendToServer(new HakiKeyPayload(down));
        }
    }
}
