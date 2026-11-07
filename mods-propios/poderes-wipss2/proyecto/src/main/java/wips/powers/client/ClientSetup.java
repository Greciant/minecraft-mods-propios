package wips.powers.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import wips.powers.WipsPowers;

/** Registra en el cliente quien dibuja cada tipo de efecto que manda el servidor (WipsFx). */
@EventBusSubscriber(modid = WipsPowers.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientSetup {
    private ClientSetup() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        GravitonScalerFx.register();
        // un efecto propio por habilidad (reemplaza las capas genericas de ElementalFx/ArcaneFx/SampleFx,
        // que solo conservan las estelas de proyectiles en su tick)
        AbilityFx.register();
        AwakenGlow.register();
    }

    @SubscribeEvent
    public static void onGuiLayers(net.neoforged.neoforge.client.event.RegisterGuiLayersEvent event) {
        LevelHud.register(event);
    }
}
