package wips.powers.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import wips.powers.CastSigil;
import wips.powers.WipsPowers;

/**
 * Sello de energia bajo los pies (y un halo giroscopico al pecho) mientras dura cada tecnica.
 * Todo el movimiento se calcula con el tiempo continuo t = ticks + partialTick, asi que es fluido
 * a cualquier FPS. Los valores de "AJUSTES" regulan velocidad e intensidad.
 */
@EventBusSubscriber(modid = WipsPowers.MOD_ID, value = Dist.CLIENT)
public final class SigilRenderer {
    // ===================== AJUSTES =====================
    /** Interruptor general: false = no se dibuja ningun sello, anillo, halo ni estela (pedido 2026-10-31). */
    static final boolean ENABLED = false;
    /** Aparicion: ticks que tarda el "pop" y cuanto rebota (0 = sin rebote, 1.7 = rebote marcado). */
    static final float APPEAR_TICKS = 9f;
    static final float APPEAR_OVERSHOOT = 1.7f;
    /** Ticks de desvanecido al terminar (curva smoothstep). */
    static final float FADE_TICKS = 12f;
    /** Giro principal en Y: grados por tick, y cuantas veces mas rapido gira al final de una carga. */
    static final float SPIN_Y = 3.0f;
    static final float SPIN_RAMP = 2.5f;
    static final float RAMP_TICKS = 30f;
    /** Oscilacion "flotando en un campo de energia": grados y velocidad (rad/tick) en X y Z. */
    static final float WOBBLE_X = 6f;
    static final float WOBBLE_X_SPEED = 0.11f;
    static final float WOBBLE_Z = 4.5f;
    static final float WOBBLE_Z_SPEED = 0.17f;
    /** Respiracion: amplitud de escala, velocidad y estiramiento opuesto entre X y Z. */
    static final float BREATH_AMP = 0.045f;
    static final float BREATH_SPEED = 0.21f;
    static final float STRETCH = 0.035f;
    /** Latido doble (lub-dub): cada cuantos ticks, cuanto crece y cuanto brilla en cada golpe. */
    static final float BEAT_PERIOD = 22f;
    static final float BEAT_AMP = 0.07f;
    static final float BEAT_GLOW = 0.25f;
    /** Flotacion vertical del sello del suelo. */
    static final float HOVER_AMP = 0.05f;
    static final float HOVER_SPEED = 0.13f;
    /** Al soltar la tecnica: duracion y fuerza del estallido hacia afuera. */
    static final float RELEASE_TICKS = 8f;
    static final float RELEASE_PUNCH = 0.45f;
    /** Intensidad maxima del brillo del sello (es aditivo: 1 = a tope). */
    static final float ALPHA = 0.9f;
    /** Halo del pecho: inclinacion, precesion (grados/tick) y tamano relativo. */
    static final float HALO_TILT = 68f;
    static final float HALO_PRECESS = 4.5f;
    static final float HALO_SCALE = 0.42f;
    /** Particulas por tick que deja el borde del sello (estela en espiral). */
    static final int SPARKS_PER_TICK = 3;
    static final double MAX_DIST = 64.0;
    // ===================================================

    private static final ResourceLocation OUTER = ResourceLocation.fromNamespaceAndPath(WipsPowers.MOD_ID, "textures/fx/sigil_outer.png");
    private static final ResourceLocation INNER = ResourceLocation.fromNamespaceAndPath(WipsPowers.MOD_ID, "textures/fx/sigil_inner.png");
    private static final int FULL_BRIGHT = 0xF000F0;

    private record Style(int outer, int inner, ParticleOptions particle, float radius) {}

    private static Style style(String ability) {
        return switch (ability) {
            case "seiryuu_thunder_bagua" -> new Style(0x9B5CFF, 0xD9C4FF, ParticleTypes.ELECTRIC_SPARK, 2.2f);
            case "seiryuu_flame_cloud" -> new Style(0xFF6A0A, 0xFFB45A, ParticleTypes.FLAME, 1.6f);
            case "seiryuu_draconic_thunder" -> new Style(0x7A3CFF, 0xC8A8FF, ParticleTypes.ELECTRIC_SPARK, 2.6f);
            case "seiryuu_bolo_barrage" -> new Style(0xFF3A08, 0xFF9A3A, ParticleTypes.FLAME, 2.0f);
            case "seiryuu_blast_breath" -> new Style(0xFFA21E, 0xFFE08A, ParticleTypes.FLAME, 1.8f);
            case "gamma_island_shaker" -> new Style(0x7FC4FF, 0xD8F0FF, ParticleTypes.END_ROD, 3.0f);
            default -> new Style(0xFFFFFF, 0xFFFFFF, ParticleTypes.END_ROD, 2.0f);
        };
    }

    // ---------- easing ----------
    static float easeOutBack(float x) {
        float c3 = APPEAR_OVERSHOOT + 1f;
        float u = x - 1f;
        return 1f + c3 * u * u * u + APPEAR_OVERSHOOT * u * u;
    }

    static float easeOutCubic(float x) {
        float u = 1f - x;
        return 1f - u * u * u;
    }

    static float smoothstep(float x) {
        return x * x * (3f - 2f * x);
    }

    /** Dos golpes gaussianos por periodo: un latido organico en vez de un seno. */
    static float beat(float t) {
        float x = t % BEAT_PERIOD;
        float a = x;
        float b = x - 4.5f;
        return (float) (Math.exp(-(a * a) / 1.28) + 0.55 * Math.exp(-(b * b) / 1.28));
    }

    /** Angulo del giro en Y integrado: velocidad que acelera de forma cubica durante la carga. */
    static float spin(float t) {
        float r = Math.min(t, RAMP_TICKS);
        float extra = r * r * r * r / (4f * RAMP_TICKS * RAMP_TICKS * RAMP_TICKS) + Math.max(0f, t - RAMP_TICKS);
        return SPIN_Y * (t + SPIN_RAMP * extra);
    }

    /** Fotograma del sello en el instante t (ticks continuos). */
    private record Frame(float scale, float alpha, float angle, float sx, float sz, float tiltX, float tiltZ, float hover, float breath) {}

    private static Frame frame(CastSigil.State s, Style st, long now, float pt) {
        float t = (now - s.start) + pt;
        float remain = (s.end - now) - pt;
        float appear = Mth.clamp(t / APPEAR_TICKS, 0f, 1f);
        float fadeIn = easeOutCubic(Mth.clamp(t / 5f, 0f, 1f));
        float fade = smoothstep(Mth.clamp(remain / FADE_TICKS, 0f, 1f));
        float rel = s.releasedAt == Long.MIN_VALUE ? 0f : Mth.clamp(((now - s.releasedAt) + pt) / RELEASE_TICKS, 0f, 1f);
        float b = beat(t);
        float scale = st.radius() * easeOutBack(appear) * (1f + RELEASE_PUNCH * easeOutCubic(rel)) * (1f + BEAT_AMP * b);
        float alpha = Mth.clamp(ALPHA * fadeIn * fade * (1f + BEAT_GLOW * b), 0f, 1f);
        float breath = 1f + BREATH_AMP * Mth.sin(t * BREATH_SPEED);
        float stretch = STRETCH * Mth.sin(t * BREATH_SPEED * 0.5f + 0.7f);
        return new Frame(scale, alpha, spin(t), breath * (1f + stretch), breath * (1f - stretch),
                WOBBLE_X * Mth.sin(t * WOBBLE_X_SPEED), WOBBLE_Z * Mth.sin(t * WOBBLE_Z_SPEED + 1.3f),
                HOVER_AMP * Mth.sin(t * HOVER_SPEED), breath);
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (!ENABLED) {
            CastSigil.CLIENT.clear();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            CastSigil.CLIENT.clear();
            return;
        }
        if (CastSigil.CLIENT.isEmpty() || mc.isPaused()) {
            return;
        }
        long now = level.getGameTime();
        Iterator<Map.Entry<Integer, CastSigil.State>> it = CastSigil.CLIENT.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, CastSigil.State> en = it.next();
            CastSigil.State s = en.getValue();
            s.resolve(now);
            Entity e = level.getEntity(en.getKey());
            if (e == null || now > s.end + 2) {
                it.remove();
                continue;
            }
            Style st = style(s.ability);
            Frame f = frame(s, st, now, 0f);
            if (f.alpha() < 0.05f) {
                continue;
            }
            // estela: chispas en el borde que giran con el sello y suben en espiral
            float r = f.scale() * 0.92f;
            for (int i = 0; i < SPARKS_PER_TICK; i++) {
                double a = Math.toRadians(-f.angle()) + i * (Math.PI * 2 / SPARKS_PER_TICK) + level.random.nextDouble() * 0.4;
                double x = e.getX() + Math.cos(a) * r;
                double z = e.getZ() + Math.sin(a) * r;
                double tx = -Math.sin(a) * 0.04;
                double tz = Math.cos(a) * 0.04;
                level.addParticle(st.particle(), x, e.getY() + 0.15, z, tx, 0.03 + level.random.nextDouble() * 0.04, tz);
            }
        }
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (!ENABLED || event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || CastSigil.CLIENT.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        long now = level.getGameTime();
        Vec3 cam = event.getCamera().getPosition();
        // En 21.1 la rotacion de camara ya esta en el modelview: aqui solo trasladamos
        PoseStack pose = event.getPoseStack() != null ? event.getPoseStack() : new PoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        for (int pass = 0; pass < 2; pass++) {
            // mezcla aditiva (RenderType.eyes): brilla sobre cualquier fondo e ignora la luz del mundo
            RenderType type = RenderType.eyes(pass == 0 ? OUTER : INNER);
            VertexConsumer vc = buffers.getBuffer(type);
            for (Map.Entry<Integer, CastSigil.State> en : CastSigil.CLIENT.entrySet()) {
                CastSigil.State s = en.getValue();
                if (s.start == Long.MIN_VALUE || s.end == Long.MIN_VALUE) {
                    continue;
                }
                Entity e = level.getEntity(en.getKey());
                if (e == null || e.distanceToSqr(cam) > MAX_DIST * MAX_DIST) {
                    continue;
                }
                Style st = style(s.ability);
                Frame f = frame(s, st, now, pt);
                if (f.alpha() < 0.01f) {
                    continue;
                }
                Vec3 p = e.getPosition(pt);
                double x = p.x - cam.x;
                double y = p.y - cam.y + 0.06 + f.hover();
                double z = p.z - cam.z;
                if (pass == 0) {
                    // sello del suelo: giro en Y + oscilacion en X/Z + respiracion no uniforme
                    pose.pushPose();
                    pose.translate(x, y, z);
                    pose.mulPose(Axis.XP.rotationDegrees(f.tiltX()));
                    pose.mulPose(Axis.ZP.rotationDegrees(f.tiltZ()));
                    pose.mulPose(Axis.YP.rotationDegrees(f.angle()));
                    pose.scale(f.scale() * f.sx(), 1f, f.scale() * f.sz());
                    quad(vc, pose.last(), st.outer(), f.alpha());
                    pose.popPose();
                } else {
                    // anillo interior a contragiro, un poco mas alto
                    pose.pushPose();
                    pose.translate(x, y + 0.03, z);
                    pose.mulPose(Axis.XP.rotationDegrees(f.tiltX() * 1.4f));
                    pose.mulPose(Axis.ZP.rotationDegrees(f.tiltZ() * 1.4f));
                    pose.mulPose(Axis.YP.rotationDegrees(-f.angle() * 1.6f));
                    float k = f.scale() * 0.55f * f.breath();
                    pose.scale(k, 1f, k);
                    quad(vc, pose.last(), st.inner(), f.alpha() * 0.9f);
                    pose.popPose();
                    // halo giroscopico al pecho (no en primera persona propia: taparia la vista)
                    boolean ownFirstPerson = e == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
                    if (!ownFirstPerson) {
                        float t = (now - s.start) + pt;
                        pose.pushPose();
                        pose.translate(x, p.y - cam.y + e.getBbHeight() * 0.55, z);
                        pose.mulPose(Axis.YP.rotationDegrees(t * HALO_PRECESS));
                        pose.mulPose(Axis.XP.rotationDegrees(HALO_TILT + 6f * Mth.sin(t * 0.09f)));
                        pose.mulPose(Axis.YP.rotationDegrees(f.angle() * 1.3f));
                        float h = f.scale() * HALO_SCALE * f.breath();
                        pose.scale(h, 1f, h);
                        quad(vc, pose.last(), st.inner(), f.alpha() * 0.75f);
                        pose.popPose();
                    }
                }
            }
            buffers.endBatch(type);
        }
    }

    /** Cuadrado unitario en el plano XZ, por ambas caras, color multiplicado y luz completa. */
    private static void quad(VertexConsumer vc, PoseStack.Pose pose, int rgb, float alpha) {
        // aditivo: la opacidad va multiplicada en el color (texturas premultiplicadas)
        float r = (rgb >> 16 & 255) / 255f * alpha;
        float g = (rgb >> 8 & 255) / 255f * alpha;
        float b = (rgb & 255) / 255f * alpha;
        v(vc, pose, -1, -1, 0, 0, r, g, b, alpha, 1);
        v(vc, pose, -1, 1, 0, 1, r, g, b, alpha, 1);
        v(vc, pose, 1, 1, 1, 1, r, g, b, alpha, 1);
        v(vc, pose, 1, -1, 1, 0, r, g, b, alpha, 1);
        v(vc, pose, 1, -1, 1, 0, r, g, b, alpha, -1);
        v(vc, pose, 1, 1, 1, 1, r, g, b, alpha, -1);
        v(vc, pose, -1, 1, 0, 1, r, g, b, alpha, -1);
        v(vc, pose, -1, -1, 0, 0, r, g, b, alpha, -1);
    }

    private static void v(VertexConsumer vc, PoseStack.Pose pose, float x, float z, float u, float w,
                          float r, float g, float b, float a, float ny) {
        vc.addVertex(pose, x, 0f, z)
                .setColor(r, g, b, a)
                .setUv(u, w)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT)
                .setNormal(pose, 0f, ny, 0f);
    }
}
