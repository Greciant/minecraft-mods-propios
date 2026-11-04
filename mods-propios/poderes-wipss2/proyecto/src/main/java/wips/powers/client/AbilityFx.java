package wips.powers.client;

import com.awakenedmod.awakened.content.pyrokinetic.FlameTone;
import com.awakenedmod.awakened.content.pyrokinetic.PyroHeatState;
import com.devilfruits.client.vfx.fx.ArcBurst;
import com.devilfruits.client.vfx.fx.BoltStrike;
import com.devilfruits.client.vfx.fx.GroundWave;
import com.devilfruits.client.vfx.fx.Impact;
import com.devilfruits.client.vfx.particle.VfxParticles;
import com.devilfruits.client.vfx.particle.VfxPresets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import wips.powers.WipsPowers;
import wips.powers.fx.WipsFx;

/**
 * Un efecto propio por habilidad (11 poderes de Awakened + Seiryuu), encima de los efectos base de
 * Awakened, que se mantienen. Cada habilidad tiene su firma: una animacion sacada de los fotogramas
 * de los samples (forma y modo distintos) y un motivo procedural que no se repite en otra habilidad.
 * Todo es aditivo o humo translucido, asi que funciona igual con shaders.
 *
 * Ranuras (0..4) en el orden de Awakened:
 * pyro: Fireball, Flame Beam, Fire Jets, Barrage, Backdraft
 * hydro: Water Lance, Geyser, Wave Rider, Tide Hook, Tsunami
 * storm: Lightning Strike, Tesla Beam, Updraft, Thunderfall, Tempest
 * kinetic: Kinetic Throw, Recoil Shot, Impact Strike, Rupture Kick, Full Release
 * velocity: Overdrive, Phasing, Static Bolt, Timeless, Speed Tunnel
 * ruin: Grip, Spread, Rush, Breach, Collapse
 * phyto: Ivy Wrap, Sunflower Field, Vine Grapple, Life Graft, Cradle of Roots
 * gamma: Provoke, Thunderclap, Smash Leap, Boulder Throw, Island Shaker
 * summoning: Summon, Recall, Command, Mass Raise, Possess
 * graviton: Heavy Hand, Gravity Well, Weightless, Dense, Chibaku Tensei
 * scaler: Size Change, Mass Leap, Hitch Stomp, Scale Ray, Clamber/Seize
 * seiryuu: Thunder Bagua, Flame Cloud Strike, Draconic Thunder, Bolo Barrage, Blast Breath
 */
@EventBusSubscriber(modid = WipsPowers.MOD_ID, value = Dist.CLIENT)
public final class AbilityFx {
    // colores tema de Awakened
    static final int FIRE = 0xFF7A2B;
    static final int EMBER = 0xFFB04A;
    static final int HOT = 0xFFE2A0;
    static final int WATER = 0x2E8DFF;
    static final int FOAM = 0xCFEFFF;
    static final int STORM = 0x8FB4FF;
    static final int STORM_PALE = 0xDDE8FF;
    static final int CLOUD = 0x3A4258;
    static final int KINETIC = 0xC77DFF;
    static final int KINETIC_PALE = 0xEBD6FF;
    static final int SPEED = 0xE8323C;
    static final int SPEED_PALE = 0xFFC2C5;
    static final int RUIN = 0x2A0E14;
    static final int RUIN_GLOW = 0xC9C4BE;
    static final int RUIN_SMOKE = 0x3A3633;
    static final int PHYTO = 0x58C24A;
    static final int PHYTO_PALE = 0xCFF5A8;
    static final int SUN = 0xF2D04A;
    static final int GAMMA = 0x76E03A;
    static final int GAMMA_PALE = 0xD4F7BC;
    static final int SUMMON = 0x38E1C8;
    static final int SUMMON_PALE = 0xBFF7EE;
    static final int GRAV = 0x8A3CFF;
    static final int GRAV_PALE = 0xD9C2FF;
    static final int MINT = 0x5BE0A8;
    static final int DUST = 0x6A6056;
    static final int WHITE = 0xFFFFFF;
    private static final Vec3 UP = new Vec3(0, 1, 0);

    private record Timed(int life, IntConsumer step, int[] age) {}

    private static final List<Timed> TIMED = new ArrayList<>();

    private AbilityFx() {
    }

    private static void run(int life, IntConsumer step) {
        TIMED.add(new Timed(life, step, new int[]{0}));
    }

    private static void later(int ticks, Runnable r) {
        run(ticks + 1, age -> {
            if (age == ticks) r.run();
        });
    }

    public static void register() {
        WipsFx.handle("cast_pyrokinetic", AbilityFx::pyro);
        WipsFx.handle("active_pyrokinetic", AbilityFx::pyroHeld);
        WipsFx.handle("pyro_burst", AbilityFx::pyroBurst);
        WipsFx.handle("cast_hydrokinetic", AbilityFx::hydro);
        WipsFx.handle("active_hydrokinetic", AbilityFx::hydroHeld);
        WipsFx.handle("cast_storm_caller", AbilityFx::storm);
        WipsFx.handle("active_storm_caller", AbilityFx::stormHeld);
        WipsFx.handle("storm_strike", AbilityFx::stormStrike);
        WipsFx.handle("cast_kinetic_charger", AbilityFx::kinetic);
        WipsFx.handle("active_kinetic_charger", AbilityFx::kineticHeld);
        WipsFx.handle("cast_velocity", AbilityFx::velocity);
        WipsFx.handle("active_velocity", AbilityFx::velocityHeld);
        WipsFx.handle("cast_ruin", AbilityFx::ruin);
        WipsFx.handle("active_ruin", AbilityFx::ruinHeld);
        WipsFx.handle("cast_phytokinetic", AbilityFx::phyto);
        WipsFx.handle("active_phytokinetic", AbilityFx::phytoHeld);
        WipsFx.handle("cast_gamma", AbilityFx::gamma);
        WipsFx.handle("cast_summoning", AbilityFx::summoning);
        WipsFx.handle("active_summoning", AbilityFx::summoningHeld);
        WipsFx.handle("cast_graviton", AbilityFx::graviton);
        WipsFx.handle("pressure", AbilityFx::pressure);
        WipsFx.handle("chibaku_implode", AbilityFx::chibakuImplode);
        WipsFx.handle("chibaku_slam", AbilityFx::chibakuSlam);
        WipsFx.handle("cast_scaler", AbilityFx::scaler);
        WipsFx.handle("scaler_shift", AbilityFx::scalerShift);
        WipsFx.handle("scaler_step", AbilityFx::scalerStep);
        WipsFx.handle("cast_seiryuu", AbilityFx::seiryuu);
    }

    // =====================================================================
    // utilidades
    // =====================================================================
    private static ClientLevel level() {
        return Minecraft.getInstance().level;
    }

    private static Vec3 look(WipsFx.Payload p) {
        return ElementalFx.look(p);
    }

    private static Vec3 feet(WipsFx.Payload p) {
        return p.pos().add(0, -1.55, 0);
    }

    private static Vec3 hand(WipsFx.Payload p) {
        return ElementalFx.hand(p.pos(), look(p));
    }

    private static Vec3 aim(WipsFx.Payload p, double range) {
        return ElementalFx.aim(level(), p.pos(), look(p), range);
    }

    /** Direccion horizontal de la mirada. */
    private static Vec3 fwd(Vec3 look) {
        Vec3 f = new Vec3(look.x, 0, look.z);
        return f.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : f.normalize();
    }

    private static double rnd() {
        return level().random.nextDouble();
    }

    private static boolean hot(Entity e) {
        try {
            return e != null && FlameTone.forHeat(PyroHeatState.visualHeat(e)).blueFraction() >= 0.5F;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    // =====================================================================
    // PYROKINETIC
    // =====================================================================
    private static void pyro(WipsFx.Payload p) {
        ClientLevel level = level();
        if (level == null) return;
        boolean blue = hot(level.getEntity(p.entity()));
        Vec3 look = look(p);
        Vec3 hand = hand(p);
        Vec3 feet = feet(p);
        switch (p.extra()) {
            case 0 -> // Fireball: la bola se forma en la mano (fuego que se enrosca)
                    FlipbookFx.billboard(blue ? "sorcerer_flame" : "burst_fire", hand.add(look.scale(0.4)), 0.9f, 12, WHITE, 1f);
            case 4 -> { // Backdraft: seis columnas de fuego que la succion arrastra hacia el usuario y estallan en el suelo
                for (int k = 0; k < 6; k++) {
                    double a = k * Math.PI / 3;
                    Vec3 from = feet.add(Math.cos(a) * 4.5, 0, Math.sin(a) * 4.5);
                    run(12, age -> {
                        if (age % 3 != 0) return;
                        double f = age / 12.0;
                        Vec3 q = from.add(feet.subtract(from).scale(f));
                        FlipbookFx.billboard(blue ? "blue_ground" : "campfire", q.add(0, 0.8, 0), 0.9f, 7, WHITE, 0.9f);
                    });
                }
                later(12, () -> {
                    FlipbookFx.flat(blue ? "ring_blue" : "ground_fire", feet.add(0, 0.08, 0), UP, 4.2f, 22, WHITE, 1f, 0f, false);
                    Impact.at(level, feet.add(0, 0.5, 0), p.seed()).flash(0.7f).glow(FIRE).embers(24).play();
                });
            }
            default -> {
            }
        }
    }

    private static void pyroHeld(WipsFx.Payload p) {
        ClientLevel level = level();
        if (level == null) return;
        boolean blue = hot(level.getEntity(p.entity()));
        Vec3 look = look(p);
        Vec3 hand = hand(p);
        switch (p.extra()) {
            case 1 -> { // Flame Beam: chorro de lanzallamas de la mano al blanco y chispas donde pega
                Vec3 end = aim(p, 14);
                FlipbookFx.stretch(blue ? "blue_jet" : "flame_jet", hand.add(look.scale(0.3)), end, blue ? 0.5f : 0.85f, 6, WHITE, 0.95f);
                FlipbookFx.billboard("sparks", end, 0.8f, 6, blue ? 0x9CCBFF : WHITE, 0.8f);
            }
            case 2 -> { // Fire Jets: dos llamas de suelo invertidas bajo los pies (propulsion)
                Vec3 feet = feet(p);
                Vec3 side = new Vec3(-fwd(look).z, 0, fwd(look).x).scale(0.22);
                for (int s = -1; s <= 1; s += 2) {
                    Vec3 at = feet.add(side.scale(s));
                    FlipbookFx.stretch("groundfire_mask", at, at.add(0, -2.4, 0), 0.5f, 5, blue ? 0x66B8FF : FIRE, 1f).boost(2);
                }
            }
            case 3 -> { // Barrage: rafaga de llamaradas pequenas en abanico delante
                Vec3[] b = ElementalFx.basis(look);
                Vec3 q = hand.add(look.scale(1.5 + rnd() * 2)).add(b[0].scale((rnd() - 0.5) * 1.2)).add(b[1].scale((rnd() - 0.5) * 0.8));
                FlipbookFx.billboard("flame_mask", q, 0.6f, 7, blue ? 0x7CC6FF : EMBER, 1f).boost(2);
            }
            default -> {
            }
        }
    }

    /** Impacto de la bola de fuego (y de las rafagas). */
    private static void pyroBurst(WipsFx.Payload p) {
        ClientLevel level = level();
        if (level == null) return;
        boolean blue = p.extra() >= 50;
        Vec3 c = p.pos();
        Vec3 n = look(p);
        float r = Math.max(0.6f, p.power());
        FlipbookFx.billboard(blue ? "sorcerer_flame" : "explosion", c.add(0, r * 0.6, 0), r * 1.4f + 0.8f, 22, WHITE, 1f);
        FlipbookFx.flat("fire_ring", c.add(n.scale(0.08)), n, r * 2.2f + 1.2f, 18, blue ? 0x7CC6FF : WHITE, 0.9f, 2f, false);
    }

    // =====================================================================
    // HYDROKINETIC
    // =====================================================================
    private static void hydro(WipsFx.Payload p) {
        ClientLevel level = level();
        if (level == null) return;
        Vec3 look = look(p);
        Vec3 hand = hand(p);
        Vec3 eye = p.pos();
        switch (p.extra()) {
            case 0 -> // Water Lance: remolino de agua que gira en la mano
                    FlipbookFx.flat("water_circle", hand.add(look.scale(0.5)), look, 1.6f, 18, FOAM, 1f, 9f, false).boost(3);
            case 1 -> { // Geyser: columna de agua que sale del suelo donde apunta y revienta en burbujas arriba
                Vec3 at = aim(p, 30);
                FlipbookFx.stretch("smoke_side", at, at.add(0, 7.5, 0), 1.5f, 30, FOAM, 0.85f);
                FlipbookFx.stretch("water_spray", at, at.add(0, 7.5, 0), 1.7f, 30, FOAM, 1f).boost(3);
                later(10, () -> FlipbookFx.billboard("bubble_cloud", at.add(0, 7, 0), 2.6f, 26, FOAM, 0.8f));
                GroundWave.at(at.x, at.y, at.z, 3.2f, 16).strength(1.0f).ring(WATER, 0.45f).spawn();
            }
            case 3 -> { // Tide Hook: latigo de agua en arco hasta el blanco
                Vec3 end = aim(p, 20);
                Vec3 ctrl = hand.add(end).scale(0.5).add(0, 2.5, 0);
                Vec3 prev = hand;
                for (int i = 1; i <= 6; i++) {
                    double t = i / 6.0;
                    Vec3 q = hand.scale((1 - t) * (1 - t)).add(ctrl.scale(2 * t * (1 - t))).add(end.scale(t * t));
                    FlipbookFx.stretch("beam_mask", prev, q, 0.35f, 10, WATER, 0.9f);
                    prev = q;
                }
                FlipbookFx.billboard("bubbles", end, 0.9f, 18, FOAM, 0.9f);
            }
            case 4 -> { // Tsunami: muro de bruma y espuma que avanza en frente curvo
                Vec3 f = fwd(look);
                Vec3 side = new Vec3(-f.z, 0, f.x);
                Vec3 base = eye.add(0, -1.5, 0);
                run(30, age -> {
                    if (age % 3 != 0) return;
                    double d = 2.0 + age * 0.65;
                    for (int i = -4; i <= 4; i++) {
                        double s = i / 4.0;
                        Vec3 q = base.add(f.scale(d - s * s * 2.0)).add(side.scale(s * 7.0)).add(0, 1.6, 0);
                        FlipbookFx.billboard("smoke_side", q, 1.3f, 10, FOAM, 0.55f);
                        if ((i + age) % 2 == 0) level.addParticle(ParticleTypes.SPLASH, q.x, q.y + 1, q.z, f.x * 0.3, 0.25, f.z * 0.3);
                    }
                });
            }
            default -> {
            }
        }
    }

    private static void hydroHeld(WipsFx.Payload p) {
        if (level() == null) return;
        if (p.extra() == 2) { // Wave Rider: estela de rocio hacia atras sobre la ola
            Vec3 look = look(p);
            Vec3 feet = feet(p);
            Vec3 back = fwd(look).scale(-3.0);
            FlipbookFx.stretch("water_spray", feet.add(0, 0.3, 0), feet.add(back).add(0, 0.9, 0), 1.2f, 9, FOAM, 1f).boost(3);
            FlipbookFx.billboard("bubble_cloud", feet.add(back.scale(0.6)).add(0, 0.6, 0), 0.9f, 10, FOAM, 1f).boost(2);
        }
    }

    // =====================================================================
    // STORM CALLER
    // =====================================================================
    private static void stormStrike(WipsFx.Payload p) {
        Vec3 g = p.pos();
        float r = Math.max(1f, p.power());
        boolean b = (p.seed() & 1) == 0;
        // Lightning Strike: el rayo de video cae y deja una onda azul a ras de suelo
        FlipbookFx.billboard(b ? "strike_b" : "strike_a", g.add(0, 3.2, 0), 3.4f + r * 0.3f, 12, WHITE, 1f);
        FlipbookFx.flat("ring_blue", g.add(0, 0.1, 0), UP, r * 1.6f + 1.4f, 14, WHITE, 0.85f, 0f, false);
    }

    private static void storm(WipsFx.Payload p) {
        ClientLevel level = level();
        if (level == null) return;
        Vec3 look = look(p);
        Vec3 eye = p.pos();
        Vec3 feet = feet(p);
        switch (p.extra()) {
            case 2 -> { // Updraft: torbellino de viento con arcos que suben enroscados
                FlipbookFx.flat("smoke_ring", feet.add(0, 0.06, 0), UP, 3.0f, 22, 0xD0D8E8, 0.55f, -8f, true);
                for (int k = 0; k < 3; k++) {
                    double a0 = k * Math.PI * 2 / 3;
                    run(24, age -> {
                        if (age % 4 != 0) return;
                        double a = a0 + age * 0.35;
                        Vec3 q = feet.add(Math.cos(a) * 1.1, age * 0.22, Math.sin(a) * 1.1);
                        FlipbookFx.billboard("arc_mask", q, 0.8f, 7, STORM, 1f).boost(2);
                    });
                }
            }
            case 3 -> { // Thunderfall: nova electrica en el suelo y un rayo gigante unos ticks despues
                Vec3 at = aim(p, 40);
                FlipbookFx.flat("nova_mask", at.add(0, 0.1, 0), UP, 4.5f, 18, STORM, 1f, 0f, true);
                later(12, () -> FlipbookFx.stretch("strike_mask", at, at.add(0, 22, 0), 5.5f, 10, STORM_PALE, 1f));
            }
            case 4 -> { // Tempest: nubarron oscuro que gira sobre el usuario con rayos dentro
                run(70, age -> {
                    if (age % 5 != 0) return;
                    double a = age * 0.09;
                    for (int k = 0; k < 4; k++) {
                        double b = a + k * Math.PI / 2;
                        Vec3 q = feet.add(Math.cos(b) * 5.5, 12.5, Math.sin(b) * 5.5);
                        FlipbookFx.billboard("gas_blast", q, 3.2f, 16, CLOUD, 0.6f);
                    }
                    if (age % 20 == 0) {
                        double b = level.random.nextDouble() * Math.PI * 2;
                        Vec3 q = feet.add(Math.cos(b) * 4, 0, Math.sin(b) * 4);
                        FlipbookFx.stretch("strike_mask", q.add(0, 12, 0), q, 1.6f, 6, STORM_PALE, 0.9f);
                    }
                });
            }
            default -> {
            }
        }
    }

    private static void stormHeld(WipsFx.Payload p) {
        ClientLevel level = level();
        if (level == null) return;
        if (p.extra() == 1) { // Tesla Beam: arco de video de la mano al blanco + rayo procedural fino
            Vec3 hand = hand(p);
            Vec3 end = aim(p, 24);
            FlipbookFx.stretch("bolt_arc", hand, end, 0.9f, 5, WHITE, 0.95f);
            BoltStrike.between(hand, end, p.seed()).width(0.1f).forks(1).rough(0.5f).life(3).heat(1.4f).spawn();
        }
    }

    // =====================================================================
    // KINETIC CHARGER (morado)
    // =====================================================================
    private static void kinetic(WipsFx.Payload p) {
        ClientLevel level = level();
        if (level == null) return;
        Vec3 look = look(p);
        Vec3 hand = hand(p);
        Vec3 feet = feet(p);
        switch (p.extra()) {
            case 0 -> // Kinetic Throw: estallido de energia violeta al soltar
                    FlipbookFx.billboard("burst_violet", hand.add(look.scale(0.6)), 1.4f, 16, WHITE, 1f).boost(3);
            case 1 -> { // Recoil Shot: disparo en linea fina y humo hacia atras por el retroceso
                FlipbookFx.stretch("spell_pink", hand, aim(p, 26), 0.5f, 10, KINETIC_PALE, 1f);
                FlipbookFx.billboard("smoke_puff", hand.subtract(look.scale(1.2)), 0.9f, 18, 0x4A4458, 0.5f);
            }
            case 2 -> { // Impact Strike: placa de choque delante del puno
                FlipbookFx.flat("shield_mask", p.pos().add(look.scale(1.5)).add(0, -0.3, 0), look, 1.4f, 10, KINETIC, 1f, 0f, false);
                Impact.at(level, p.pos().add(look.scale(1.5)), p.seed()).flash(0.5f).glow(KINETIC).sparks(14, 0.7f).play();
            }
            case 3 -> // Rupture Kick: onda morada que abre el suelo frente al pie
                    FlipbookFx.flat("ring_mask", feet.add(fwd(look).scale(1.5)).add(0, 0.08, 0), UP, 3.2f, 16, KINETIC, 1f, 0f, false);
            case 4 -> { // Full Release: nova gigante y anillo de ceniza en el suelo
                FlipbookFx.billboard("nova_mask", p.pos().add(0, -0.4, 0), 4.5f, 18, KINETIC, 1f);
                FlipbookFx.flat("ash_ring", feet.add(0, 0.06, 0), UP, 7f, 26, 0x5A5060, 0.7f, 0f, false);
                GroundWave.at(feet.x, feet.y, feet.z, 7f, 18).strength(1.4f).ring(KINETIC, 0.5f).dust(36, 0x6a5a40, 0.4f, 0.9f, 1.6f, p.seed()).spawn();
            }
            default -> {
            }
        }
    }

    private static void kineticHeld(WipsFx.Payload p) {
        if (level() == null) return;
        // carga: lineas de chispa que convergen hacia el pecho
        Vec3 chest = p.pos().add(0, -0.5, 0);
        Vec3 d = GravitonScalerFx.fib(level().random.nextInt(60), 60);
        FlipbookFx.stretch("spark_mask", chest.add(d.scale(2.8)), chest.add(d.scale(0.6)), 0.35f, 6, KINETIC, 1f).boost(3);
    }

    // =====================================================================
    // VELOCITY (rojo)
    // =====================================================================
    private static void velocity(WipsFx.Payload p) {
        ClientLevel level = level();
        if (level == null) return;
        Vec3 look = look(p);
        Vec3 eye = p.pos();
        Entity e = level.getEntity(p.entity());
        switch (p.extra()) {
            case 0 -> { // Overdrive: aura roja que arde un momento alrededor del cuerpo
                if (e != null) FlipbookFx.billboardOn(e, new Vec3(0, 1.0, 0), "flame_mask", 1.2f, 18, SPEED, 0.8f);
            }
            case 1 -> // Phasing: compuerta de fase delante que se cierra al cruzarla
                    FlipbookFx.flat("portal_mask", eye.add(look.scale(1.6)).add(0, -0.5, 0), look, 1.5f, 16, SPEED_PALE, 1f, 0f, true).boost(2);
            case 2 -> // Static Bolt: relampago rojo de la mano al blanco
                    FlipbookFx.stretch("arc_mask", hand(p), aim(p, 22), 1.1f, 9, SPEED, 1f).boost(3);
            case 3 -> { // Timeless: un remolino que gira hacia atras (el tiempo se frena)
                FlipbookFx.flat("water_circle", eye.add(0, -0.6, 0), UP, 2.6f, 26, SPEED, 1f, -14f, true).boost(3);
                FlipbookFx.flat("water_circle", eye.add(0, -0.6, 0), UP, 1.5f, 26, SPEED_PALE, 1f, 20f, true).boost(2);
            }
            case 4 -> { // Speed Tunnel: tunel de seis lineas largas a lo largo de la mirada
                Vec3[] b = ElementalFx.basis(look);
                for (int k = 0; k < 6; k++) {
                    double a = k * Math.PI / 3;
                    Vec3 o = b[0].scale(Math.cos(a) * 1.5).add(b[1].scale(Math.sin(a) * 1.5));
                    FlipbookFx.stretch("beam_mask", eye.add(look.scale(1.5)).add(o), eye.add(look.scale(18)).add(o), 0.3f, 16, k % 2 == 0 ? SPEED : SPEED_PALE, 0.85f);
                }
            }
            default -> {
            }
        }
    }

    private static void velocityHeld(WipsFx.Payload p) {
        ClientLevel level = level();
        if (level == null) return;
        Entity e = level.getEntity(p.entity());
        if (e == null) return;
        Vec3 v = e.getDeltaMovement();
        Vec3 dir = new Vec3(v.x, 0, v.z);
        if (dir.lengthSqr() < 0.02) return;
        dir = dir.normalize();
        // polvo que levanta al correr y estelas de velocidad
        FlipbookFx.billboard("smoke_side", e.position().subtract(dir.scale(1.2)).add(0, 0.25, 0), 0.6f, 10, 0xB8A898, 0.45f);
        Vec3 c = e.position().add(0, 0.9, 0);
        Vec3[] b = ElementalFx.basis(dir);
        for (int i = 0; i < 6; i++) {
            double a = level.random.nextDouble() * Math.PI * 2;
            double r = 0.3 + level.random.nextDouble() * 0.6;
            Vec3 q = c.add(b[0].scale(Math.cos(a) * r)).add(b[1].scale(Math.sin(a) * r)).add(dir.scale(-0.5 - level.random.nextDouble()));
            VfxParticles.emit(VfxPresets.SPEED_LINE, q.x, q.y, q.z, -dir.x * 0.6, -dir.y * 0.6, -dir.z * 0.6).tint(i % 3 == 0 ? SPEED : SPEED_PALE).size(0.8f).alpha(0.75f);
        }
    }

    // =====================================================================
    // RUIN (ceniza, gris)
    // =====================================================================
    private static void ruin(WipsFx.Payload p) {
        ClientLevel level = level();
        if (level == null) return;
        Vec3 look = look(p);
        Vec3 feet = feet(p);
        switch (p.extra()) {
            case 0 -> // Grip: la mano se cierra y lo agarrado se deshace en polvo
                    FlipbookFx.billboard("smoke_puff", hand(p).add(look.scale(1.0)), 1.2f, 24, RUIN_SMOKE, 1f);
            case 1 -> // Spread: anillo de ceniza que se extiende por el suelo
                    FlipbookFx.flat("ash_ring", feet.add(0, 0.06, 0), UP, 6.5f, 30, RUIN_SMOKE, 0.8f, 0f, false);
            case 3 -> // Breach: chorro de polvo que entra en la pared/suelo apuntado
                    FlipbookFx.stretch("smoke_side", p.pos().add(look.scale(1.0)), aim(p, 6).add(look.scale(1.5)), 0.7f, 20, RUIN_SMOKE, 0.7f);
            case 4 -> { // Collapse: onda de humo enorme a ras de suelo y grietas palidas
                FlipbookFx.flat("smoke_ring", feet.add(0, 0.06, 0), UP, 10f, 34, RUIN_SMOKE, 0.85f, 0f, false);
                for (int i = 0; i < 12; i++) {
                    double a = i * Math.PI / 6;
                    Vec3 q = feet.add(Math.cos(a) * (2 + rnd() * 6), 0.1, Math.sin(a) * (2 + rnd() * 6));
                    VfxParticles.emit(VfxPresets.CRACKS_VIOLET, q.x, q.y, q.z, 0, 0, 0).tint(RUIN_GLOW).size(1.3f);
                }
            }
            default -> {
            }
        }
    }

    private static void ruinHeld(WipsFx.Payload p) {
        if (level() == null) return;
        if (p.extra() == 2) { // Rush: nube de escombro detras al embestir
            Vec3 back = fwd(look(p)).scale(-1.4);
            FlipbookFx.billboard("gas_blast", feet(p).add(back).add(0, 0.7, 0), 0.9f, 14, RUIN_SMOKE, 0.55f);
        }
    }

    // =====================================================================
    // PHYTOKINETIC (verde)
    // =====================================================================
    private static void phyto(WipsFx.Payload p) {
        ClientLevel level = level();
        if (level == null) return;
        Vec3 look = look(p);
        Vec3 feet = feet(p);
        switch (p.extra()) {
            case 0 -> { // Ivy Wrap: enredadera de energia que sube alrededor del blanco
                Vec3 at = aim(p, 16);
                FlipbookFx.billboard("groundfire_mask", at.add(0, 1.0, 0), 1.2f, 26, PHYTO, 0.9f);
                spiralRoots(at, 3, 1.0);
            }
            case 1 -> // Sunflower Field: flor dorada que se abre en el suelo
                    FlipbookFx.flat("nova_mask", feet.add(0, 0.07, 0), UP, 5.5f, 30, SUN, 1f, 2f, false);
            case 2 -> // Vine Grapple: liana tensa hasta el punto de enganche
                    FlipbookFx.stretch("spark_mask", hand(p), aim(p, 24), 0.5f, 12, PHYTO, 1f).boost(3);
            case 3 -> { // Life Graft: llama de vida que envuelve al usuario
                Entity e = level.getEntity(p.entity());
                if (e != null) FlipbookFx.billboardOn(e, new Vec3(0, 1.0, 0), "teal_flame", 1.2f, 30, 0x9CF59A, 0.8f);
            }
            case 4 -> { // Cradle of Roots: cuna de raices en espiral + esporas que suben
                spiralRoots(feet, 6, 3.0);
                for (int i = 0; i < 5; i++) {
                    double a = i * Math.PI * 2 / 5;
                    later(i * 3, () -> FlipbookFx.billboard("bubbles", feet.add(Math.cos(a) * 2.5, 1.5, Math.sin(a) * 2.5), 1.0f, 28, PHYTO_PALE, 0.8f));
                }
            }
            default -> {
            }
        }
    }

    private static void spiralRoots(Vec3 at, int roots, double r) {
        ClientLevel level = level();
        for (int k = 0; k < roots; k++) {
            double a0 = k * (Math.PI * 2 / roots);
            Vec3 base = at.add(Math.cos(a0) * r * 0.6, 0, Math.sin(a0) * r * 0.6);
            run(22, age -> {
                double a = a0 + age * 0.45;
                Vec3 q = base.add(Math.cos(a) * 0.35, age * 0.12, Math.sin(a) * 0.35);
                VfxParticles.emit(VfxPresets.GLOW, q.x, q.y, q.z, 0, 0.01, 0).tint(age % 2 == 0 ? PHYTO : PHYTO_PALE).size(0.45f).alpha(0.85f).life(1.0f);
                if (age % 4 == 0) level.addParticle(ParticleTypes.SPORE_BLOSSOM_AIR, q.x, q.y, q.z, 0, 0, 0);
            });
        }
    }

    private static void phytoHeld(WipsFx.Payload p) {
        ClientLevel level = level();
        if (level == null || level.random.nextInt(3) != 0) return;
        Vec3 c = p.pos().add(0, -0.8, 0);
        level.addParticle(ParticleTypes.SPORE_BLOSSOM_AIR, c.x + (rnd() - 0.5) * 2, c.y + (rnd() - 0.5) * 1.5, c.z + (rnd() - 0.5) * 2, 0, 0, 0);
    }

    // =====================================================================
    // GAMMA (verde neon)
    // =====================================================================
    private static void gamma(WipsFx.Payload p) {
        ClientLevel level = level();
        if (level == null) return;
        Vec3 look = look(p);
        Vec3 eye = p.pos();
        Vec3 feet = feet(p);
        switch (p.extra()) {
            case 0 -> // Provoke: rugido, fogonazo de radiacion en el pecho
                    FlipbookFx.billboard("burst_mask", eye.add(look.scale(0.8)).add(0, -0.5, 0), 1.6f, 16, GAMMA, 1f);
            case 1 -> // Thunderclap: aplauso, onda vertical que sale hacia delante
                    FlipbookFx.flat("ring_mask", eye.add(look.scale(1.4)).add(0, -0.4, 0), look, 2.8f, 12, GAMMA_PALE, 1f, 0f, false);
            case 2 -> { // Smash Leap: nube de polvo verdoso al despegar
                FlipbookFx.billboard("gas_blast", feet.add(0, 0.8, 0), 2.0f, 22, 0x5A6A4A, 0.7f);
                GroundWave.at(feet.x, feet.y, feet.z, 4f, 16).strength(1.1f).ring(GAMMA, 0.4f).dust(24, 0x3a4a30, 0.4f, 0.8f, 1.4f, p.seed()).spawn();
            }
            case 3 -> // Boulder Throw: chispas de radiacion al arrancar la roca
                    FlipbookFx.billboard("spark_mask", hand(p).add(0, 0.6, 0), 1.7f, 18, GAMMA, 1f).boost(3);
            case 4 -> { // Island Shaker: la isla se agrieta en rayos de energia verde
                Vec3 f = fwd(look);
                for (int k = -1; k <= 1; k++) {
                    Vec3 dir = f.yRot(k * 0.5f);
                    Vec3 at = feet.add(dir.scale(4));
                    FlipbookFx.flat("strike_mask", at.add(0, 0.1, 0), UP, 4f, 24, GAMMA, 1f, 0f, false);
                }
            }
            default -> {
            }
        }
    }

    // =====================================================================
    // SUMMONING (turquesa)
    // =====================================================================
    private static void summoning(WipsFx.Payload p) {
        ClientLevel level = level();
        if (level == null) return;
        Vec3 look = look(p);
        Vec3 feet = feet(p);
        Vec3 eye = p.pos();
        switch (p.extra()) {
            case 0 -> { // Summon: portal en el suelo y una llama fantasma que sube de el
                Vec3 at = feet.add(fwd(look).scale(2.5));
                FlipbookFx.flat("portal_mask", at.add(0, 0.07, 0), UP, 2.2f, 45, SUMMON, 0.95f, 3f, false);
                later(8, () -> FlipbookFx.billboard("teal_flame", at.add(0, 1.3, 0), 1.4f, 34, WHITE, 0.9f));
            }
            case 1 -> // Recall: escudo turquesa que se cierra delante (los aliados vuelven)
                    FlipbookFx.billboard("shield", eye.add(look.scale(1.6)).add(0, -0.3, 0), 1.3f, 16, WHITE, 1f);
            case 2 -> { // Command: senal de mando, haz fino de la mano a donde apunta
                Vec3 at = aim(p, 30);
                FlipbookFx.stretch("beam_blue", hand(p), at, 0.4f, 14, SUMMON_PALE, 1f);
                FlipbookFx.flat("water_circle", at.add(0, 0.1, 0), UP, 1.3f, 20, SUMMON, 0.9f, 6f, false);
            }
            case 3 -> { // Mass Raise: cinco llamas fantasma en circulo alrededor del usuario
                for (int k = 0; k < 5; k++) {
                    double a = k * Math.PI * 2 / 5;
                    Vec3 q = feet.add(Math.cos(a) * 3.5, 0, Math.sin(a) * 3.5);
                    later(k * 4, () -> {
                        FlipbookFx.billboard("blue_ground", q.add(0, 1.0, 0), 1.1f, 30, 0x9FFFF0, 0.9f);
                        for (int i = 0; i < 8; i++) level.addParticle(ParticleTypes.REVERSE_PORTAL, q.x, q.y + 0.2, q.z, 0, 0.3, 0);
                    });
                }
            }
            case 4 -> // Possess: brecha morada que se abre en la mira
                    FlipbookFx.flat("portal", eye.add(look.scale(3.0)), look, 1.5f, 24, WHITE, 1f, 6f, false);
            default -> {
            }
        }
    }

    private static void summoningHeld(WipsFx.Payload p) {
        ClientLevel level = level();
        if (level == null) return;
        Vec3 c = p.pos().add(0, -0.6, 0);
        double a = rnd() * Math.PI * 2;
        Vec3 q = c.add(Math.cos(a) * 0.8, rnd() * 1.2 - 0.4, Math.sin(a) * 0.8);
        VfxParticles.emit(VfxPresets.GLOW, q.x, q.y, q.z, 0, 0.04, 0).tint(SUMMON_PALE).size(0.4f).alpha(0.8f).life(0.8f);
    }

    // =====================================================================
    // GRAVITON (violeta oscuro): ranura 3 = presion (GravitonScalerFx), 4 = Chibaku Tensei
    // =====================================================================
    private static void graviton(WipsFx.Payload p) {
        ClientLevel level = level();
        if (level == null) return;
        Vec3 look = look(p);
        switch (p.extra()) {
            case 0 -> { // Heavy Hand: flor de energia aplastada contra el suelo donde apunta
                Vec3 at = aim(p, 18);
                FlipbookFx.flat("burst_violet", at.add(0, 0.08, 0), UP, 2.0f, 16, WHITE, 1f, 0f, false);
            }
            case 1 -> // Gravity Well: portal que se traga todo hacia dentro (al reves)
                    FlipbookFx.flat("portal", aim(p, 22).add(0, 0.1, 0), UP, 3.2f, 40, WHITE, 0.95f, -5f, true);
            case 2 -> { // Weightless: motas que flotan hacia arriba
                Entity e = level.getEntity(p.entity());
                if (e != null) FlipbookFx.billboardOn(e, new Vec3(0, 0.9, 0), "bubble_cloud", 1.5f, 30, GRAV_PALE, 1f).boost(3);
            }
            default -> {
            }
        }
    }

    private static void pressure(WipsFx.Payload p) {
        ClientLevel level = level();
        if (level == null || p.extra() <= 0) return;
        if (level.random.nextInt(2) == 0) {
            FlipbookFx.flat("smoke_ring", p.pos().add(0, 0.05, 0), UP, p.power() * 0.9f, 20, 0x2A2030, 0.35f, 0f, true);
        }
    }

    private static void chibakuImplode(WipsFx.Payload p) {
        FlipbookFx.billboard("burst_violet", p.pos(), 3.6f, 14, WHITE, 1f).reverse = true;
    }

    private static void chibakuSlam(WipsFx.Payload p) {
        Vec3 g = p.pos();
        float r = p.power();
        FlipbookFx.billboard("explosion", g.add(0, r * 0.45, 0), r * 0.75f, 30, WHITE, 1f);
        FlipbookFx.billboard("gas_blast", g.add(0, r * 0.3, 0), r * 0.6f, 34, 0x4A4050, 0.75f);
        FlipbookFx.flat("smoke_ring", g.add(0, 0.08, 0), UP, r * 1.8f, 36, 0x4A4050, 0.8f, 0f, false);
    }

    // =====================================================================
    // SCALER (menta, polvo de tierra)
    // =====================================================================
    private static void scaler(WipsFx.Payload p) {
        ClientLevel level = level();
        if (level == null) return;
        Entity e = level.getEntity(p.entity());
        float s = e != null ? Math.max(1f, e.getBbHeight() / 1.8f) : 1f;
        Vec3 look = look(p);
        Vec3 feet = e != null ? e.position() : feet(p);
        switch (p.extra()) {
            case 1 -> // Mass Leap: polvareda al despegar, proporcional al tamano
                    FlipbookFx.billboard("smoke_side", feet.add(0, 0.3 * s, 0), 1.4f * s, 24, DUST, 0.85f);
            case 2 -> { // Hitch Stomp: pisoton con anillo de polvo y piedras
                FlipbookFx.flat("ash_ring", feet.add(0, 0.06, 0), UP, 2.6f * s, 24, DUST, 0.75f, 0f, false);
                GroundWave.at(feet.x, feet.y, feet.z, 2.5f * s, 16).strength(1.2f).dust((int) (14 * s), 0x6a5a40, 0.4f, 0.8f, 1.4f, p.seed()).spawn();
            }
            case 3 -> // Scale Ray: rayo menta que encoge/agranda
                    FlipbookFx.stretch("beam_mask", hand(p), aim(p, 30), 0.35f * Math.min(s, 3f), 12, MINT, 1f);
            case 4 -> // Clamber/Seize: la mano gigante agarra, placa de agarre menta
                    FlipbookFx.flat("shield_mask", p.pos().add(look.scale(1.2 * s)), look, 1.2f * s, 14, MINT, 1f, 0f, true).boost(2);
            default -> {
            }
        }
    }

    private static void scalerShift(WipsFx.Payload p) {
        float s = Math.max(0.3f, p.power());
        boolean grow = p.extra() == 1;
        // crecer: nova que se abre; encoger: la misma que se cierra
        FlipbookFx.flat("nova_mask", p.pos().add(0, 0.1, 0), UP, 2.4f * s, 16, MINT, 1f, 0f, !grow);
    }

    private static void scalerStep(WipsFx.Payload p) {
        float s = p.power();
        FlipbookFx.flat("smoke_ring", p.pos().add(0, 0.06, 0), UP, 1.4f * s, 22, DUST, 0.65f, 0f, false);
        if (s >= 4.5f) {
            FlipbookFx.billboard("smoke_puff", p.pos().add(0, 0.35 * s, 0), 0.5f * s, 24, DUST, 0.5f);
        }
    }

    // =====================================================================
    // SEIRYUU: acentos sobre sus efectos propios (rayo y llama del dragon azul)
    // =====================================================================
    private static void seiryuu(WipsFx.Payload p) {
        ClientLevel level = level();
        if (level == null) return;
        Vec3 look = look(p);
        Vec3 hand = hand(p);
        Vec3 feet = feet(p);
        switch (p.extra()) {
            case 0 -> { // Thunder Bagua: arco electrico en el puno
                Entity e = level.getEntity(p.entity());
                ArcBurst.at(hand, p.seed()).follow(e, 1.1f).arcs(4).radius(0.2f, 0.9f).life(12).spin(0.5f).spawn();
                FlipbookFx.billboard("bolt_arc", hand.add(look.scale(0.6)), 0.9f, 12, WHITE, 1f);
            }
            case 1 -> // Flame Cloud Strike: llama azul que sube en columna desde el suelo
                    FlipbookFx.billboard("blue_ground", feet.add(fwd(look).scale(2.5)).add(0, 1.6, 0), 2.0f, 28, WHITE, 1f);
            case 2 -> { // Draconic Thunder: rayo grande desde el cielo
                Vec3 at = aim(p, 30);
                FlipbookFx.stretch("strike_b", at, at.add(0, 18, 0), 4.2f, 12, WHITE, 1f);
            }
            case 3 -> // Bolo Barrage: llamarada dorada al formar las bolas
                    FlipbookFx.billboard("burst_fire", hand.add(look.scale(1.0)), 1.3f, 16, WHITE, 1f);
            case 4 -> // Blast Breath: chorro de fuego azul desde la boca
                    FlipbookFx.stretch("blue_jet", p.pos().add(look.scale(0.6)), p.pos().add(look.scale(16)), 1.2f, 20, WHITE, 1f);
            default -> {
            }
        }
    }

    // =====================================================================
    // tick
    // =====================================================================
    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            TIMED.clear();
            return;
        }
        if (mc.isPaused()) return;
        List<Timed> live = new ArrayList<>(TIMED);
        TIMED.clear();
        for (Timed t : live) {
            t.step().accept(t.age()[0]);
            if (++t.age()[0] < t.life()) TIMED.add(t);
        }
    }
}
