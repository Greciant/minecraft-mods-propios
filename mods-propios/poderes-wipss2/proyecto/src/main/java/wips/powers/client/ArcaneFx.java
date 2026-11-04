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
import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
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
import wips.powers.WipsPowers;
import wips.powers.fx.WipsFx;

/**
 * VFX de Summoning, Kinetic Charger, Velocity, Ruin, Phytokinetic y Gamma. Mismo sistema que
 * ElementalFx: geometria procedural (circulos que giran, pilares, lineas convergentes, conos,
 * ondas a ras de suelo, helices) sobre el motor de Grand Line.
 */
@EventBusSubscriber(modid = WipsPowers.MOD_ID, value = Dist.CLIENT)
public final class ArcaneFx {
    // ---------------- paletas (color tema original de cada poder en Awakened) ----------------
    static final int SUMMON = 0x38E1C8;
    static final int SUMMON_PALE = 0xBFF7EE;
    static final int KINETIC = 0xC77DFF;
    static final int KINETIC_PALE = 0xEBD6FF;
    static final int SPEED = 0xE8323C;
    static final int SPEED_PALE = 0xFFC2C5;
    static final int RUIN = 0x2A0E14;
    static final int RUIN_GLOW = 0xC9C4BE;
    static final int PHYTO = 0x58C24A;
    static final int PHYTO_PALE = 0xCFF5A8;
    static final int GAMMA = 0x76E03A;
    static final int GAMMA_PALE = 0xD4F7BC;

    private static final ResourceLocation RUNES = ResourceLocation.fromNamespaceAndPath(WipsPowers.MOD_ID, "textures/fx/summon_runes.png");

    private record Timed(int life, IntConsumer step, int[] age) {}

    /** Circulo runico en el suelo (solo Summoning). */
    private record Circle(Vec3 at, float radius, long start, int life, int rgb) {}

    private static final List<Timed> TIMED = new ArrayList<>();
    private static final List<Circle> CIRCLES = new ArrayList<>();

    private static void run(int life, IntConsumer step) {
        TIMED.add(new Timed(life, step, new int[]{0}));
    }

    public static void register() {
        WipsFx.handle("cast_summoning", ArcaneFx::summoningCast);
        WipsFx.handle("active_summoning", p -> motes(p.pos().add(0, -0.6, 0), SUMMON_PALE, 2));
        WipsFx.handle("cast_kinetic_charger", ArcaneFx::kineticCast);
        WipsFx.handle("active_kinetic_charger", ArcaneFx::kineticActive);
        WipsFx.handle("cast_velocity", ArcaneFx::velocityCast);
        WipsFx.handle("active_velocity", ArcaneFx::velocityActive);
        WipsFx.handle("cast_ruin", ArcaneFx::ruinCast);
        WipsFx.handle("active_ruin", ArcaneFx::ruinActive);
        WipsFx.handle("cast_phytokinetic", ArcaneFx::phytoCast);
        WipsFx.handle("active_phytokinetic", p -> spores(p.pos().add(0, -0.8, 0), 3));
        WipsFx.handle("cast_gamma", ArcaneFx::gammaCast);
        WipsFx.handle("active_gamma", ArcaneFx::gammaActive);
    }

    private static ClientLevel level() {
        return Minecraft.getInstance().level;
    }

    private static Vec3 look(WipsFx.Payload p) {
        Vec3 d = p.dir();
        return d.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : d.normalize();
    }

    private static Vec3 feet(WipsFx.Payload p) {
        return p.pos().add(0, -1.55, 0);
    }

    private static void motes(Vec3 c, int rgb, int n) {
        ClientLevel level = level();
        for (int i = 0; i < n; i++) {
            double a = level.random.nextDouble() * Math.PI * 2;
            Vec3 q = c.add(Math.cos(a) * 0.8, level.random.nextDouble() * 1.2 - 0.4, Math.sin(a) * 0.8);
            VfxParticles.emit(VfxPresets.GLOW, q.x, q.y, q.z, 0, 0.04, 0).tint(rgb).size(0.4f).alpha(0.8f).life(0.8f);
        }
    }

    // =====================================================================
    // SUMMONING: circulos runicos que giran, pilares de invocacion y brechas dimensionales
    // =====================================================================
    private static void summoningCast(WipsFx.Payload p) {
        ClientLevel level = level();
        if (level == null) return;
        Vec3 feet = feet(p);
        Vec3 look = look(p);
        switch (p.extra()) {
            case 0, 3 -> { // Summon / Mass Raise: circulo + pilar(es)
                boolean mass = p.extra() == 3;
                Vec3 at = mass ? feet : feet.add(new Vec3(look.x, 0, look.z).normalize().scale(2.5));
                float r = mass ? 5.0f : 2.2f;
                CIRCLES.add(new Circle(at.add(0, 0.05, 0), r, level.getGameTime(), mass ? 60 : 45, SUMMON));
                int pillars = mass ? 5 : 1;
                for (int k = 0; k < pillars; k++) {
                    double a = k * (Math.PI * 2 / pillars);
                    Vec3 base = mass ? at.add(Math.cos(a) * 3.5, 0, Math.sin(a) * 3.5) : at;
                    run(26, age -> {
                        double h = Math.min(age, 14) * 0.3;
                        for (int j = 0; j < 3; j++) {
                            double b = age * 0.7 + j * (Math.PI * 2 / 3);
                            Vec3 q = base.add(Math.cos(b) * 0.45, h - j * 0.3, Math.sin(b) * 0.45);
                            VfxParticles.emit(VfxPresets.GLOW, q.x, q.y, q.z, 0, 0.18, 0).tint(j == 0 ? SUMMON_PALE : SUMMON).size(0.5f).alpha(0.85f).life(0.9f);
                        }
                        if (age % 3 == 0) level.addParticle(ParticleTypes.REVERSE_PORTAL, base.x, base.y + 0.2, base.z, 0, 0.3, 0);
                    });
                }
                GroundWave.at(at.x, at.y, at.z, r + 1, 18).strength(0.9f).ring(SUMMON, 0.45f).spawn();
            }
            case 1 -> { // Recall: los aliados vuelven por una brecha
                breach(p.pos().add(look.scale(1.5)), look);
            }
            case 4 -> { // Possess: brecha en la mira
                breach(p.pos().add(look.scale(3.0)), look);
            }
            default -> ShockRing.at(feet.add(0, 0.1, 0), new Vec3(0, 1, 0)).radius(0.4f, 3f).life(12).strength(0.8f).band(0.25f).rim(0.4f, SUMMON).spawn();
        }
    }

    /** Brecha dimensional: anillo vertical que se abre y particulas que entran a otra dimension. */
    private static void breach(Vec3 c, Vec3 n) {
        ClientLevel level = level();
        ShockRing.at(c, n).radius(0.2f, 1.6f).life(14).strength(1.0f).band(0.35f).rim(0.5f, SUMMON_PALE).spawn();
        ShockRing.at(c, n).radius(1.6f, 0.2f).life(18).strength(0.7f).band(0.2f).rim(0.35f, SUMMON).spawn();
        for (int i = 0; i < 30; i++) {
            level.addParticle(ParticleTypes.REVERSE_PORTAL, c.x + (level.random.nextDouble() - 0.5) * 2, c.y + (level.random.nextDouble() - 0.5) * 2,
                    c.z + (level.random.nextDouble() - 0.5) * 2, 0, 0, 0);
        }
    }

    // =====================================================================
    // KINETIC CHARGER: lineas que convergen al cargar, pulsos de sobrecarga, ondas al liberar
    // =====================================================================
    private static void kineticCast(WipsFx.Payload p) {
        ClientLevel level = level();
        if (level == null) return;
        Vec3 eye = p.pos();
        Vec3 look = look(p);
        Vec3 front = eye.add(look.scale(1.2)).add(0, -0.3, 0);
        boolean full = p.extra() == 4;
        float k = full ? 2.2f : 1f;
        ShockRing.at(front, look).radius(0.3f, 2.4f * k).life(9).move(look.scale(0.5)).strength(1.2f).band(0.3f).rim(0.5f, KINETIC_PALE).spawn();
        ShockRing.at(front, look).radius(0.5f, 3.6f * k).life(13).move(look.scale(0.8)).strength(0.7f).band(0.18f).rim(0.3f, KINETIC).spawn();
        Impact.at(level, front, p.seed()).flash(0.5f * k).glow(KINETIC).sparks((int) (14 * k), 0.7f).trauma(0.15f * k, 0.08f, 1.5f).play();
        if (full) {
            Vec3 feet = feet(p);
            GroundWave.at(feet.x, feet.y, feet.z, 7f, 18).strength(1.4f).ring(KINETIC, 0.5f).dust(36, 0x6a5a40, 0.4f, 0.9f, 1.6f, p.seed()).spawn();
        }
    }

    private static void kineticActive(WipsFx.Payload p) {
        ClientLevel level = level();
        if (level == null) return;
        Vec3 chest = p.pos().add(0, -0.5, 0);
        // lineas de energia que convergen hacia el usuario
        for (int i = 0; i < 6; i++) {
            Vec3 d = GravitonScalerFx.fib(level.random.nextInt(120), 120);
            double dist = 2.5 + level.random.nextDouble() * 1.5;
            Vec3 q = chest.add(d.scale(dist));
            Vec3 v = d.scale(-0.32);
            VfxParticles.emit(VfxPresets.SPARK, q.x, q.y, q.z, v.x, v.y, v.z).tint(i % 2 == 0 ? KINETIC : KINETIC_PALE).size(0.5f).life(0.5f);
        }
        if (level.getGameTime() % 9 == 0) { // pulso de sobrecarga
            ShockRing.at(chest, new Vec3(0, 1, 0)).radius(1.6f, 0.3f).life(8).strength(0.8f).band(0.3f).rim(0.4f, KINETIC).spawn();
        }
    }

    // =====================================================================
    // VELOCITY: speed lines densas, conos de ruptura sonica, distorsion de viento
    // =====================================================================
    private static void velocityCast(WipsFx.Payload p) {
        ClientLevel level = level();
        if (level == null) return;
        Vec3 eye = p.pos();
        Vec3 look = look(p);
        sonicCone(eye.add(look.scale(1.0)).add(0, -0.4, 0), look);
        if (p.extra() == 4) { // Speed Tunnel: tunel de lineas a lo largo de la mirada
            run(16, age -> {
                for (int i = 0; i < 10; i++) {
                    double a = level.random.nextDouble() * Math.PI * 2;
                    Vec3[] b = ElementalFx.basis(look);
                    Vec3 q = eye.add(look.scale(2 + level.random.nextDouble() * 14)).add(b[0].scale(Math.cos(a) * 1.6)).add(b[1].scale(Math.sin(a) * 1.6));
                    VfxParticles.emit(VfxPresets.SPEED_LINE, q.x, q.y, q.z, look.x * 0.9, look.y * 0.9, look.z * 0.9).tint(SPEED_PALE).size(0.9f).alpha(0.8f);
                }
            });
        }
    }

    private static void sonicCone(Vec3 c, Vec3 n) {
        ShockRing.at(c, n).radius(0.2f, 1.6f).life(7).move(n.scale(0.6)).strength(1.1f).band(0.3f).rim(0.5f, SPEED_PALE).spawn();
        ShockRing.at(c.add(n.scale(0.4)), n).radius(0.4f, 2.6f).life(9).move(n.scale(0.9)).strength(0.8f).band(0.2f).rim(0.35f, SPEED).spawn();
        ShockRing.at(c.add(n.scale(0.8)), n).radius(0.6f, 3.6f).life(11).move(n.scale(1.2)).strength(0.5f).band(0.15f).rim(0.25f, SPEED).spawn();
    }

    private static void velocityActive(WipsFx.Payload p) {
        ClientLevel level = level();
        if (level == null) return;
        Entity e = level.getEntity(p.entity());
        if (e == null) return;
        Vec3 v = e.getDeltaMovement();
        Vec3 dir = new Vec3(v.x, 0, v.z);
        if (dir.lengthSqr() < 0.01) dir = new Vec3(look(p).x, 0, look(p).z);
        dir = dir.normalize();
        Vec3 c = e.position().add(0, 0.9, 0);
        Vec3[] b = ElementalFx.basis(dir);
        for (int i = 0; i < 8; i++) { // estela densa de lineas detras
            double a = level.random.nextDouble() * Math.PI * 2;
            double r = 0.3 + level.random.nextDouble() * 0.6;
            Vec3 q = c.add(b[0].scale(Math.cos(a) * r)).add(b[1].scale(Math.sin(a) * r)).add(dir.scale(-0.5 - level.random.nextDouble()));
            VfxParticles.emit(VfxPresets.SPEED_LINE, q.x, q.y, q.z, -dir.x * 0.6, -dir.y * 0.6, -dir.z * 0.6).tint(i % 3 == 0 ? SPEED : SPEED_PALE).size(0.8f).alpha(0.75f);
        }
        if (v.horizontalDistanceSqr() > 0.25 && level.getGameTime() % 6 == 0) {
            sonicCone(c.add(dir.scale(0.8)), dir);
        }
        if (level.random.nextInt(2) == 0) {
            level.addParticle(ParticleTypes.CLOUD, c.x - dir.x, c.y - 0.6, c.z - dir.z, -dir.x * 0.1, 0.02, -dir.z * 0.1);
        }
    }

    // =====================================================================
    // RUIN: desintegracion y ceniza oscura, ondas de corrupcion a ras de suelo, fracturas de energia
    // =====================================================================
    private static void ruinCast(WipsFx.Payload p) {
        ClientLevel level = level();
        if (level == null) return;
        Vec3 eye = p.pos();
        Vec3 look = look(p);
        Vec3 feet = feet(p);
        switch (p.extra()) {
            case 1, 4 -> { // Spread / Collapse: onda de corrupcion que avanza a ras de suelo
                float r = p.extra() == 4 ? 9f : 6f;
                GroundWave.at(feet.x, feet.y, feet.z, r, 22).strength(1.2f).ring(RUIN_GLOW, 0.5f).dust(20, 0x1a1012, 0.3f, 0.7f, 1.1f, p.seed()).spawn();
                run(18, age -> {
                    double d = 1 + age * r / 18.0;
                    for (int i = 0; i < 10; i++) {
                        double a = i * (Math.PI * 2 / 10) + age * 0.05;
                        Vec3 q = feet.add(Math.cos(a) * d, 0.1, Math.sin(a) * d);
                        VfxParticles.emit(VfxPresets.SMOKE, q.x, q.y, q.z, 0, 0.02, 0).tint(RUIN).size(0.5f).alpha(0.4f).life(0.6f);
                        if (i % 3 == 0) VfxParticles.emit(VfxPresets.CRACKS_VIOLET, q.x, q.y + 0.02, q.z, 0, 0, 0).tint(RUIN_GLOW).size(1.1f);
                    }
                });
            }
            default -> { // Grip / Rush / Breach: fractura de energia hacia donde mira y ceniza
                Vec3 front = eye.add(look.scale(1.5)).add(0, -0.3, 0);
                ShockRing.at(front, look).radius(0.2f, 1.8f).life(10).strength(1.0f).band(0.25f).rim(0.45f, RUIN_GLOW).spawn();
                ash(front, 20, 1.0);
                for (int i = 0; i < 10; i++) {
                    Vec3 q = front.add(look.scale(i * 0.4)).add((level.random.nextDouble() - 0.5) * 0.4 * i * 0.2, (level.random.nextDouble() - 0.5) * 0.3, (level.random.nextDouble() - 0.5) * 0.4 * i * 0.2);
                    VfxParticles.emit(VfxPresets.CRACKS_VIOLET, q.x, q.y, q.z, 0, 0, 0).tint(RUIN_GLOW).size(0.7f);
                }
            }
        }
    }

    private static void ruinActive(WipsFx.Payload p) {
        ash(p.pos().add(0, -0.6, 0), 4, 0.8);
    }

    /** Ceniza oscura que se desintegra hacia arriba. */
    private static void ash(Vec3 c, int n, double spread) {
        ClientLevel level = level();
        for (int i = 0; i < n; i++) {
            Vec3 q = c.add((level.random.nextDouble() - 0.5) * spread * 2, (level.random.nextDouble() - 0.5) * spread, (level.random.nextDouble() - 0.5) * spread * 2);
            VfxParticles.emit(VfxPresets.SOOT, q.x, q.y, q.z, (level.random.nextDouble() - 0.5) * 0.03, 0.05, (level.random.nextDouble() - 0.5) * 0.03).tint(RUIN).size(0.5f).alpha(0.85f).life(1.2f);
            if (i % 4 == 0) level.addParticle(ParticleTypes.ASH, q.x, q.y, q.z, 0, 0, 0);
        }
    }

    // =====================================================================
    // PHYTOKINETIC: esporas, espirales de hojas y raices emergiendo, particulas organicas envolventes
    // =====================================================================
    private static void phytoCast(WipsFx.Payload p) {
        ClientLevel level = level();
        if (level == null) return;
        Vec3 eye = p.pos();
        Vec3 look = look(p);
        Vec3 feet = feet(p);
        Vec3 at = p.extra() == 1 || p.extra() == 4 ? feet : ElementalFx.aim(level, eye, look, 16);
        float r = p.extra() == 4 ? 4.5f : 2.0f;
        GroundWave.at(at.x, at.y, at.z, r + 1f, 18).strength(0.9f).ring(PHYTO, 0.4f).dust(20, 0x4a3a20, 0.3f, 0.4f, 1.2f, p.seed()).spawn();
        // raices: espirales que emergen del suelo y se enroscan hacia arriba
        int roots = p.extra() == 4 ? 6 : 3;
        for (int k = 0; k < roots; k++) {
            double a0 = k * (Math.PI * 2 / roots);
            Vec3 base = at.add(Math.cos(a0) * r * 0.6, 0, Math.sin(a0) * r * 0.6);
            run(22, age -> {
                double h = age * 0.12;
                double a = a0 + age * 0.45;
                Vec3 q = base.add(Math.cos(a) * 0.35, h, Math.sin(a) * 0.35);
                VfxParticles.emit(VfxPresets.GLOW, q.x, q.y, q.z, 0, 0.01, 0).tint(age % 2 == 0 ? PHYTO : PHYTO_PALE).size(0.45f).alpha(0.85f).life(1.0f);
                if (age % 4 == 0) level.addParticle(ParticleTypes.SPORE_BLOSSOM_AIR, q.x, q.y, q.z, 0, 0, 0);
            });
        }
        // hojas en espiral envolvente
        for (int i = 0; i < 18; i++) {
            double a = i * 0.7;
            Vec3 q = feet.add(Math.cos(a) * 1.0, 0.2 + i * 0.09, Math.sin(a) * 1.0);
            level.addParticle(ParticleTypes.CHERRY_LEAVES, q.x, q.y, q.z, -Math.sin(a) * 0.05, 0.02, Math.cos(a) * 0.05);
        }
        spores(feet.add(0, 1, 0), 10);
    }

    private static void spores(Vec3 c, int n) {
        ClientLevel level = level();
        if (level == null) return;
        for (int i = 0; i < n; i++) {
            level.addParticle(ParticleTypes.SPORE_BLOSSOM_AIR, c.x + (level.random.nextDouble() - 0.5) * 2, c.y + (level.random.nextDouble() - 0.5) * 1.5,
                    c.z + (level.random.nextDouble() - 0.5) * 2, 0, 0, 0);
        }
    }

    // =====================================================================
    // GAMMA: haces de radiacion, pulsos verde neon expansivos, ionizacion y distorsion
    // =====================================================================
    private static void gammaCast(WipsFx.Payload p) {
        ClientLevel level = level();
        if (level == null) return;
        Vec3 eye = p.pos();
        Vec3 look = look(p);
        Vec3 feet = feet(p);
        // pulso expansivo neon a la altura del pecho
        ShockRing.at(eye.add(0, -0.6, 0), new Vec3(0, 1, 0)).radius(0.4f, 4.5f).life(12).strength(1.1f).band(0.25f).rim(0.5f, GAMMA).spawn();
        ShockRing.at(eye.add(0, -0.6, 0), new Vec3(0, 1, 0)).radius(0.6f, 7f).life(18).strength(0.6f).band(0.15f).rim(0.3f, GAMMA_PALE).spawn();
        ionize(eye.add(0, -0.6, 0), 18, 1.4);
        switch (p.extra()) {
            case 0 -> { // Provoke: haz de radiacion concentrada hacia donde mira
                for (int i = 0; i < 26; i++) {
                    Vec3 q = eye.add(look.scale(1 + i * 0.5)).add(0, -0.3, 0);
                    VfxParticles.emit(VfxPresets.GLOW, q.x, q.y, q.z, look.x * 0.05, 0, look.z * 0.05).tint(i % 2 == 0 ? GAMMA : GAMMA_PALE).size(0.6f - i * 0.01f).alpha(0.85f).life(0.6f);
                }
            }
            case 2, 4 -> GroundWave.at(feet.x, feet.y, feet.z, 6f, 18).strength(1.3f).ring(GAMMA, 0.45f).dust(30, 0x3a4a30, 0.4f, 0.8f, 1.5f, p.seed()).spawn();
            default -> {
            }
        }
    }

    private static void gammaActive(WipsFx.Payload p) {
        ionize(p.pos().add(0, -0.6, 0), 3, 1.0);
    }

    /** Particulas de ionizacion: chispas neon que orbitan y se escapan. */
    private static void ionize(Vec3 c, int n, double r) {
        ClientLevel level = level();
        if (level == null) return;
        for (int i = 0; i < n; i++) {
            double a = level.random.nextDouble() * Math.PI * 2;
            double y = (level.random.nextDouble() - 0.5) * 1.6;
            Vec3 q = c.add(Math.cos(a) * r, y, Math.sin(a) * r);
            VfxParticles.emit(VfxPresets.SPARK, q.x, q.y, q.z, -Math.sin(a) * 0.1, 0.03, Math.cos(a) * 0.1).tint(i % 2 == 0 ? GAMMA : GAMMA_PALE).size(0.45f).life(0.7f);
        }
    }

    // =====================================================================
    // tick y render
    // =====================================================================
    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            TIMED.clear();
            CIRCLES.clear();
            return;
        }
        if (mc.isPaused()) return;
        List<Timed> live = new ArrayList<>(TIMED);
        TIMED.clear();
        for (Timed t : live) {
            t.step().accept(t.age()[0]);
            if (++t.age()[0] < t.life()) TIMED.add(t);
        }
        long now = mc.level.getGameTime();
        CIRCLES.removeIf(c -> now - c.start() > c.life());
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || CIRCLES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float now = mc.level.getGameTime() + pt;
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack() != null ? event.getPoseStack() : new PoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType type = RenderType.eyes(RUNES);
        VertexConsumer vc = buffers.getBuffer(type);
        for (Circle c : CIRCLES) {
            float t = now - c.start();
            float appear = Mth.clamp(t / 8f, 0f, 1f);
            float scale = c.radius() * (1f - (1f - appear) * (1f - appear) * (1f - appear));
            float fade = Mth.clamp((c.life() - t) / 12f, 0f, 1f);
            float alpha = 0.9f * fade * (0.85f + 0.15f * Mth.sin(t * 0.4f));
            for (int layer = 0; layer < 2; layer++) {
                pose.pushPose();
                pose.translate(c.at().x - cam.x, c.at().y - cam.y + layer * 0.02, c.at().z - cam.z);
                pose.mulPose(Axis.YP.rotationDegrees((layer == 0 ? 1 : -1.6f) * t * 3f));
                float k = layer == 0 ? scale : scale * 0.55f;
                pose.scale(k, 1f, k);
                quad(vc, pose.last(), layer == 0 ? c.rgb() : SUMMON_PALE, alpha * (layer == 0 ? 1f : 0.8f));
                pose.popPose();
            }
        }
        buffers.endBatch(type);
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose pose, int rgb, float alpha) {
        float r = (rgb >> 16 & 255) / 255f * alpha, g = (rgb >> 8 & 255) / 255f * alpha, b = (rgb & 255) / 255f * alpha;
        float[][] v = {{-1, -1, 0, 0}, {-1, 1, 0, 1}, {1, 1, 1, 1}, {1, -1, 1, 0}};
        for (int side = 0; side < 2; side++) {
            for (int i = 0; i < 4; i++) {
                float[] q = v[side == 0 ? i : 3 - i];
                vc.addVertex(pose, q[0], 0f, q[1]).setColor(r, g, b, 1f).setUv(q[2], q[3]).setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(0xF000F0).setNormal(pose, 0f, side == 0 ? 1f : -1f, 0f);
            }
        }
    }
}
