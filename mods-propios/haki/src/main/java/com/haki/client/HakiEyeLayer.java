package com.haki.client;

import com.haki.HakiMod;
import com.haki.HakiVisuals;
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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.minecraft.util.Mth;

/**
 * Brillo rojo de los ojos. Es una capa del modelo del jugador: se dibuja con el PoseStack ya dentro del
 * modelo y la parte "head" aplicada (translateAndRotate), igual que la capa de la cabeza. Asi sigue el
 * giro y la inclinacion de la cabeza, el agachado y cualquier animacion que mueva el ModelPart
 * (player-animation-lib, Emotecraft...), y queda por delante de la capa exterior del sombrero.
 */
public final class HakiEyeLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public HakiEyeLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int packedLight, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (HakiClientFx.CASTERS.isEmpty() || player.isInvisible()) return;
        HakiClientFx.Caster c = HakiClientFx.CASTERS.get(player.getId());
        if (c == null) return;
        float fade = c.prevFade + (c.fade - c.prevFade) * partialTick;
        if (fade <= 0.001f) return;
        float t = c.age + partialTick;
        float flicker = 1f - HakiVisuals.EYE_FLICKER * (0.5f + 0.5f * Mth.sin(t * 1.9f) * Mth.sin(t * 0.7f));
        float a = fade * flicker;

        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(HakiFxRender.EYE_TEX));
        pose.pushPose();
        // Origen del ModelPart "head" (la base del cuello); +Y apunta hacia abajo, -Z hacia delante, 1 unidad = 1 bloque
        getParentModel().head.translateAndRotate(pose);
        PoseStack.Pose last = pose.last();
        float px = 1f / 16f;
        float y = (HakiVisuals.EYE_ROW_FROM_TOP - 8f) * px;       // fila 4 de 8 desde arriba, hacia arriba = negativo
        float z = -(4f + HakiVisuals.EYE_FORWARD_PX) * px;        // delante de la capa exterior (llega a 4,5)
        float half = HakiVisuals.EYE_GLOW_SIZE * 0.5f;
        for (int side = -1; side <= 1; side += 2) {
            float x = side * HakiVisuals.EYE_SPACING_PX * px;
            quad(vc, last, x, y, z, half, a);
        }
        pose.popPose();
    }

    /** Cuadrado en el plano de la cara, con el color de los ojos. */
    private static void quad(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float half, float alpha) {
        int rgb = HakiVisuals.EYE_RGB;
        float r = (rgb >> 16 & 255) / 255f, g = (rgb >> 8 & 255) / 255f, b = (rgb & 255) / 255f;
        v(vc, pose, x - half, y - half, z, 0f, 0f, r, g, b, alpha);
        v(vc, pose, x - half, y + half, z, 0f, 1f, r, g, b, alpha);
        v(vc, pose, x + half, y + half, z, 1f, 1f, r, g, b, alpha);
        v(vc, pose, x + half, y - half, z, 1f, 0f, r, g, b, alpha);
    }

    private static void v(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z,
                          float u, float v, float r, float g, float b, float a) {
        vc.addVertex(pose, x, y, z)
            .setColor(r, g, b, a)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(HakiFxRender.FULL_BRIGHT)
            .setNormal(pose, 0f, 0f, -1f);
    }

    @EventBusSubscriber(modid = HakiMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Register {
        @SubscribeEvent
        public static void addLayers(EntityRenderersEvent.AddLayers event) {
            for (var skin : event.getSkins()) {
                if (event.getSkin(skin) instanceof PlayerRenderer renderer) renderer.addLayer(new HakiEyeLayer(renderer));
            }
        }
    }
}
