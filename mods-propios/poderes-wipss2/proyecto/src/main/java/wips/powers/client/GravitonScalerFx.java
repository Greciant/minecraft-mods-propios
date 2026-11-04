package wips.powers.client;

import com.devilfruits.client.vfx.fx.GroundWave;
import com.devilfruits.client.vfx.fx.Impact;
import com.devilfruits.client.vfx.fx.ShockRing;
import com.devilfruits.client.vfx.particle.VfxParticles;
import com.devilfruits.client.vfx.particle.VfxPresets;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import wips.powers.WipsPowers;
import wips.powers.fx.WipsFx;

/**
 * VFX de Graviton (presion, Chibaku Tensei y acentos de cada ranura) y de Scaler (distorsion de
 * tamano y pisadas gigantes). Geometria procedural: lineas, anillos, espirales y orbitas calculadas
 * con t = ticks + partialTick; las ondas e impactos usan el motor de VFX de Grand Line.
 */
@EventBusSubscriber(modid = WipsPowers.MOD_ID, value = Dist.CLIENT)
public final class GravitonScalerFx {
    // ---------------- paleta ----------------
    static final int VOID = 0x0A0412;
    static final int VIOLET = 0x8A3CFF;
    static final int VIOLET_DEEP = 0x4A1A8A;
    static final int VIOLET_PALE = 0xC9A6FF;
    static final int MINT = 0x5BE0A8;
    static final int MINT_PALE = 0xC8F5DF;

    // ---------------- presion (ranura 4) ----------------
    /** Lineas de presion por area, su velocidad de caida (bloques/tick) y la altura desde la que caen. */
    static final int PRESSURE_LINES = 170;
    static final float FALL_MIN = 0.45f;
    static final float FALL_MAX = 0.8f;
    static final float FALL_HEIGHT = 7.0f;
    static final float LINE_WIDTH = 0.045f;

    // ---------------- Chibaku Tensei ----------------
    static final int ROCKS = 40;
    static final int ROCK_FLIGHT = 50;
    static final int ATTRACT_LINES = 56;
    static final int SUCTION_RINGS = 3;

    private static final ResourceLocation WHITE = tex("white");
    private static final ResourceLocation STREAK = tex("streak");
    private static final ResourceLocation RING = tex("sigil_inner");

    private static ResourceLocation tex(String n) {
        return ResourceLocation.fromNamespaceAndPath(WipsPowers.MOD_ID, "textures/fx/" + n + ".png");
    }

    private record Field(long until, float radius, long seed) {}

    private static final Map<Integer, Field> PRESSURE = new ConcurrentHashMap<>();
    private static final List<Chibaku> CHIBAKU = new ArrayList<>();

    // =====================================================================
    // registro de efectos (lo llama ClientSetup)
    // =====================================================================
    public static void register() {
        WipsFx.handle("pressure", p -> {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) return;
            if (p.extra() <= 0) {
                PRESSURE.remove(p.entity());
            } else {
                boolean fresh = !PRESSURE.containsKey(p.entity());
                PRESSURE.put(p.entity(), new Field(level.getGameTime() + p.extra(), p.power(), p.seed()));
                if (fresh) {
                    // golpe inicial: el aire se aplasta contra el suelo
                    GroundWave.at(p.x(), p.y(), p.z(), p.power(), 18).strength(1.2f).ring(VIOLET_DEEP, 0.45f)
                            .dust(30, 0x2a2030, 0.4f, 0.4f, 1.6f, p.seed()).spawn();
                    ShockRing.at(p.pos().add(0, 3.5, 0), new Vec3(0, 1, 0)).radius(p.power(), 0.6f).life(14)
                            .strength(1.0f).band(0.2f).rim(0.35f, VIOLET).spawn();
                }
            }
        });
        WipsFx.handle("chibaku", p -> {
            ClientLevel level = Minecraft.getInstance().level;
            if (level != null) CHIBAKU.add(new Chibaku(p.pos(), p.seed(), level.getGameTime(), p.power()));
        });
        WipsFx.handle("chibaku_implode", GravitonScalerFx::implode);
        WipsFx.handle("chibaku_slam", GravitonScalerFx::slam);
        WipsFx.handle("cast_graviton", GravitonScalerFx::gravitonCast);
        WipsFx.handle("scaler_shift", GravitonScalerFx::scalerShift);
        WipsFx.handle("scaler_step", GravitonScalerFx::scalerStep);
    }

    // =====================================================================
    // utilidades matematicas
    // =====================================================================
    static float hash(long seed, int i, int salt) {
        long h = seed * 0x9E3779B97F4A7C15L + i * 0xC2B2AE3D27D4EB4FL + salt * 0x165667B19E3779F9L;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        return (h >>> 40) / (float) (1L << 24);
    }

    static float easeInCubic(float x) {
        return x * x * x;
    }

    static float easeOutCubic(float x) {
        float u = 1f - x;
        return 1f - u * u * u;
    }

    static float easeInOutCubic(float x) {
        return x < 0.5f ? 4f * x * x * x : 1f - (float) Math.pow(-2f * x + 2f, 3) / 2f;
    }

    static float smoothstep(float x) {
        x = Mth.clamp(x, 0f, 1f);
        return x * x * (3f - 2f * x);
    }

    /** Punto de una esfera de Fibonacci (reparto uniforme de direcciones). */
    static Vec3 fib(int i, int n) {
        double y = 1.0 - 2.0 * (i + 0.5) / n;
        double r = Math.sqrt(1.0 - y * y);
        double a = i * 2.399963229728653;
        return new Vec3(Math.cos(a) * r, y, Math.sin(a) * r);
    }

    static Vec3 rotY(Vec3 v, double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new Vec3(v.x * c - v.z * s, v.y, v.x * s + v.z * c);
    }

    // =====================================================================
    // Chibaku Tensei: estado de cliente
    // =====================================================================
    private static final class Chibaku {
        final Vec3 origin;
        final long seed;
        final long start;
        final float pullRadius;
        long implodeAt = -1;
        Vec3 implodeCore;
        Vec3 ground;
        Rock[] rocks;

        Chibaku(Vec3 origin, long seed, long start, float pullRadius) {
            this.origin = origin;
            this.seed = seed;
            this.start = start;
            this.pullRadius = pullRadius;
        }

        Vec3 core(float t) {
            if (this.implodeAt >= 0 && this.implodeCore != null) return this.implodeCore;
            float u = Mth.clamp(t / 60f, 0f, 1f);
            return this.origin.add(0, 14.0 * easeOutCubic(u), 0);
        }

        float radius(float t) {
            float grow = 0.45f + 0.85f * easeOutCubic(Mth.clamp(t / 60f, 0f, 1f));
            float pulse = 1f + 0.05f * Mth.sin(t * 0.35f);
            if (this.implodeAt >= 0) {
                float k = Mth.clamp((t - (this.implodeAt - this.start)) / 6f, 0f, 1f);
                return grow * pulse * (1f - easeInCubic(k));
            }
            return grow * pulse;
        }
    }

    private static final class Rock {
        BlockState state;
        double r0, a0, groundY;
        Vec3 shellDir;
        int startTick;
        float spinA, spinB;
    }

    private static void buildRocks(ClientLevel level, Chibaku c) {
        c.rocks = new Rock[ROCKS];
        for (int i = 0; i < ROCKS; i++) {
            Rock r = new Rock();
            r.a0 = hash(c.seed, i, 1) * Math.PI * 2;
            r.r0 = 6.0 + hash(c.seed, i, 2) * (c.pullRadius - 6.0);
            int x = Mth.floor(c.origin.x + Math.cos(r.a0) * r.r0);
            int z = Mth.floor(c.origin.z + Math.sin(r.a0) * r.r0);
            int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
            BlockState s = level.getBlockState(new BlockPos(x, top - 1, z));
            r.state = s.isAir() || !s.getFluidState().isEmpty() ? Blocks.COBBLESTONE.defaultBlockState() : s;
            r.groundY = top;
            r.shellDir = fib(i, ROCKS);
            r.startTick = 30 + i * 2;
            r.spinA = (hash(c.seed, i, 3) - 0.5f) * 30f;
            r.spinB = (hash(c.seed, i, 4) - 0.5f) * 24f;
            c.rocks[i] = r;
        }
    }

    /** Posicion de una roca: espiral desde el suelo hasta su lugar en el planetoide, luego orbita. */
    private static Vec3 rockPos(Chibaku c, Rock r, float t) {
        Vec3 core = c.core(t);
        double shell = 2.9 + hash(c.seed, (int) (r.a0 * 1000), 5) * 0.6;
        float rel = t - r.startTick;
        if (c.implodeAt >= 0) {
            float ti = (c.implodeAt - c.start);
            Vec3 at = core.add(rotY(r.shellDir, ti * 0.02).scale(shell));
            float v = Mth.clamp((t - ti) / 16f, 0f, 1f);
            double drop = Math.max(0, core.y - (c.ground != null ? c.ground.y : core.y - 14));
            return at.add(rotY(r.shellDir, ti * 0.02).multiply(1, 0, 1).scale(2.5 * v)).add(0, -drop * v * v, 0);
        }
        Vec3 target = core.add(rotY(r.shellDir, t * 0.02).scale(shell));
        float u = Mth.clamp(rel / ROCK_FLIGHT, 0f, 1f);
        float e = easeInOutCubic(u);
        double ang = r.a0 + e * Math.PI * 2 * 2.5;
        double rad = Mth.lerp(e, r.r0, shell);
        double y = Mth.lerp(e, r.groundY + 0.5, core.y) + Math.sin(Math.PI * u) * 2.0;
        Vec3 spiral = new Vec3(core.x + Math.cos(ang) * rad, y, core.z + Math.sin(ang) * rad);
        float w = smoothstep((u - 0.6f) / 0.4f);
        return spiral.scale(1 - w).add(target.scale(w));
    }

    private static void implode(WipsFx.Payload p) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        for (Chibaku c : CHIBAKU) {
            if (c.seed == p.seed()) {
                c.implodeAt = level.getGameTime();
                c.implodeCore = p.pos();
                c.ground = p.pos().add(p.dir());
            }
        }
        Vec3 core = p.pos();
        Impact.at(level, core, p.seed()).flash(2.4f).glow(VIOLET).sparks(48, 0.9f).smoke(12, 0x140818, 2.2f)
                .trauma(0.45f, 0.06f, 2.5f).play();
        ShockRing.at(core, new Vec3(0, 1, 0)).radius(10f, 0.5f).life(10).strength(1.2f).band(0.25f).rim(0.5f, VIOLET_PALE).spawn();
        ShockRing.at(core, new Vec3(0.6, 0.3, 0.7).normalize()).radius(8f, 0.4f).life(12).strength(1.0f).band(0.2f).rim(0.4f, VIOLET).spawn();
    }

    private static void slam(WipsFx.Payload p) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        Vec3 g = p.pos();
        float r = p.power();
        Impact.at(level, g, p.seed()).flash(1.6f).glow(VIOLET).wave(r, 22, VIOLET_PALE).dust(70).debris(36, 0.9f)
                .smoke(18, 0x2a2030, 2.6f).trauma(0.65f, 0.05f, 3.0f).screenFlash(0.25f).play();
        GroundWave.at(g.x, g.y, g.z, r + 3f, 26).strength(1.8f).ring(VIOLET, 0.55f)
                .dust(56, 0x6b5a50, 0.5f, 1.0f, 2.2f, p.seed()).spawn();
        ShockRing.at(g.add(0, 0.4, 0), new Vec3(0, 1, 0)).radius(1.5f, r + 4f).life(18).strength(1.4f).band(0.2f).rim(0.45f, VIOLET_PALE).spawn();
        CHIBAKU.removeIf(c -> c.seed == p.seed());
    }

    // =====================================================================
    // acentos por ranura de Graviton (1-4); la 5 es el Chibaku
    // =====================================================================
    private static void gravitonCast(WipsFx.Payload p) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        Vec3 eye = p.pos();
        Vec3 look = p.dir().lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : p.dir().normalize();
        long seed = p.seed();
        switch (p.extra()) {
            case 0 -> { // Heavy Hand: la mano colapsa el espacio delante
                Vec3 hand = eye.add(look.scale(1.2)).add(0, -0.4, 0);
                ShockRing.at(hand, look).radius(0.2f, 1.8f).life(9).strength(1.1f).band(0.3f).rim(0.45f, VIOLET).spawn();
                inwardSpiral(hand, 2.2, 18, seed, VOID);
                VfxParticles.emit(VfxPresets.GLOW, hand.x, hand.y, hand.z, 0, 0, 0).tint(VIOLET_DEEP).size(1.6f).alpha(0.6f);
            }
            case 1 -> { // Gravity Well: pozo en el suelo hacia donde mira
                Vec3 at = eye.add(look.multiply(1, 0, 1).normalize().scale(4.0));
                double gy = groundY(level, at);
                GroundWave.at(at.x, gy, at.z, 4.5f, 20).strength(1.0f).ring(VIOLET, 0.45f).dust(24, 0x2a2030, 0.3f, -0.4f, 1.4f, seed).spawn();
                ShockRing.at(new Vec3(at.x, gy + 0.2, at.z), new Vec3(0, 1, 0)).radius(5f, 0.4f).life(14).strength(1.0f).band(0.25f).rim(0.4f, VIOLET_PALE).spawn();
                inwardSpiral(new Vec3(at.x, gy + 1.0, at.z), 4.0, 26, seed, VOID);
            }
            case 2 -> { // Weightless: helice ascendente alrededor del cuerpo
                Vec3 feet = eye.add(0, -1.6, 0);
                for (int i = 0; i < 28; i++) {
                    double a = i * 0.45;
                    double h = i * 0.08;
                    VfxParticles.emit(VfxPresets.GLOW, feet.x + Math.cos(a) * 0.9, feet.y + h, feet.z + Math.sin(a) * 0.9,
                            -Math.sin(a) * 0.04, 0.06, Math.cos(a) * 0.04).tint(VIOLET_PALE).size(0.5f).alpha(0.7f).life(1.2f);
                }
                ShockRing.at(feet.add(0, 0.1, 0), new Vec3(0, 1, 0)).radius(0.4f, 2.6f).life(12).strength(0.7f).band(0.3f).rim(0.35f, VIOLET_PALE).spawn();
            }
            case 3 -> { // Dense: el suelo cede bajo los pies
                Vec3 feet = eye.add(0, -1.6, 0);
                GroundWave.at(feet.x, feet.y, feet.z, 3.0f, 14).strength(1.3f).ring(VIOLET_DEEP, 0.5f).dust(26, 0x3a3030, 0.3f, 0.5f, 1.4f, seed).spawn();
            }
            default -> {
            }
        }
    }

    /** Motas oscuras que caen en espiral hacia un punto (sensacion de succion). */
    private static void inwardSpiral(Vec3 c, double radius, int n, long seed, int rgb) {
        for (int i = 0; i < n; i++) {
            double a = hash(seed, i, 7) * Math.PI * 2;
            double y = (hash(seed, i, 8) - 0.5) * radius * 0.6;
            Vec3 at = c.add(Math.cos(a) * radius, y, Math.sin(a) * radius);
            Vec3 to = c.subtract(at).normalize();
            Vec3 tang = new Vec3(-Math.sin(a), 0, Math.cos(a));
            Vec3 v = to.scale(0.16).add(tang.scale(0.08));
            VfxParticles.emit(VfxPresets.SMOKE, at.x, at.y, at.z, v.x, v.y, v.z).tint(rgb).size(0.6f).alpha(0.75f).life(0.7f);
        }
    }

    private static double groundY(ClientLevel level, Vec3 at) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(at.x), Mth.floor(at.z));
    }

    // =====================================================================
    // Scaler
    // =====================================================================
    private static void scalerShift(WipsFx.Payload p) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        boolean grow = p.extra() == 1;
        float s = Math.max(0.3f, p.power());
        Vec3 feet = p.pos();
        float big = 2.6f * s;
        float small = 0.35f;
        // pila de anillos a lo alto del cuerpo: se abren al crecer, se cierran al encoger
        for (int k = 0; k < 4; k++) {
            double h = 0.1 + k * 0.55 * s;
            ShockRing.at(feet.add(0, h, 0), new Vec3(0, 1, 0))
                    .radius(grow ? small : big, grow ? big : small)
                    .life(10 + k * 2).strength(0.9f).band(0.28f).rim(0.4f, k % 2 == 0 ? MINT : MINT_PALE).spawn();
        }
        // helice doble de chispas que sale (crecer) o entra (encoger)
        int n = 36;
        for (int i = 0; i < n; i++) {
            double a = i * (Math.PI * 2 / 12) + (i % 2) * Math.PI;
            double h = (i / (double) n) * 2.0 * s;
            double r = 0.8 * s;
            Vec3 at = feet.add(Math.cos(a) * r, h, Math.sin(a) * r);
            Vec3 radial = new Vec3(Math.cos(a), 0, Math.sin(a));
            Vec3 v = radial.scale(grow ? 0.12 : -0.08).add(0, grow ? 0.03 : -0.01, 0);
            VfxParticles.emit(i % 3 == 0 ? VfxPresets.SPARK : VfxPresets.GLOW, at.x, at.y, at.z, v.x, v.y, v.z)
                    .tint(i % 2 == 0 ? MINT : MINT_PALE).size(0.55f).alpha(0.85f).life(0.9f);
        }
    }

    private static void scalerStep(WipsFx.Payload p) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        float s = p.power();
        int stage = p.extra();
        boolean colossus = s >= 4.5f;
        Vec3 at = p.pos();
        GroundWave.at(at.x, at.y, at.z, 2.0f * s, colossus ? 18 : 14)
                .strength(colossus ? 1.4f : 0.9f)
                .ring(MINT_PALE, colossus ? 0.4f : 0.28f)
                .dust(16 + stage * 6, 0x7a6a5a, 0.35f * s, 0.55f, 1.0f + 0.25f * s, p.seed()).spawn();
        if (colossus) {
            ShockRing.at(at.add(0, 0.3, 0), new Vec3(0, 1, 0)).radius(0.8f, 3.2f * s).life(14).strength(1.0f).band(0.18f).rim(0.3f, MINT).spawn();
        }
    }

    // =====================================================================
    // tick: limpieza y particulas continuas
    // =====================================================================
    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            PRESSURE.clear();
            CHIBAKU.clear();
            return;
        }
        if (mc.isPaused()) return;
        long now = level.getGameTime();
        Iterator<Map.Entry<Integer, Field>> it = PRESSURE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Field> en = it.next();
            Field f = en.getValue();
            Entity e = level.getEntity(en.getKey());
            if (e == null || now > f.until()) {
                it.remove();
                continue;
            }
            // polvo oscuro donde golpean las lineas
            for (int i = 0; i < 3; i++) {
                double a = level.random.nextDouble() * Math.PI * 2;
                double r = Math.sqrt(level.random.nextDouble()) * f.radius();
                VfxParticles.emit(VfxPresets.DUST, e.getX() + Math.cos(a) * r, e.getY() + 0.05, e.getZ() + Math.sin(a) * r, 0, 0.01, 0)
                        .tint(0x241a2e).size(0.6f).alpha(0.45f);
            }
            if (now % 16 == 0) {
                GroundWave.at(e.getX(), e.getY(), e.getZ(), f.radius(), 20).strength(0.6f).ring(VIOLET_DEEP, 0.35f).spawn();
            }
        }
        Iterator<Chibaku> ci = CHIBAKU.iterator();
        while (ci.hasNext()) {
            Chibaku c = ci.next();
            float t = now - c.start;
            if (t > 400) {
                ci.remove();
                continue;
            }
            if (c.rocks == null) buildRocks(level, c);
            if (c.implodeAt >= 0) continue;
            Vec3 core = c.core(t);
            float r = c.radius(t);
            VfxParticles.emit(VfxPresets.GLOW, core.x, core.y, core.z, 0, 0, 0).tint(VIOLET_DEEP).size(2.4f * r).alpha(0.5f).life(0.5f);
            if (t >= 30) {
                // motas que caen en espiral al nucleo desde todo el radio
                for (int i = 0; i < 4; i++) {
                    Vec3 d = fib(level.random.nextInt(200), 200);
                    double dist = 6 + level.random.nextDouble() * 10;
                    Vec3 at = core.add(d.scale(dist));
                    Vec3 v = d.scale(-0.35).add(rotY(d, Math.PI / 2).scale(0.1));
                    VfxParticles.emit(VfxPresets.SMOKE, at.x, at.y, at.z, v.x, v.y, v.z).tint(0x120818).size(0.7f).alpha(0.7f).life(0.8f);
                }
            }
        }
    }

    // =====================================================================
    // render
    // =====================================================================
    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (PRESSURE.isEmpty() && CHIBAKU.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        long now = level.getGameTime();
        float tNow = now + pt;
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack() != null ? event.getPoseStack() : new PoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        // ---- lineas negras de presion (mezcla normal, no aditiva: el negro tiene que verse) ----
        if (!PRESSURE.isEmpty()) {
            RenderType type = RenderType.entityTranslucentEmissive(STREAK);
            VertexConsumer vc = buffers.getBuffer(type);
            for (Map.Entry<Integer, Field> en : PRESSURE.entrySet()) {
                Entity e = level.getEntity(en.getKey());
                if (e == null) continue;
                Field f = en.getValue();
                Vec3 c = e.getPosition(pt);
                float fadeOut = Mth.clamp((f.until() - tNow) / 6f, 0f, 1f);
                // mas lineas cuanto mas grande el area (misma densidad que a 14 bloques), con tope
                int lines = Math.min(1400, (int) (PRESSURE_LINES * Math.max(1f, f.radius() * f.radius() / 196f)));
                for (int i = 0; i < lines; i++) {
                    double a = hash(f.seed(), i, 1) * Math.PI * 2;
                    double r = Math.sqrt(hash(f.seed(), i, 2)) * f.radius();
                    double x = c.x + Math.cos(a) * r;
                    double z = c.z + Math.sin(a) * r;
                    float speed = Mth.lerp(hash(f.seed(), i, 3), FALL_MIN, FALL_MAX);
                    float len = 0.7f + hash(f.seed(), i, 4) * 1.1f;
                    float drop = (tNow * speed + hash(f.seed(), i, 5) * FALL_HEIGHT) % FALL_HEIGHT;
                    double y1 = c.y + FALL_HEIGHT - drop;
                    double y0 = Math.max(c.y, y1 - len);
                    if (y1 - y0 < 0.05) continue;
                    float top = Mth.clamp(drop / 1.2f, 0f, 1f);
                    // cerca de la camara se desvanecen (si no, se ven gruesas y tapan)
                    double cd = Math.sqrt((x - cam.x) * (x - cam.x) + (z - cam.z) * (z - cam.z));
                    float near = Mth.clamp((float) (cd - 1.0) / 2.5f, 0f, 1f);
                    float alpha = 0.85f * top * fadeOut * near;
                    verticalStreak(vc, pose.last(), x - cam.x, y0 - cam.y, y1 - cam.y, z - cam.z, cam, x, z, LINE_WIDTH, 0.02f, 0.0f, 0.05f, alpha);
                }
            }
            buffers.endBatch(type);
        }

        if (CHIBAKU.isEmpty()) return;
        // ---- nucleo negro ----
        RenderType solid = RenderType.entityTranslucentEmissive(WHITE);
        VertexConsumer sv = buffers.getBuffer(solid);
        for (Chibaku c : CHIBAKU) {
            float t = tNow - c.start;
            float r = c.radius(t);
            if (r < 0.02f) continue;
            Vec3 core = c.core(t);
            pose.pushPose();
            pose.translate(core.x - cam.x, core.y - cam.y, core.z - cam.z);
            pose.mulPose(Axis.YP.rotationDegrees(t * 2f));
            sphere(sv, pose.last(), r, 0.01f, 0.0f, 0.02f, 1f);
            pose.popPose();
        }
        buffers.endBatch(solid);

        // ---- aditivos: borde violeta, anillos de succion y lineas de atraccion ----
        RenderType ringType = RenderType.eyes(RING);
        VertexConsumer rv = buffers.getBuffer(ringType);
        for (Chibaku c : CHIBAKU) {
            float t = tNow - c.start;
            float r = c.radius(t);
            if (r < 0.02f) continue;
            Vec3 core = c.core(t);
            // borde: aro de cara a la camara que late
            float pulse = 0.75f + 0.25f * Mth.sin(t * 0.5f);
            billboard(rv, pose, core.subtract(cam), event.getCamera().rotation(), r * 1.55f, VIOLET, pulse);
            if (t < 30 || c.implodeAt >= 0) continue;
            for (int k = 0; k < SUCTION_RINGS; k++) {
                float cyc = t / 26f + k / (float) SUCTION_RINGS;
                int idx = (int) Math.floor(cyc);
                float u = cyc - idx;
                float rad = Mth.lerp(easeInCubic(u), 9f, r * 1.3f);
                float alpha = Mth.sin((float) Math.PI * u) * 0.85f;
                pose.pushPose();
                pose.translate(core.x - cam.x, core.y - cam.y, core.z - cam.z);
                pose.mulPose(Axis.XP.rotationDegrees((hash(c.seed, idx * 3 + k, 9) - 0.5f) * 70f));
                pose.mulPose(Axis.ZP.rotationDegrees((hash(c.seed, idx * 3 + k, 10) - 0.5f) * 70f));
                pose.scale(rad, 1f, rad);
                flatQuad(rv, pose.last(), VIOLET, alpha);
                pose.popPose();
            }
        }
        buffers.endBatch(ringType);

        RenderType lineType = RenderType.eyes(STREAK);
        VertexConsumer lv = buffers.getBuffer(lineType);
        for (Chibaku c : CHIBAKU) {
            float t = tNow - c.start;
            if (t < 30 || c.implodeAt >= 0) continue;
            float r = c.radius(t);
            Vec3 core = c.core(t);
            for (int i = 0; i < ATTRACT_LINES; i++) {
                Vec3 d = fib(i, ATTRACT_LINES);
                d = rotY(d, hash(c.seed, i, 11) * 6.28);
                float u = ((t + hash(c.seed, i, 12) * 28f) / 28f) % 1f;
                double dist = Mth.lerp(easeInCubic(u), 16f, r * 1.2f);
                double len = 1.5 + 2.0 * (1 - u);
                Vec3 head = core.add(d.scale(dist));
                Vec3 tail = core.add(d.scale(dist + len));
                float alpha = Mth.sin((float) Math.PI * u) * 0.9f;
                ribbon(lv, pose.last(), head.subtract(cam), tail.subtract(cam), 0.06f, VIOLET_PALE, alpha);
            }
        }
        buffers.endBatch(lineType);

        // ---- rocas del suelo en espiral hacia el planetoide ----
        for (Chibaku c : CHIBAKU) {
            if (c.rocks == null) continue;
            float t = tNow - c.start;
            for (Rock rock : c.rocks) {
                if (t < rock.startTick) continue;
                if (c.implodeAt >= 0 && t - (c.implodeAt - c.start) > 16) continue;
                Vec3 p = rockPos(c, rock, t);
                pose.pushPose();
                pose.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z);
                pose.mulPose(Axis.YP.rotationDegrees(t * rock.spinA));
                pose.mulPose(Axis.XP.rotationDegrees(t * rock.spinB));
                pose.scale(0.85f, 0.85f, 0.85f);
                pose.translate(-0.5, -0.5, -0.5);
                int light = LevelRenderer.getLightColor(level, BlockPos.containing(p));
                mc.getBlockRenderer().renderSingleBlock(rock.state, pose, buffers, light, OverlayTexture.NO_OVERLAY);
                pose.popPose();
            }
        }
        buffers.endBatch();
    }

    // ---------------- geometria ----------------
    private static final int FULL = 0xF000F0;

    private static void vtx(VertexConsumer vc, PoseStack.Pose pose, double x, double y, double z, float u, float v,
                            float r, float g, float b, float a) {
        vc.addVertex(pose, (float) x, (float) y, (float) z).setColor(r, g, b, a).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL).setNormal(pose, 0f, 1f, 0f);
    }

    /** Linea vertical que siempre mira a la camara (gira solo en Y). */
    private static void verticalStreak(VertexConsumer vc, PoseStack.Pose pose, double rx, double y0, double y1, double rz,
                                       Vec3 cam, double wx, double wz, float width, float r, float g, float b, float a) {
        double dx = wx - cam.x, dz = wz - cam.z;
        double l = Math.sqrt(dx * dx + dz * dz) + 1e-6;
        double sx = -dz / l * width, sz = dx / l * width;
        vtx(vc, pose, rx - sx, y1, rz - sz, 0, 0, r, g, b, a);
        vtx(vc, pose, rx - sx, y0, rz - sz, 0, 1, r, g, b, a);
        vtx(vc, pose, rx + sx, y0, rz + sz, 1, 1, r, g, b, a);
        vtx(vc, pose, rx + sx, y1, rz + sz, 1, 0, r, g, b, a);
    }

    /** Cinta entre dos puntos de cara a la camara (aditiva: color premultiplicado por alfa). */
    private static void ribbon(VertexConsumer vc, PoseStack.Pose pose, Vec3 a, Vec3 b, float width, int rgb, float alpha) {
        Vec3 dir = b.subtract(a);
        Vec3 mid = a.add(b).scale(0.5);
        Vec3 side = dir.cross(mid).normalize().scale(width);
        float cr = (rgb >> 16 & 255) / 255f * alpha, cg = (rgb >> 8 & 255) / 255f * alpha, cb = (rgb & 255) / 255f * alpha;
        vtx(vc, pose, a.x - side.x, a.y - side.y, a.z - side.z, 0, 1, cr, cg, cb, 1);
        vtx(vc, pose, a.x + side.x, a.y + side.y, a.z + side.z, 1, 1, cr, cg, cb, 1);
        vtx(vc, pose, b.x + side.x, b.y + side.y, b.z + side.z, 1, 0, cr, cg, cb, 1);
        vtx(vc, pose, b.x - side.x, b.y - side.y, b.z - side.z, 0, 0, cr, cg, cb, 1);
    }

    /** Cuadrado unitario en XZ por ambas caras (aditivo). */
    private static void flatQuad(VertexConsumer vc, PoseStack.Pose pose, int rgb, float alpha) {
        float r = (rgb >> 16 & 255) / 255f * alpha, g = (rgb >> 8 & 255) / 255f * alpha, b = (rgb & 255) / 255f * alpha;
        vtx(vc, pose, -1, 0, -1, 0, 0, r, g, b, 1);
        vtx(vc, pose, -1, 0, 1, 0, 1, r, g, b, 1);
        vtx(vc, pose, 1, 0, 1, 1, 1, r, g, b, 1);
        vtx(vc, pose, 1, 0, -1, 1, 0, r, g, b, 1);
        vtx(vc, pose, 1, 0, -1, 1, 0, r, g, b, 1);
        vtx(vc, pose, 1, 0, 1, 1, 1, r, g, b, 1);
        vtx(vc, pose, -1, 0, 1, 0, 1, r, g, b, 1);
        vtx(vc, pose, -1, 0, -1, 0, 0, r, g, b, 1);
    }

    /** Cuadrado de cara a la camara centrado en `rel` (posicion relativa a la camara). */
    private static void billboard(VertexConsumer vc, PoseStack pose, Vec3 rel, org.joml.Quaternionf camRot, float half, int rgb, float alpha) {
        pose.pushPose();
        pose.translate(rel.x, rel.y, rel.z);
        pose.mulPose(camRot);
        pose.mulPose(Axis.XP.rotationDegrees(90f));
        pose.scale(half, 1f, half);
        flatQuad(vc, pose.last(), rgb, alpha);
        pose.popPose();
    }

    /** Esfera procedural (latitud/longitud). */
    private static void sphere(VertexConsumer vc, PoseStack.Pose pose, float radius, float r, float g, float b, float a) {
        int lat = 12, lon = 20;
        for (int i = 0; i < lat; i++) {
            double t0 = Math.PI * i / lat, t1 = Math.PI * (i + 1) / lat;
            for (int j = 0; j < lon; j++) {
                double p0 = 2 * Math.PI * j / lon, p1 = 2 * Math.PI * (j + 1) / lon;
                sv(vc, pose, radius, t0, p0, r, g, b, a);
                sv(vc, pose, radius, t1, p0, r, g, b, a);
                sv(vc, pose, radius, t1, p1, r, g, b, a);
                sv(vc, pose, radius, t0, p1, r, g, b, a);
            }
        }
    }

    private static void sv(VertexConsumer vc, PoseStack.Pose pose, float radius, double th, double ph, float r, float g, float b, float a) {
        float x = (float) (Math.sin(th) * Math.cos(ph)), y = (float) Math.cos(th), z = (float) (Math.sin(th) * Math.sin(ph));
        vc.addVertex(pose, x * radius, y * radius, z * radius).setColor(r, g, b, a).setUv(0.5f, 0.5f)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL).setNormal(pose, x, y, z);
    }
}
