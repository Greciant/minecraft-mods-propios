package wips.powers.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;
import wips.powers.WipsPowers;
import wips.powers.fx.WipsFx;

/**
 * Presentacion de cliente del despertar de un poder (AwakenBurst), con la implementacion visual de Lodestar Trail:
 * la estrella de llegada (GuideArt.star) en el suelo y cinco hadas (GuideArt.light) con estela (TrailGeometry.glow)
 * que suben en espiral alrededor del jugador y convergen arriba, con el render type GLOW de Lodestar y sus mismos
 * ajustes (intensidad, escala de efectos, colores vivos, movimiento reducido). Todo dura 3 s y desaparece solo.
 * Si Lodestar no esta (o cambio su API), queda un respaldo con particulas.
 */
public final class AwakenGlow {
    private static final int LIFE = 60;
    private static final int FAIRIES = 5;

    private record Burst(Vec3 at, int color, int accent, long start) {}

    private static final List<Burst> BURSTS = new ArrayList<>();

    // ---- Lodestar por reflexion (las clases son de paquete) ----
    private static boolean tried;
    private static Method star;
    private static Method light;
    private static Method glow;
    private static Constructor<?> point;
    private static RenderType glowType;
    private static Field intensity;
    private static Field effectScale;
    private static Field vibrant;
    private static Field reducedMotion;

    private AwakenGlow() {
    }

    public static void register() {
        WipsFx.handle("awaken_burst", p -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                return;
            }
            BURSTS.add(new Burst(p.pos(), p.extra() & 0xFFFFFF, (int) p.seed() & 0xFFFFFF, mc.level.getGameTime()));
            if (!lodestar()) {
                fallback(p.pos(), p.extra());
            }
        });
        NeoForge.EVENT_BUS.addListener(AwakenGlow::render);
    }

    private static boolean lodestar() {
        if (!tried) {
            tried = true;
            if (!ModList.get().isLoaded("lodestar_trail")) {
                return false;
            }
            try {
                ClassLoader cl = AwakenGlow.class.getClassLoader();
                Class<?> art = Class.forName("dev.lodestartrail.neoforge.GuideArt", true, cl);
                Class<?> geo = Class.forName("dev.lodestartrail.neoforge.TrailGeometry", true, cl);
                Class<?> type = Class.forName("dev.lodestartrail.neoforge.TrailRenderType", true, cl);
                Class<?> cfg = Class.forName("dev.lodestartrail.neoforge.ClientConfig", true, cl);
                Class<?> pt = Class.forName("dev.lodestartrail.common.Point", true, cl);
                star = art.getDeclaredMethod("star", VertexConsumer.class, Matrix4f.class, pt, Vec3.class, Vec3.class, Vec3.class,
                        double.class, double.class, int.class, double.class);
                light = art.getDeclaredMethod("light", VertexConsumer.class, Matrix4f.class, pt, Vec3.class, Vec3.class, Vec3.class,
                        double.class, int.class, double.class);
                glow = geo.getDeclaredMethod("glow", VertexConsumer.class, Matrix4f.class, pt, Vec3.class, Vec3.class, Vec3.class,
                        double.class, int.class, double.class);
                point = pt.getDeclaredConstructor(double.class, double.class, double.class);
                Field g = type.getDeclaredField("GLOW");
                intensity = cfg.getDeclaredField("INTENSITY");
                effectScale = cfg.getDeclaredField("EFFECT_SCALE");
                vibrant = cfg.getDeclaredField("VIBRANT");
                reducedMotion = cfg.getDeclaredField("REDUCED_MOTION");
                for (var a : new java.lang.reflect.AccessibleObject[]{star, light, glow, point, g, intensity, effectScale, vibrant, reducedMotion}) {
                    a.setAccessible(true);
                }
                glowType = (RenderType) g.get(null);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                WipsPowers.LOGGER.warn("[wipspowers] Lodestar no disponible para el despertar, uso particulas: {}", e.toString());
                star = null;
            }
        }
        return star != null && glowType != null;
    }

    private static double cfg(Field f, double def) {
        try {
            Object v = ((java.util.function.Supplier<?>) f.get(null)).get();
            return v instanceof Number n ? n.doubleValue() : v instanceof Boolean b ? (b ? 1 : 0) : def;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return def;
        }
    }

    private static Object pt(Vec3 v) throws ReflectiveOperationException {
        return point.newInstance(v.x, v.y, v.z);
    }

    private static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || BURSTS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            BURSTS.clear();
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        long now = mc.level.getGameTime();
        Iterator<Burst> it = BURSTS.iterator();
        while (it.hasNext()) {
            if (now - it.next().start() > LIFE) {
                it.remove(); // desaparece solo al terminar
            }
        }
        if (BURSTS.isEmpty() || !lodestar()) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        Vec3 left = new Vec3(event.getCamera().getLeftVector());
        Vec3 up = new Vec3(event.getCamera().getUpVector());
        Matrix4f matrix = event.getPoseStack().last().pose();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer out = buffers.getBuffer(glowType);
        double alphaScale = cfg(intensity, 1.0);
        double size = cfg(effectScale, 1.0) * (cfg(vibrant, 0) > 0 ? 1.4 : 1.0);
        boolean reduced = cfg(reducedMotion, 0) > 0;
        Vec3 axisX = new Vec3(1, 0, 0);
        Vec3 axisZ = new Vec3(0, 0, 1);
        try {
            for (Burst b : BURSTS) {
                double age = now - b.start() + partial;
                double p = Math.min(1.0, age / LIFE);
                double fade = Math.sin(Math.PI * p) * alphaScale;
                Vec3 base = b.at().add(0, 0.12, 0);
                // estrella de llegada en el suelo, que se abre y gira; una segunda con el acento, a contramano
                star.invoke(null, out, matrix, pt(base), camera, axisX, axisZ, (0.3 + 2.8 * Math.sin(p * Math.PI * 0.7)) * size,
                        reduced ? 0.0 : age * 0.04, b.color(), fade);
                star.invoke(null, out, matrix, pt(base.add(0, 0.02, 0)), camera, axisX, axisZ, (0.2 + 1.6 * Math.sin(p * Math.PI * 0.8)) * size,
                        reduced ? 0.0 : -age * 0.06, b.accent(), fade * 0.8);
                if (reduced) {
                    continue;
                }
                // hadas: espiral ascendente que se cierra y converge en la cima
                for (int i = 0; i < FAIRIES; i++) {
                    int color = (i % 2 == 0) ? b.color() : b.accent();
                    for (int k = 0; k <= 8; k++) {
                        double a2 = Math.max(0, age - k * 0.8);
                        Vec3 at = fairy(b.at(), i, a2);
                        if (k == 0) {
                            light.invoke(null, out, matrix, pt(at), camera, left, up, 0.9 * size, color, fade);
                        } else {
                            double life = 1.0 - k / 9.0;
                            glow.invoke(null, out, matrix, pt(at), camera, left, up, 0.06 * size * life, color, fade * life * 0.8);
                        }
                    }
                }
                if (p > 0.72) {
                    double q = (p - 0.72) / 0.28;
                    Vec3 apex = b.at().add(0, 3.6, 0);
                    star.invoke(null, out, matrix, pt(apex), camera, left, up, (0.4 + 1.8 * q) * size, age * 0.05, b.accent(),
                            Math.sin(Math.PI * q) * alphaScale);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            WipsPowers.LOGGER.warn("[wipspowers] fallo el dibujo de Lodestar del despertar: {}", e.toString());
            star = null;
        }
        buffers.endBatch(glowType);
    }

    private static Vec3 fairy(Vec3 c, int i, double age) {
        double p = Math.min(1.0, age / LIFE);
        double ang = i * (Math.PI * 2 / FAIRIES) + age * 0.16;
        double conv = p < 0.72 ? 1.0 : 1.0 - (p - 0.72) / 0.28; // al final convergen al centro
        double rad = (0.6 + 1.5 * Math.sin(Math.min(1.0, p * 1.6) * Math.PI * 0.5)) * conv;
        double y = 0.3 + 3.3 * Math.pow(p, 0.8) + Math.sin(age * 0.3 + i) * 0.12;
        return c.add(Math.cos(ang) * rad, y, Math.sin(ang) * rad);
    }

    /** Sin Lodestar: espiral de particulas del lado del cliente (complementa las del servidor). */
    private static void fallback(Vec3 c, int color) {
        Minecraft mc = Minecraft.getInstance();
        for (int i = 0; i < 40; i++) {
            double a = i * 0.45;
            double y = i * 0.09;
            mc.level.addParticle(ParticleTypes.END_ROD, c.x + Math.cos(a) * 1.4, c.y + y, c.z + Math.sin(a) * 1.4, 0, 0.02, 0);
        }
    }
}
