package wips.powers.client;

import com.awakenedmod.awakened.content.AwakenedEntities;
import com.devilfruits.client.vfx.fx.ArcBurst;
import com.devilfruits.client.vfx.fx.BoltStrike;
import com.devilfruits.client.vfx.fx.GroundWave;
import com.devilfruits.client.vfx.fx.Impact;
import com.devilfruits.client.vfx.fx.ShockRing;
import com.devilfruits.client.vfx.particle.VfxParticles;
import com.devilfruits.client.vfx.particle.VfxPresets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import wips.powers.WipsPowers;
import wips.powers.fx.WipsFx;

/**
 * VFX de Pyrokinetic, Hydrokinetic y Storm Caller. Todo es geometria procedural (helices, anillos,
 * curvas, frentes de onda) compuesta con el motor de Grand Line; cada poder tiene paleta e identidad
 * propia. Los efectos que duran varios ticks son {@link Timed} y se avanzan en el tick del cliente.
 */
@EventBusSubscriber(modid = WipsPowers.MOD_ID, value = Dist.CLIENT)
public final class ElementalFx {
    // ---------------- paletas ----------------
    static final int FIRE = 0xFF7A2B;
    static final int EMBER = 0xFFB04A;
    static final int HOT = 0xFFE2A0;
    static final int WATER = 0x2E8BFF;
    static final int FOAM = 0xCFEFFF;
    static final int DEEP = 0x1B4FA8;
    static final int STORM = 0x8FB4FF;
    static final int STORM_PALE = 0xDDE8FF;
    static final int CLOUD = 0x3A4258;

    /** Efecto que dura varios ticks: recibe la edad (0..life-1). */
    private record Timed(int life, java.util.function.IntConsumer step, int[] age) {}

    private static final List<Timed> TIMED = new ArrayList<>();

    private static void run(int life, java.util.function.IntConsumer step) {
        TIMED.add(new Timed(life, step, new int[]{0}));
    }

    public static void register() {
        WipsFx.handle("pyro_burst", ElementalFx::pyroBurst);
        WipsFx.handle("cast_pyrokinetic", ElementalFx::pyroCast);
        WipsFx.handle("active_pyrokinetic", ElementalFx::pyroActive);
        WipsFx.handle("cast_hydrokinetic", ElementalFx::hydroCast);
        WipsFx.handle("active_hydrokinetic", ElementalFx::hydroActive);
        WipsFx.handle("storm_strike", ElementalFx::stormStrike);
        WipsFx.handle("cast_storm_caller", ElementalFx::stormCast);
        WipsFx.handle("active_storm_caller", ElementalFx::stormActive);
    }

    // =====================================================================
    // utilidades
    // =====================================================================
    private static ClientLevel level() {
        return Minecraft.getInstance().level;
    }

    /** Base ortonormal (u, w) perpendicular a n. */
    static Vec3[] basis(Vec3 n) {
        Vec3 ref = Math.abs(n.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = n.cross(ref).normalize();
        Vec3 w = n.cross(u).normalize();
        return new Vec3[]{u, w};
    }

    static Vec3 aim(ClientLevel level, Vec3 eye, Vec3 look, double range) {
        Vec3 to = eye.add(look.scale(range));
        HitResult hit = level.clip(new ClipContext(eye, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS ? to : hit.getLocation();
    }

    static Vec3 hand(Vec3 eye, Vec3 look) {
        Vec3 flat = new Vec3(look.x, 0, look.z);
        Vec3 side = flat.lengthSqr() < 1e-4 ? Vec3.ZERO : new Vec3(-flat.z, 0, flat.x).normalize();
        return eye.add(look.scale(0.9)).add(side.scale(-0.35)).add(0, -0.35, 0);
    }

    static Vec3 look(WipsFx.Payload p) {
        Vec3 d = p.dir();
        return d.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : d.normalize();
    }

    // =====================================================================
    // PYROKINETIC: espirales de fuego, brasas ascendentes, anillos de ignicion y ondas de calor
    // =====================================================================
    private static void pyroBurst(WipsFx.Payload p) {
        ClientLevel level = level();
        if (level == null) return;
        Vec3 c = p.pos();
        Vec3 n = look(p);
        float r = Math.max(0.5f, p.power());
        ShockRing.at(c, n).radius(0.3f, r * 1.8f + 1f).life(10).strength(1.1f).band(0.3f).rim(0.5f, HOT).spawn();
        ShockRing.at(c.add(n.scale(0.1)), n).radius(0.5f, r * 2.6f + 2f).life(16).strength(0.6f).band(0.15f).rim(0.3f, FIRE).spawn();
        if (n.y > 0.6) {
            GroundWave.at(c.x, c.y, c.z, r * 2f + 1.5f, 14).strength(0.9f).ring(FIRE, 0.35f)
                    .dust((int) (10 + r * 4), 0x3a2a20, 0.3f, 0.5f, 1.0f, p.seed()).spawn();
        }
        Impact.at(level, c, p.seed()).flash(0.6f + 0.2f * r).glow(FIRE).embers(16).sparks(10, 0.5f).play();
        // brasas que suben en espiral
        for (int i = 0; i < 24; i++) {
            double a = i * 0.52;
            double rad = 0.3 + i * 0.04 * r;
            Vec3 at = c.add(Math.cos(a) * rad, 0.2, Math.sin(a) * rad);
            VfxParticles.emit(VfxPresets.EMBER, at.x, at.y, at.z, -Math.sin(a) * 0.05, 0.08 + (i % 4) * 0.02, Math.cos(a) * 0.05)
                    .tint(i % 3 == 0 ? HOT : EMBER).size(0.6f).life(1.3f);
        }
        // onda de calor: resplandor ancho y suave
        VfxParticles.emit(VfxPresets.GLOW, c.x, c.y + 0.3, c.z, 0, 0.02, 0).tint(EMBER).size(r * 3f).alpha(0.35f).life(0.6f);
    }

    private static void pyroCast(WipsFx.Payload p) {
        ClientLevel level = level();
        if (level == null) return;
        Vec3 eye = p.pos();
        Vec3 look = look(p);
        Vec3 hand = hand(eye, look);
        switch (p.extra()) {
            case 0 -> ShockRing.at(hand, look).radius(0.15f, 1.3f).life(8).strength(1.0f).band(0.35f).rim(0.45f, HOT).spawn();
            case 4 -> { // Backdraft: el fuego se traga hacia el usuario en espiral y luego estalla
                Vec3 feet = eye.add(0, -1.5, 0);
                for (int i = 0; i < 40; i++) {
                    double a = i * 0.45;
                    double rad = 4.0 - (i % 10) * 0.15;
                    Vec3 at = feet.add(Math.cos(a) * rad, 0.3 + (i % 5) * 0.35, Math.sin(a) * rad);
                    Vec3 in = feet.add(0, 1, 0).subtract(at).normalize();
                    Vec3 v = in.scale(0.22).add(-Math.sin(a) * 0.12, 0, Math.cos(a) * 0.12);
                    VfxParticles.emit(VfxPresets.FIRE_TONGUE, at.x, at.y, at.z, v.x, v.y, v.z).tint(FIRE).size(0.7f).life(0.8f);
                }
                GroundWave.at(feet.x, feet.y, feet.z, 4.5f, 16).strength(1.0f).ring(FIRE, 0.4f).spawn();
            }
            default -> {
            }
        }
    }

    private static void pyroActive(WipsFx.Payload p) {
        ClientLevel level = level();
        if (level == null) return;
        Vec3 eye = p.pos();
        Vec3 look = look(p);
        Vec3 hand = hand(eye, look);
        Vec3 feet = eye.add(0, -1.55, 0);
        switch (p.extra()) {
            case 1 -> { // Flame Beam: anillos de calor que viajan por el rayo
                ShockRing.at(hand, look).radius(0.2f, 1.1f).life(7).move(look.scale(0.7)).strength(0.8f).band(0.3f).rim(0.35f, HOT).spawn();
                emberBurst(hand, look, 4);
            }
            case 2 -> { // Fire Jets: aros de propulsion bajo los pies
                ShockRing.at(feet, new Vec3(0, 1, 0)).radius(0.3f, 1.6f).life(7).move(new Vec3(0, -0.15, 0)).strength(0.8f).band(0.3f).rim(0.4f, FIRE).spawn();
                for (int i = 0; i < 4; i++) {
                    double a = level.random.nextDouble() * Math.PI * 2;
                    VfxParticles.emit(VfxPresets.FIRE_TONGUE, feet.x + Math.cos(a) * 0.3, feet.y, feet.z + Math.sin(a) * 0.3,
                            Math.cos(a) * 0.05, -0.25, Math.sin(a) * 0.05).tint(FIRE).size(0.6f).life(0.6f);
                }
            }
            case 3 -> ShockRing.at(hand, look).radius(0.15f, 0.8f).life(5).strength(0.7f).band(0.35f).rim(0.35f, EMBER).spawn();
            default -> emberBurst(feet.add(0, 1, 0), new Vec3(0, 1, 0), 3);
        }
    }

    private static void emberBurst(Vec3 at, Vec3 dir, int n) {
        ClientLevel level = level();
        for (int i = 0; i < n; i++) {
            Vec3 v = dir.scale(0.08).add((level.random.nextDouble() - 0.5) * 0.08, 0.05, (level.random.nextDouble() - 0.5) * 0.08);
            VfxParticles.emit(VfxPresets.EMBER, at.x, at.y, at.z, v.x, v.y, v.z).tint(EMBER).size(0.5f).life(1.1f);
        }
    }

    /** Estela en espiral de cada bola de fuego (sin paquetes: se lee del propio proyectil). */
    private static void pyroProjectiles(ClientLevel level) {
        EntityType<?> type = AwakenedEntities.PYRO_FIREBALL.get();
        for (Entity e : level.entitiesForRendering()) {
            if (e.getType() != type) continue;
            Vec3 v = e.getDeltaMovement();
            if (v.lengthSqr() < 1e-3) continue;
            Vec3 n = v.normalize();
            Vec3[] b = basis(n);
            double ph = e.tickCount * 0.9 + e.getId();
            Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
            for (int k = 0; k < 2; k++) {
                double a = ph + k * Math.PI;
                Vec3 at = c.add(b[0].scale(Math.cos(a) * 0.45)).add(b[1].scale(Math.sin(a) * 0.45));
                VfxParticles.emit(VfxPresets.FIRE_TONGUE, at.x, at.y, at.z, -n.x * 0.05, -n.y * 0.05, -n.z * 0.05)
                        .tint(k == 0 ? FIRE : EMBER).size(0.45f).life(0.6f);
            }
            VfxParticles.emit(VfxPresets.EMBER, c.x, c.y, c.z, (level.random.nextDouble() - 0.5) * 0.04, 0.05, (level.random.nextDouble() - 0.5) * 0.04)
                    .tint(EMBER).size(0.45f).life(1.0f);
        }
    }

    // =====================================================================
    // HYDROKINETIC: latigos/olas fluidas, vortices, rocio y burbujas
    // =====================================================================
    private static void hydroCast(WipsFx.Payload p) {
        ClientLevel level = level();
        if (level == null) return;
        Vec3 eye = p.pos();
        Vec3 look = look(p);
        Vec3 hand = hand(eye, look);
        long seed = p.seed();
        switch (p.extra()) {
            case 0 -> { // Water Lance: vortice en la mano
                ShockRing.at(hand, look).radius(0.2f, 1.4f).life(9).strength(0.9f).band(0.3f).rim(0.45f, FOAM).spawn();
                spiralSpray(hand, look, 0.5, 18);
            }
            case 1 -> { // Geyser: columna helicoidal donde apunta
                Vec3 at = aim(level, eye, look, 30);
                GroundWave.at(at.x, at.y, at.z, 3.2f, 16).strength(1.0f).ring(WATER, 0.45f).dust(18, 0x7a8a9a, 0.3f, 0.4f, 1.0f, seed).spawn();
                ShockRing.at(at.add(0, 0.2, 0), new Vec3(0, 1, 0)).radius(0.4f, 3.5f).life(12).strength(1.0f).band(0.25f).rim(0.45f, FOAM).spawn();
                run(28, age -> {
                    for (int k = 0; k < 3; k++) {
                        double a = age * 0.6 + k * (Math.PI * 2 / 3);
                        double h = age * 0.28;
                        double rad = 0.6 + 0.15 * Math.sin(age * 0.3);
                        Vec3 q = at.add(Math.cos(a) * rad, h, Math.sin(a) * rad);
                        VfxParticles.emit(VfxPresets.GLOW, q.x, q.y, q.z, 0, 0.12, 0).tint(k == 0 ? FOAM : WATER).size(0.6f).alpha(0.8f).life(0.9f);
                        level.addParticle(ParticleTypes.SPLASH, q.x, q.y, q.z, Math.cos(a) * 0.1, 0.2, Math.sin(a) * 0.1);
                    }
                    if (age % 4 == 0) level.addParticle(ParticleTypes.BUBBLE_POP, at.x, at.y + age * 0.28, at.z, 0, 0.05, 0);
                });
            }
            case 3 -> { // Tide Hook: latigo curvo de la mano al objetivo
                Vec3 end = aim(level, eye, look, 20);
                Vec3 ctrl = hand.add(end).scale(0.5).add(0, 2.5, 0);
                for (int i = 0; i <= 28; i++) {
                    double t = i / 28.0;
                    Vec3 q = hand.scale((1 - t) * (1 - t)).add(ctrl.scale(2 * t * (1 - t))).add(end.scale(t * t));
                    VfxParticles.emit(VfxPresets.GLOW, q.x, q.y, q.z, 0, 0, 0).tint(t < 0.5 ? WATER : FOAM).size(0.55f).alpha(0.85f).life(0.6f + (float) t * 0.4f);
                    if (i % 3 == 0) level.addParticle(ParticleTypes.SPLASH, q.x, q.y, q.z, 0, 0.1, 0);
                }
                ShockRing.at(end, look).radius(0.2f, 1.6f).life(10).strength(0.9f).band(0.3f).rim(0.45f, FOAM).spawn();
            }
            case 4 -> { // Tsunami: frente de ola curvado que avanza
                Vec3 flat = new Vec3(look.x, 0, look.z);
                Vec3 fwd = flat.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : flat.normalize();
                Vec3 side = new Vec3(-fwd.z, 0, fwd.x);
                Vec3 base = eye.add(0, -1.5, 0);
                GroundWave.at(base.x, base.y, base.z, 4f, 16).strength(1.2f).ring(WATER, 0.5f).spawn();
                run(32, age -> {
                    double d = 2.0 + age * 0.65;
                    for (int i = -10; i <= 10; i++) {
                        double s = i / 10.0;
                        double curve = -s * s * 2.0; // las alas quedan atras: frente curvo
                        double h = 2.6 * (1.0 - s * s * 0.5) * Math.min(1, age / 6.0);
                        Vec3 foot = base.add(fwd.scale(d + curve)).add(side.scale(s * 7.0));
                        double y = h * (0.5 + 0.5 * level.random.nextDouble());
                        Vec3 q = foot.add(0, y, 0);
                        VfxParticles.emit(VfxPresets.GLOW, q.x, q.y, q.z, fwd.x * 0.2, 0.02, fwd.z * 0.2)
                                .tint(y > h * 0.75 ? FOAM : WATER).size(0.9f).alpha(0.7f).life(0.7f);
                        if ((i + age) % 4 == 0) level.addParticle(ParticleTypes.SPLASH, q.x, foot.y + h, q.z, fwd.x * 0.3, 0.25, fwd.z * 0.3);
                    }
                });
            }
            default -> {
            }
        }
    }

    private static void hydroActive(WipsFx.Payload p) {
        ClientLevel level = level();
        if (level == null) return;
        Vec3 eye = p.pos();
        Vec3 look = look(p);
        Vec3 feet = eye.add(0, -1.55, 0);
        switch (p.extra()) {
            case 2 -> { // Wave Rider: aros de espuma bajo los pies y rocio hacia atras
                ShockRing.at(feet.add(0, 0.1, 0), new Vec3(0, 1, 0)).radius(0.4f, 2.2f).life(8).strength(0.8f).band(0.3f).rim(0.4f, FOAM).spawn();
                for (int i = 0; i < 4; i++) {
                    level.addParticle(ParticleTypes.SPLASH, feet.x, feet.y + 0.2, feet.z, -look.x * 0.3 + (level.random.nextDouble() - 0.5) * 0.2, 0.25, -look.z * 0.3 + (level.random.nextDouble() - 0.5) * 0.2);
                }
            }
            default -> { // vortice de agua alrededor del pecho mientras se mantiene
                Vec3 chest = eye.add(0, -0.6, 0);
                double a0 = level.getGameTime() * 0.5;
                for (int k = 0; k < 3; k++) {
                    double a = a0 + k * (Math.PI * 2 / 3);
                    Vec3 q = chest.add(Math.cos(a) * 0.9, Math.sin(a0 * 0.5 + k) * 0.3, Math.sin(a) * 0.9);
                    VfxParticles.emit(VfxPresets.GLOW, q.x, q.y, q.z, -Math.sin(a) * 0.08, 0, Math.cos(a) * 0.08).tint(k == 0 ? FOAM : WATER).size(0.45f).alpha(0.8f).life(0.6f);
                }
                if (level.random.nextInt(3) == 0) level.addParticle(ParticleTypes.BUBBLE_POP, chest.x, chest.y, chest.z, 0, 0.03, 0);
            }
        }
    }

    private static void spiralSpray(Vec3 c, Vec3 n, double radius, int count) {
        ClientLevel level = level();
        Vec3[] b = basis(n);
        for (int i = 0; i < count; i++) {
            double a = i * (Math.PI * 2 / count);
            Vec3 o = b[0].scale(Math.cos(a) * radius).add(b[1].scale(Math.sin(a) * radius));
            Vec3 tang = b[0].scale(-Math.sin(a)).add(b[1].scale(Math.cos(a)));
            Vec3 v = tang.scale(0.12).add(n.scale(0.1));
            Vec3 q = c.add(o);
            VfxParticles.emit(VfxPresets.GLOW, q.x, q.y, q.z, v.x, v.y, v.z).tint(i % 2 == 0 ? FOAM : WATER).size(0.45f).alpha(0.85f).life(0.7f);
            if (i % 3 == 0) level.addParticle(ParticleTypes.SPLASH, q.x, q.y, q.z, v.x, v.y + 0.1, v.z);
        }
    }

    /** Estela helicoidal de las lanzas de agua. */
    private static void hydroProjectiles(ClientLevel level) {
        EntityType<?> type = AwakenedEntities.HYDRO_LANCE.get();
        for (Entity e : level.entitiesForRendering()) {
            if (e.getType() != type) continue;
            Vec3 v = e.getDeltaMovement();
            if (v.lengthSqr() < 1e-3) continue;
            Vec3 n = v.normalize();
            Vec3[] b = basis(n);
            double ph = e.tickCount * 1.1 + e.getId();
            Vec3 c = e.position();
            for (int k = 0; k < 2; k++) {
                double a = ph + k * Math.PI;
                Vec3 at = c.add(b[0].scale(Math.cos(a) * 0.35)).add(b[1].scale(Math.sin(a) * 0.35));
                VfxParticles.emit(VfxPresets.GLOW, at.x, at.y, at.z, -n.x * 0.04, -n.y * 0.04, -n.z * 0.04).tint(k == 0 ? FOAM : WATER).size(0.4f).alpha(0.8f).life(0.5f);
            }
            level.addParticle(ParticleTypes.SPLASH, c.x, c.y, c.z, 0, 0.05, 0);
        }
    }

    // =====================================================================
    // STORM CALLER: arcos ramificados, chispas estaticas y vortices de tormenta
    // =====================================================================
    private static void stormStrike(WipsFx.Payload p) {
        ClientLevel level = level();
        if (level == null) return;
        Vec3 g = p.pos();
        float r = Math.max(1f, p.power());
        long seed = p.seed();
        double jx = ((seed & 0xFF) / 255.0 - 0.5) * 6.0;
        double jz = (((seed >> 8) & 0xFF) / 255.0 - 0.5) * 6.0;
        BoltStrike.between(g.add(jx, 28, jz), g, seed).width(0.32f).forks(5).rough(0.65f).life(9).heat(1.3f).endGlow(1.6f).spawn();
        BoltStrike.between(g.add(-jx * 0.6, 24, -jz * 0.6), g, seed ^ 0x5DEECE66DL).width(0.16f).forks(2).rough(0.7f).life(6).heat(1.1f).spawn();
        ArcBurst.at(g.add(0, 0.3, 0), seed).arcs(7).radius(0.4f, 2.6f * r / 2.5f).life(8).spin(0.3f).spawn();
        GroundWave.at(g.x, g.y, g.z, r * 2f + 2f, 14).strength(1.0f).ring(STORM_PALE, 0.45f).dust(18, 0x4a4a55, 0.3f, 0.6f, 1.0f, seed).spawn();
        ShockRing.at(g.add(0, 0.2, 0), new Vec3(0, 1, 0)).radius(0.5f, r * 2.5f + 2f).life(12).strength(1.0f).band(0.2f).rim(0.4f, STORM).spawn();
        // chispas estaticas que siguen chisporroteando un rato
        run(20, age -> {
            for (int i = 0; i < 3; i++) {
                double a = level.random.nextDouble() * Math.PI * 2;
                double d = level.random.nextDouble() * r * 1.5;
                VfxParticles.emit(VfxPresets.THUNDER_SPARK, g.x + Math.cos(a) * d, g.y + 0.1 + level.random.nextDouble() * 0.6, g.z + Math.sin(a) * d,
                        (level.random.nextDouble() - 0.5) * 0.2, 0.1, (level.random.nextDouble() - 0.5) * 0.2);
            }
        });
    }

    private static void stormCast(WipsFx.Payload p) {
        ClientLevel level = level();
        if (level == null) return;
        Vec3 eye = p.pos();
        Vec3 look = look(p);
        Vec3 hand = hand(eye, look);
        Entity caster = level.getEntity(p.entity());
        switch (p.extra()) {
            case 0 -> ArcBurst.at(hand, p.seed()).follow(caster, 1.1f).arcs(4).radius(0.2f, 0.8f).life(12).spin(0.5f).spawn();
            case 2 -> { // Updraft: vortice de viento ascendente
                Vec3 feet = eye.add(0, -1.55, 0);
                run(30, age -> {
                    for (int k = 0; k < 3; k++) {
                        double a = age * 0.55 + k * (Math.PI * 2 / 3);
                        double h = (age % 15) * 0.25;
                        double rad = 1.2 - h * 0.15;
                        Vec3 q = feet.add(Math.cos(a) * rad, h, Math.sin(a) * rad);
                        VfxParticles.emit(VfxPresets.SMOKE, q.x, q.y, q.z, -Math.sin(a) * 0.15, 0.18, Math.cos(a) * 0.15).tint(0xD0D8E8).size(0.8f).alpha(0.45f).life(0.6f);
                        if (k == 0) VfxParticles.emit(VfxPresets.THUNDER_MOTE, q.x, q.y, q.z, 0, 0.12, 0);
                    }
                });
                ShockRing.at(feet.add(0, 0.1, 0), new Vec3(0, 1, 0)).radius(2.5f, 0.4f).life(10).strength(0.9f).band(0.3f).rim(0.35f, STORM_PALE).spawn();
            }
            case 3, 4 -> { // Thunderfall / Tempest: vortice de nubes sobre el lugar
                Vec3 at = p.extra() == 3 ? aim(level, eye, look, 40) : eye.add(0, -1.5, 0);
                long seed = p.seed();
                run(70, age -> {
                    double a0 = age * 0.09;
                    for (int k = 0; k < 6; k++) {
                        double a = a0 + k * (Math.PI * 2 / 6);
                        double rad = 6.0 - Math.min(age, 40) * 0.05;
                        Vec3 q = at.add(Math.cos(a) * rad, 13 + Math.sin(a * 2) * 0.6, Math.sin(a) * rad);
                        VfxParticles.emit(VfxPresets.SMOKE, q.x, q.y, q.z, -Math.sin(a) * 0.12, 0, Math.cos(a) * 0.12).tint(CLOUD).size(2.6f).alpha(0.55f).life(0.8f);
                    }
                    if (age % 12 == 0) {
                        ArcBurst.at(at.add(0, 13, 0), seed + age).arcs(5).radius(0.5f, 4f).life(8).spin(0.4f).spawn();
                    }
                });
            }
            default -> {
            }
        }
    }

    private static void stormActive(WipsFx.Payload p) {
        ClientLevel level = level();
        if (level == null) return;
        Vec3 eye = p.pos();
        Vec3 look = look(p);
        Vec3 hand = hand(eye, look);
        if (p.extra() == 1) { // Tesla Beam: arco en zigzag de la mano al objetivo, regenerado cada pulso
            Vec3 end = aim(level, eye, look, 24);
            BoltStrike.between(hand, end, p.seed()).width(0.16f).forks(2).rough(0.5f).life(4).heat(1.4f).spawn();
            VfxParticles.emit(VfxPresets.THUNDER_SPARK, hand.x, hand.y, hand.z, look.x * 0.1, 0.05, look.z * 0.1);
        } else {
            for (int i = 0; i < 2; i++) {
                double a = level.random.nextDouble() * Math.PI * 2;
                Vec3 q = eye.add(Math.cos(a) * 0.8, -0.8 + level.random.nextDouble() * 1.2, Math.sin(a) * 0.8);
                VfxParticles.emit(VfxPresets.THUNDER_SPARK, q.x, q.y, q.z, 0, 0.05, 0);
            }
        }
    }

    // =====================================================================
    // tick
    // =====================================================================
    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            TIMED.clear();
            return;
        }
        if (mc.isPaused()) return;
        pyroProjectiles(level);
        hydroProjectiles(level);
        Iterator<Timed> it = TIMED.iterator();
        List<Timed> live = new ArrayList<>(TIMED);
        TIMED.clear();
        for (Timed t : live) {
            t.step().accept(t.age()[0]);
            if (++t.age()[0] < t.life()) TIMED.add(t);
        }
    }
}
