package com.aurachad.client;

import com.aurachad.AuraChadMod;
import com.aurachad.ChadConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Contorno azul neon: el mismo modelo ya posado se dibuja varias veces, algo mas grande y emisivo (coincide con las
 * poses porque comparte las partes del modelo), mas un halo en el suelo.
 */
public final class ChadAuraLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath(AuraChadMod.MODID, "textures/fx/white.png");
    static final ResourceLocation HALO = ResourceLocation.fromNamespaceAndPath(AuraChadMod.MODID, "textures/fx/halo.png");
    private static final int FULL_BRIGHT = 0xF000F0;

    public ChadAuraLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (player.isInvisible()) return;
        float fade = ChadClient.fade(player.getId(), partialTick);
        if (fade <= 0.004f) return;
        float t = ageInTicks;
        float pulse = 0.85f + 0.15f * Mth.sin(t * 0.35f);
        // Pulso breve del contorno en cada golpe de pose: mas grande y mas brillante, y decae en ChadHits.PULSE_SECONDS
        float shown = ChadClient.ageSeconds(player.getId());
        float hit = shown < 0f ? 0f : Math.min(1f, ChadHits.pulseAt(shown));
        float boost = 1f + 1.6f * hit;
        float grow = 0.07f * hit;

        PlayerModel<AbstractClientPlayer> model = getParentModel();
        if (ChadConfig.SILHOUETTE.get()) {
            // Relleno casi negro un pelo mas grande que la piel
            VertexConsumer dark = buffers.getBuffer(RenderType.entityTranslucent(WHITE));
            pose.pushPose();
            pose.translate(0f, 0.75f, 0f);
            pose.scale(1.006f, 1.006f, 1.006f);
            pose.translate(0f, -0.75f, 0f);
            model.renderToBuffer(pose, dark, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, FastColor.ARGB32.color((int) (255f * Math.min(1f, fade * 1.5f)), 4, 8, 24));
            pose.popPose();
            // Contorno: caras traseras de cascos mayores; solo asoman por el borde de la silueta
            float[][] hulls = {{1.14f, 0.22f}, {1.08f, 0.45f}, {1.04f, 0.9f}};
            for (float[] s : hulls) {
                FlippedConsumer fc = new FlippedConsumer(buffers.getBuffer(RenderType.entityTranslucentCull(WHITE)));
                pose.pushPose();
                pose.translate(0f, 0.75f, 0f);
                pose.scale(s[0] + grow, s[0] + grow, s[0] + grow);
                pose.translate(0f, -0.75f, 0f);
                int a = Math.min(255, (int) (255f * s[1] * fade * pulse * boost));
                model.renderToBuffer(pose, fc, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, FastColor.ARGB32.color(a, 90, 200, 255));
                fc.flush();
                pose.popPose();
            }
        } else {
            VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(WHITE));
            // (escala, alfa): capas de afuera hacia adentro
            float[][] shells = {{1.16f, 0.10f}, {1.10f, 0.18f}, {1.05f, 0.34f}, {1.02f, 0.5f}};
            for (float[] s : shells) {
                pose.pushPose();
                pose.translate(0f, 0.75f, 0f);       // centro del cuerpo (el origen del modelo esta en los hombros... +0.75 sube a mitad)
                pose.scale(s[0] + grow, s[0] + grow, s[0] + grow);
                pose.translate(0f, -0.75f, 0f);
                int a = Math.min(255, (int) (255f * s[1] * fade * pulse * boost));
                model.renderToBuffer(pose, vc, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, FastColor.ARGB32.color(a, 70, 170, 255));
                pose.popPose();
            }
        }

        // Halo en el suelo (los pies estan a 1,5 bloques por debajo del origen del modelo)
        VertexConsumer hv = buffers.getBuffer(RenderType.entityTranslucentEmissive(HALO));
        pose.pushPose();
        pose.translate(0f, 1.49f, 0f);
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(t * 3f));
        float r = 1.5f + 0.12f * Mth.sin(t * 0.2f);
        PoseStack.Pose last = pose.last();
        int ha = (int) (255f * fade * 0.9f);
        // Plano horizontal; +Y del modelo apunta hacia abajo, asi que la normal visible es -Y
        quad(hv, last, r, ha);
        pose.popPose();
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose pose, float r, int alpha) {
        v(vc, pose, -r, 0, -r, 0, 0, alpha);
        v(vc, pose, -r, 0, r, 0, 1, alpha);
        v(vc, pose, r, 0, r, 1, 1, alpha);
        v(vc, pose, r, 0, -r, 1, 0, alpha);
        // cara de abajo tambien, para verlo desde cualquier lado
        v(vc, pose, r, 0, -r, 1, 0, alpha);
        v(vc, pose, r, 0, r, 1, 1, alpha);
        v(vc, pose, -r, 0, r, 0, 1, alpha);
        v(vc, pose, -r, 0, -r, 0, 0, alpha);
    }

    private static void v(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v, int a) {
        vc.addVertex(pose, x, y, z).setColor(255, 255, 255, a).setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(pose, 0f, -1f, 0f);
    }

    @EventBusSubscriber(modid = AuraChadMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Register {
        @SubscribeEvent
        public static void addLayers(EntityRenderersEvent.AddLayers event) {
            for (var skin : event.getSkins()) {
                if (event.getSkin(skin) instanceof PlayerRenderer renderer) renderer.addLayer(new ChadAuraLayer(renderer));
            }
        }
    }
}
