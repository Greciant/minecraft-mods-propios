package wips.powers.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import wips.powers.ice.IcePower;
import wips.powers.kit.WipsEntities;

/** Cliente del Hielo: render de los tempanos y la tecla de salto en el aire para las Pisadas de hielo. */
public final class IceClient {
    private static boolean steps;
    private static boolean jumpWasDown;

    private IceClient() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(IceClient::tick);
    }

    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(WipsEntities.ICE_SHARD.get(), ctx -> new ThrownItemRenderer<>(ctx, 1.4F, true));
    }

    public static void state(IcePower.IceSteps.State s) {
        steps = s.on();
    }

    private static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            steps = false;
            return;
        }
        boolean down = mc.options.keyJump.isDown();
        if (steps && down && !jumpWasDown && !mc.player.onGround() && !mc.player.isInWater() && !mc.player.getAbilities().flying
                && mc.screen == null) {
            PacketDistributor.sendToServer(IcePower.IceSteps.Jump.INSTANCE_PAYLOAD);
        }
        jumpWasDown = down;
    }
}
