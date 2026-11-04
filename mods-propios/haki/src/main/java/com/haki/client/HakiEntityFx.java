package com.haki.client;

import com.haki.HakiMod;
import com.haki.HakiVisuals;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

/** Esquirlas negras con borde rojo alrededor de las entidades paralizadas. */
@EventBusSubscriber(modid = HakiMod.MODID, value = Dist.CLIENT)
public final class HakiEntityFx {
    private HakiEntityFx() {}

    private static final RandomSource RNG = RandomSource.create();
    private static final float[] PTS = new float[(HakiVisuals.SHARD_SEGMENTS + 1) * 3];

    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Post<?, ?> event) {
        if (HakiClientFx.AFFECTED.isEmpty()) return;
        LivingEntity entity = event.getEntity();
        long left = HakiClientFx.stunTicksLeft(entity.getId());
        if (left <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        float pt = event.getPartialTick();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 pos = entity.getPosition(pt);
        double max = HakiVisuals.SHARD_RENDER_DISTANCE;
        if (pos.distanceToSqr(cam) > max * max) return;

        float alpha = HakiFxRender.smooth((left - pt) / HakiVisuals.SHARD_FADE_TICKS);
        if (alpha <= 0.001f) return;
        // El PoseStack esta en los pies de la entidad: la camara en esas coordenadas es cam - pos
        float cx = (float) (cam.x - pos.x), cy = (float) (cam.y - pos.y), cz = (float) (cam.z - pos.z);
        VertexConsumer vc = event.getMultiBufferSource()
            .getBuffer(RenderType.entityTranslucentEmissive(HakiFxRender.BOLT_TEX));
        var pose = event.getPoseStack().last();

        int segs = HakiVisuals.SHARD_SEGMENTS;
        float time = mc.level.getGameTime() + pt;
        float w = entity.getBbWidth(), h = entity.getBbHeight();
        int life = HakiVisuals.SHARD_LIFE_TICKS;
        for (int i = 0; i < HakiVisuals.SHARD_COUNT; i++) {
            // Cada esquirla tiene su propia fase: crece desde el suelo, se desvanece y reaparece en otro sitio
            RNG.setSeed(entity.getId() * 4987142L + i * 1301L);
            float u = time + RNG.nextFloat() * life;
            int cycle = Mth.floor(u / life);
            float p = (u - cycle * life) / life;
            RNG.setSeed(entity.getId() * 4987142L + i * 1301L + cycle * 5947611L);
            double ang = (i + RNG.nextFloat() * 0.6) * Math.PI * 2 / HakiVisuals.SHARD_COUNT;
            float r = w * 0.5f + 0.12f + RNG.nextFloat() * 0.18f;
            float bx = (float) Math.cos(ang) * r, bz = (float) Math.sin(ang) * r;
            float top = h * (0.85f + RNG.nextFloat() * 0.35f) * HakiFxRender.easeOutCubic(p / 0.45f);
            float a = alpha * HakiFxRender.pulseAlpha(p);
            if (top < 0.05f || a <= 0.01f) continue;
            for (int k = 0; k <= segs; k++) {
                float j = k == 0 ? 0 : (RNG.nextFloat() - 0.5f) * 2f * HakiVisuals.SHARD_JITTER;
                float j2 = k == 0 ? 0 : (RNG.nextFloat() - 0.5f) * 2f * HakiVisuals.SHARD_JITTER;
                PTS[k * 3] = bx + j;
                PTS[k * 3 + 1] = -0.05f + top * k / segs;
                PTS[k * 3 + 2] = bz + j2;
            }
            HakiFxRender.ribbon(vc, pose, PTS, segs, HakiVisuals.SHARD_WIDTH, HakiVisuals.SHARD_WIDTH * 0.35f,
                cx, cy, cz, a, RNG.nextFloat());
        }
    }
}
