package com.haki;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import com.haki.client.HakiScreenFx;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = HakiMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class HakiClient {
    public static final KeyMapping HAKI_KEY = new KeyMapping(
        // Boton lateral "atras" del mouse: en WIPSS2 la G y casi todas las letras ya estan ocupadas
        "key.haki.king", InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_4, "key.categories.haki");

    /** PAL exige registrar sus capas dentro de enqueueWork del setup de cliente. */
    @SubscribeEvent
    public static void setup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        event.enqueueWork(com.haki.client.pal.HakiPalBridge::register);
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(HAKI_KEY);
    }

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, HakiScreenFx.ID, HakiScreenFx::render);
    }
}
