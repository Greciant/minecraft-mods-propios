package wips.powers.fighter;

import com.awakenedmod.awakened.content.brawler.ReelInAbility;
import com.awakenedmod.awakened.core.AimUtil;
import com.awakenedmod.awakened.core.Fx;
import com.awakenedmod.awakened.core.PowerService;
import com.awakenedmod.awakened.core.power.Ability;
import com.awakenedmod.awakened.core.power.Power;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import wips.powers.WipsPowers;
import wips.powers.kit.PowerKit;
import wips.powers.level.WipsScaling;
import wips.powers.terrain.WipsDestruction;

/**
 * Poder LUCHADOR (referencia: Sett). Pasiva: Determinacion (barra de dano recibido). Habilidades:
 *  1 Punos ardientes - 2 Reel In (el de Brawler, ampliado) - 3 Golpe de represalia - 4 Salto de impacto - 5 GOLPE SERIO.
 */
@EventBusSubscriber(modid = WipsPowers.MOD_ID)
public final class FighterPower extends Power {
    public static final String ID = "fighter";
    public static final int ORANGE = 0xFF7A2A;
    public static final double REEL_RANGE = 48.0;
    /** Durante el Reel In del Luchador (BrawlerUnliftableMixin). */
    public static boolean reeling;

    public FighterPower() {
        super(ID, List.<Ability>of(new BurningFists(), reelIn(), new Retaliation(), new ImpactLeap(), new SeriousPunch()));
    }

    /** El Reel In original de Brawler, desbloqueado en maestria 2 (como la ranura 2 de los demas poderes). */
    private static Ability reelIn() {
        ReelInAbility a = new ReelInAbility();
        try {
            java.lang.reflect.Field f = Ability.class.getDeclaredField("unlockLevel");
            f.setAccessible(true);
            f.setInt(a, 2);
        } catch (ReflectiveOperationException e) {
            WipsPowers.LOGGER.warn("[wipspowers] Reel In: no se pudo cambiar el nivel de desbloqueo: {}", e.toString());
        }
        return a;
    }

    @Override
    public int themeColor() {
        return 0xFF000000 | ORANGE;
    }

    @Override
    public int signatureSlot() {
        return 0;
    }

    abstract static class FighterAbility extends Ability {
        FighterAbility(String id, float cost, int unlock) {
            super(id, cost, unlock);
        }

        @Override
        public Component displayName() {
            return Component.translatable("ability.wipspowers." + this.id());
        }

        @Override
        public Component description() {
            return Component.translatable("ability.wipspowers." + this.id() + ".desc");
        }
    }

    // =====================================================================================================
    // PASIVA: DETERMINACION. La barra acumula el dano REALMENTE recibido (despues de armadura; solo golpes con
    // atacante: nada ambiental ni de efectos; un golpe por atacante y tick). Sube el dano del jugador hasta +20 % y su
    // resistencia hasta +20 %. Llena con dano igual al 60 % de la vida maxima; baja sola tras 6 s sin recibir golpes.
    // =====================================================================================================
    public static final class Grit {
        static final Map<UUID, float[]> BAR = new HashMap<>(); // [0..1, ultimo tick de golpe, ultimo tick sync]
        static final Map<UUID, Long> LAST_SRC = new HashMap<>();
        public static final float MAX_BONUS = 0.20F;
        public static final float FILL_FRACTION = 0.6F;

        public static float value(ServerPlayer p) {
            float[] b = BAR.get(p.getUUID());
            return b == null ? 0 : b[0];
        }

        public static void set(ServerPlayer p, float v) {
            float[] b = BAR.computeIfAbsent(p.getUUID(), k -> new float[3]);
            b[0] = Mth.clamp(v, 0, 1);
            sync(p, b[0]);
        }

        static void add(ServerPlayer p, float damage, LivingEntity attacker) {
            long now = p.serverLevel().getGameTime();
            long key = now * 31 + attacker.getId();
            Long last = LAST_SRC.put(p.getUUID(), key);
            if (last != null && last == key) {
                return; // evento duplicado
            }
            float[] b = BAR.computeIfAbsent(p.getUUID(), k -> new float[3]);
            b[0] = Mth.clamp(b[0] + damage / (p.getMaxHealth() * FILL_FRACTION), 0, 1);
            b[1] = now;
            sync(p, b[0]);
        }

        static void tick(ServerPlayer p) {
            float[] b = BAR.get(p.getUUID());
            if (b == null || b[0] <= 0) {
                return;
            }
            long now = p.serverLevel().getGameTime();
            if (now - (long) b[1] > 120 && now % 20 == 0) {
                b[0] = Math.max(0, b[0] - 0.05F);
                sync(p, b[0]);
            }
            // particulas segun la acumulacion
            if (now % 5 == 0 && b[0] > 0.05F) {
                int n = 1 + (int) (b[0] * 6);
                p.serverLevel().sendParticles(b[0] > 0.66F ? ParticleTypes.FLAME : b[0] > 0.33F ? ParticleTypes.SMALL_FLAME : ParticleTypes.CRIT,
                        p.getX(), p.getY() + 1.0, p.getZ(), n, 0.45, 0.6, 0.45, 0.01);
            }
        }

        static void sync(ServerPlayer p, float v) {
            PacketDistributor.sendToPlayer(p, new GritSync(v));
        }
    }

    public record GritSync(float value) implements CustomPacketPayload {
        public static final Type<GritSync> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(WipsPowers.MOD_ID, "grit"));
        public static final StreamCodec<ByteBuf, GritSync> CODEC = ByteBufCodecs.FLOAT.map(GritSync::new, GritSync::value);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Dano recibido real -> barra. */
    @SubscribeEvent
    public static void onDamaged(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer p && PowerKit.has(p, ID) && event.getNewDamage() > 0
                && event.getSource().getEntity() instanceof LivingEntity attacker && attacker != p) {
            Grit.add(p, event.getNewDamage(), attacker);
        }
    }

    /** Bonus de la pasiva: +dano saliente y -dano entrante segun la barra. Puños ardientes y su contador. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onIncoming(LivingIncomingDamageEvent event) {
        DamageSource src = event.getSource();
        if (event.getEntity() instanceof ServerPlayer victim && PowerKit.has(victim, ID) && src.getEntity() != null) {
            event.setAmount(event.getAmount() * (1.0F - Grit.MAX_BONUS * Grit.value(victim)));
        }
        if (src.getEntity() instanceof ServerPlayer p && p != event.getEntity() && PowerKit.has(p, ID)) {
            float amount = event.getAmount() * (1.0F + Grit.MAX_BONUS * Grit.value(p));
            if (src.getDirectEntity() == p && !WipsScaling.isPowerHit(src)) {
                amount = BurningFists.onBasicHit(p, event.getEntity(), amount);
            }
            event.setAmount(amount);
        }
    }

    // =====================================================================================================
    // 1. PUNOS ARDIENTES: 10 s de mejora de los golpes basicos reales (M1): punos en llamas, contador que sube el dano
    //    de los golpes basicos hasta +20 % (+4 % por golpe seguido) y cada golpe suelta una onda que dana al golpeado y
    //    a los cercanos. No es un ataque aparte: solo cambia el M1 mientras dura.
    // =====================================================================================================
    public static final class BurningFists extends FighterAbility {
        public static final int DURATION = 200;
        public static final float WAVE_DAMAGE = 2.5F;
        public static final double WAVE_RADIUS = 3.5;
        static final Map<UUID, int[]> ACTIVE = new HashMap<>(); // [ticks restantes, contador, ultimo golpe]

        public BurningFists() {
            super("fighter_burning_fists", 30.0F, 0);
        }

        @Override
        public String activationAnimation(ServerPlayer player) {
            return "solar_onslaught_jab";
        }

        @Override
        public boolean activate(ServerPlayer player) {
            ACTIVE.put(player.getUUID(), new int[]{DURATION, 0, -100});
            Fx.sound(player.level(), player.position(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0F, 0.7F);
            this.startCooldown(player, 400);
            return true;
        }

        public static boolean on(ServerPlayer p) {
            return ACTIVE.containsKey(p.getUUID());
        }

        static float onBasicHit(ServerPlayer p, LivingEntity target, float amount) {
            int[] s = ACTIVE.get(p.getUUID());
            if (s == null || !PowerKit.enemy(p, target)) {
                return amount;
            }
            int now = p.server.getTickCount();
            if (now - s[2] > 60) {
                s[1] = 0; // 3 s sin pegar: el contador vuelve a 0
            }
            s[1] = Math.min(5, s[1] + 1);
            s[2] = now;
            float boosted = amount * (1.0F + 0.04F * s[1]);
            target.igniteForSeconds(3.0F);
            ServerLevel level = p.serverLevel();
            Vec3 c = target.position().add(0, target.getBbHeight() * 0.4, 0);
            // onda: dana al golpeado y a los cercanos (golpe de poder: lo escala el nivel)
            for (LivingEntity e : PowerKit.enemies(p, level, c, WAVE_RADIUS, 10)) {
                e.invulnerableTime = 0;
                PowerKit.hit(p, e, WAVE_DAMAGE);
                Vec3 away = e.position().subtract(p.position()).normalize();
                e.push(away.x * 0.35, 0.12, away.z * 0.35);
            }
            for (int i = 0; i < 20; i++) {
                double a = i * (Math.PI * 2 / 20);
                level.sendParticles(ParticleTypes.FLAME, c.x + Math.cos(a) * 1.2, c.y, c.z + Math.sin(a) * 1.2, 0, Math.cos(a), 0.05, Math.sin(a), 0.25);
            }
            Fx.sound(level, c, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.8F, 1.3F);
            return boosted;
        }

        static void tick(ServerPlayer p, int[] s) {
            if (--s[0] <= 0 || !PowerKit.has(p, ID)) {
                ACTIVE.remove(p.getUUID());
                return;
            }
            if (p.tickCount % 2 == 0) {
                for (boolean off : new boolean[]{false, true}) {
                    Vec3 hand = AimUtil.handPosition(p, off);
                    p.serverLevel().sendParticles(ParticleTypes.FLAME, hand.x, hand.y, hand.z, 1, 0.05, 0.05, 0.05, 0.005);
                }
            }
        }
    }

    // =====================================================================================================
    // 3. GOLPE DE REPRESALIA: onda enorme hacia adelante con forma de trapecio (como Rupture Kick de Kinetic Charger,
    //    mucho mas grande y ancha). El dano escala con la barra de Determinacion (que se consume) y depende de la zona:
    //    centro 200 %, bordes 60 %, tramo final 60 %.
    // =====================================================================================================
    public static final class Retaliation extends FighterAbility {
        public static final double LENGTH = 16.0;
        public static final double NEAR_HALF = 2.0;
        public static final double FAR_HALF = 7.0;
        public static final float BASE = 6.0F;
        /** Autotest: ultimos multiplicadores de zona aplicados. */
        public static final List<Float> LAST_ZONES = new ArrayList<>();

        public Retaliation() {
            super("fighter_retaliation", 40.0F, 5);
        }

        @Override
        public String activationAnimation(ServerPlayer player) {
            return "rupture_kick";
        }

        /** Multiplicador de zona para un punto en coordenadas locales (f = adelante, l = lateral). 0 = fuera. */
        public static float zone(double f, double l) {
            if (f < 0 || f > LENGTH) {
                return 0;
            }
            double half = NEAR_HALF + (FAR_HALF - NEAR_HALF) * (f / LENGTH);
            double al = Math.abs(l);
            if (al > half) {
                return 0;
            }
            if (f > LENGTH * 0.75) {
                return 0.6F; // tramo final
            }
            return al <= half * 0.5 ? 2.0F : 0.6F; // centro / bordes
        }

        @Override
        public boolean activate(ServerPlayer player) {
            ServerLevel level = player.serverLevel();
            float bar = Grit.value(player);
            float total = BASE * (1.0F + 1.5F * bar);
            Grit.set(player, 0); // la represalia consume la barra
            Vec3 fwd = AimUtil.lookFlat(player);
            Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
            Vec3 o = player.position();
            LAST_ZONES.clear();
            for (LivingEntity e : PowerKit.enemies(player, level, o.add(fwd.scale(LENGTH * 0.5)), LENGTH * 0.5 + FAR_HALF, 60)) {
                Vec3 d = e.position().subtract(o);
                if (Math.abs(d.y) > 4) {
                    continue;
                }
                float z = zone(d.dot(fwd), d.dot(right));
                if (z <= 0) {
                    continue;
                }
                e.invulnerableTime = 0;
                if (PowerKit.hit(player, e, total * z)) {
                    LAST_ZONES.add(z);
                    e.push(fwd.x * 0.9 * z, 0.35, fwd.z * 0.9 * z);
                    e.hurtMarked = true;
                }
            }
            // la onda: filas de particulas que avanzan con forma de trapecio
            for (double f = 1; f <= LENGTH; f += 0.8) {
                double half = NEAR_HALF + (FAR_HALF - NEAR_HALF) * (f / LENGTH);
                for (double l = -half; l <= half; l += 0.9) {
                    Vec3 p = o.add(fwd.scale(f)).add(right.scale(l));
                    boolean center = Math.abs(l) <= half * 0.5 && f <= LENGTH * 0.75;
                    level.sendParticles(center ? ParticleTypes.FLAME : ParticleTypes.CLOUD, p.x, p.y + 0.3, p.z, 0, fwd.x, 0.15, fwd.z, 0.4);
                }
            }
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, o.x + fwd.x * 2, o.y + 1, o.z + fwd.z * 2, 6, 1.0, 0.3, 1.0, 0);
            Fx.sound(level, o, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.6F, 0.6F);
            Fx.sound(level, o, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.0F, 1.2F);
            Fx.shake(level, o, 20.0, 0.2F + 0.3F * bar, 4, 8);
            this.startCooldown(player, 240);
            return true;
        }
    }

    // =====================================================================================================
    // 4. SALTO DE IMPACTO: 1ra pulsacion, carga y salto muy alto; 2da pulsacion (en el aire), cae en picada y al
    //    aterrizar suelta una onda cuyo dano escala con la altura alcanzada. Sin dano de caida propio; tocar paredes
    //    o techos no dispara nada.
    // =====================================================================================================
    public static final class ImpactLeap extends FighterAbility {
        public static final float BASE = 4.0F;
        public static final float PER_BLOCK = 0.6F;
        public static final float CAP = 30.0F;
        static final Map<UUID, Leap> LEAPS = new HashMap<>();
        /** Autotest: ultima altura y dano base de la onda. */
        public static double lastHeight;
        public static float lastBase;

        static final class Leap {
            int phase; // 0 carga, 1 en el aire, 2 cayendo
            int ticks;
            double maxY;

            Leap(double y) {
                this.maxY = y;
            }
        }

        public ImpactLeap() {
            super("fighter_impact_leap", 35.0F, 8);
        }

        @Override
        public float staminaCost(ServerPlayer player) {
            return LEAPS.containsKey(player.getUUID()) ? 0.0F : super.staminaCost(player);
        }

        @Override
        public String activationAnimation(ServerPlayer player) {
            Leap l = LEAPS.get(player.getUUID());
            return l == null ? "gamma_leap_charge" : null;
        }

        @Override
        public boolean activate(ServerPlayer player) {
            Leap l = LEAPS.get(player.getUUID());
            if (l == null) {
                if (!player.onGround()) {
                    return this.fail(player, "message.wipspowers.fighter_leap_ground");
                }
                LEAPS.put(player.getUUID(), new Leap(player.getY()));
                return true;
            }
            if (l.phase == 1) {
                l.phase = 2;
                player.setDeltaMovement(player.getDeltaMovement().x * 0.3, -2.6, player.getDeltaMovement().z * 0.3);
                player.hurtMarked = true;
                PowerService.playAnimation(player, "gamma_leap");
                Fx.sound(player.level(), player.position(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 1.0F, 1.6F);
                return true;
            }
            return this.failSilently(player);
        }

        public static boolean leaping(ServerPlayer p) {
            return LEAPS.containsKey(p.getUUID());
        }

        static void tick(ServerPlayer p, Leap l) {
            l.ticks++;
            p.resetFallDistance();
            ServerLevel level = p.serverLevel();
            if (l.phase == 0) {
                if (l.ticks % 2 == 0) {
                    level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(), 4, 0.4, 0.0, 0.4, 0.02);
                }
                if (l.ticks >= 10) {
                    l.phase = 1;
                    Vec3 fwd = AimUtil.lookFlat(p);
                    p.setDeltaMovement(fwd.x * 0.6, 1.9, fwd.z * 0.6);
                    p.hurtMarked = true;
                    PowerService.playAnimation(p, "gamma_leap");
                    Fx.sound(level, p.position(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8F, 1.5F);
                    level.sendParticles(ParticleTypes.EXPLOSION, p.getX(), p.getY(), p.getZ(), 2, 0.3, 0.0, 0.3, 0);
                }
                return;
            }
            l.maxY = Math.max(l.maxY, p.getY());
            if (l.phase == 2) {
                level.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 0.5, p.getZ(), 3, 0.2, 0.4, 0.2, 0.01);
            }
            if (l.ticks > 12 && p.onGround()) {
                LEAPS.remove(p.getUUID());
                if (l.phase == 2) {
                    land(p, l.maxY - p.getY());
                }
                INSTANCE.startCooldown(p, 220);
                return;
            }
            if (l.ticks > 400 || p.isInWater()) { // nunca queda colgado
                LEAPS.remove(p.getUUID());
                INSTANCE.startCooldown(p, 220);
            }
        }

        static ImpactLeap INSTANCE;

        {
            INSTANCE = this;
        }

        static void land(ServerPlayer p, double height) {
            ServerLevel level = p.serverLevel();
            float base = Math.min(CAP, BASE + PER_BLOCK * (float) Math.max(0, height));
            lastHeight = height;
            lastBase = base;
            double radius = Math.min(9.0, 3.5 + height * 0.12);
            for (LivingEntity e : PowerKit.enemies(p, level, p.position(), radius, 40)) {
                e.invulnerableTime = 0;
                if (PowerKit.hit(p, e, base)) {
                    Vec3 away = e.position().subtract(p.position()).normalize();
                    e.push(away.x * 1.0, 0.5, away.z * 1.0);
                    e.hurtMarked = true;
                }
            }
            for (int ring = 1; ring <= 3; ring++) {
                double r = radius * ring / 3.0;
                for (int i = 0; i < 32; i++) {
                    double a = i * (Math.PI * 2 / 32);
                    level.sendParticles(ParticleTypes.CLOUD, p.getX() + Math.cos(a) * r, p.getY() + 0.2, p.getZ() + Math.sin(a) * r, 0,
                            Math.cos(a), 0.1, Math.sin(a), 0.3);
                }
            }
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, p.getX(), p.getY(), p.getZ(), 1, 0, 0, 0, 0);
            Fx.sound(level, p.position(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 2.0F, 0.7F);
            Fx.shake(level, p.position(), 24.0, 0.3F, 5, 10);
            PowerService.playAnimation(p, "gamma_leap_land");
        }

        @Override
        public void clearPlayer(ServerPlayer player) {
            LEAPS.remove(player.getUUID());
            super.clearPlayer(player);
        }
    }

    // =====================================================================================================
    // 5. GOLPE SERIO: definitiva mantenida. Sin cargar ya es una onda enorme hacia adelante; mientras se mantiene
    //    crece en tamano y dano (hasta 5 s, con carga visible). A carga maxima, ademas, destruye de forma PERMANENTE
    //    todos los bloques de un enorme volumen frontal (WipsDestruction, el sistema de Ruin y Graviton).
    // =====================================================================================================
    public static final class SeriousPunch extends FighterAbility {
        public static final int MAX_TICKS = 100;
        public static final float BASE = 10.0F;
        private final Map<UUID, int[]> holds = this.activeState();
        /** Autotest. */
        public static int lastBlocks;
        public static int lastTargets;

        public SeriousPunch() {
            super("fighter_serious_punch", 80.0F, 10);
        }

        @Override
        public String activationAnimation(ServerPlayer player) {
            return null;
        }

        @Override
        public boolean activate(ServerPlayer player) {
            if (this.holds.containsKey(player.getUUID())) {
                return false;
            }
            this.holds.put(player.getUUID(), new int[]{0});
            PowerService.playAnimation(player, "impact_strike_charge", true);
            return true;
        }

        @Override
        public void serverTick(ServerPlayer player) {
            int[] t = this.holds.get(player.getUUID());
            if (t == null) {
                return;
            }
            if (!player.isAlive()) {
                this.holds.remove(player.getUUID());
                return;
            }
            int ticks = ++t[0];
            float charge = Math.min(1.0F, ticks / (float) MAX_TICKS);
            ServerLevel level = player.serverLevel();
            Vec3 hand = AimUtil.handPosition(player);
            if (ticks % 2 == 0) {
                level.sendParticles(ParticleTypes.FLAME, hand.x, hand.y, hand.z, 1 + (int) (charge * 6), 0.1 + charge * 0.4, 0.1 + charge * 0.4,
                        0.1 + charge * 0.4, 0.01);
                level.sendParticles(Fx.dust(ORANGE, 0.8F + charge * 2.0F), player.getX(), player.getY() + 1, player.getZ(), 2 + (int) (charge * 10),
                        0.6, 0.8, 0.6, 0.0);
            }
            if (ticks % 20 == 0) {
                Fx.sound(level, player.position(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.6F + charge, 0.5F + charge);
                Fx.shake(level, player.position(), 16.0, 0.05F + 0.15F * charge, 3, 4);
                player.displayClientMessage(Component.translatable("message.wipspowers.serious_charge", Math.round(charge * 100)), true);
            }
            if (ticks % 10 == 0 && !PowerService.charge(player, this, 1.0F)) {
                this.punch(player, charge);
                return;
            }
            if (ticks >= MAX_TICKS) {
                this.punch(player, 1.0F);
            }
        }

        @Override
        public void onRelease(ServerPlayer player) {
            int[] t = this.holds.get(player.getUUID());
            if (t != null) {
                this.punch(player, Math.min(1.0F, t[0] / (float) MAX_TICKS));
            }
        }

        void punch(ServerPlayer player, float charge) {
            if (this.holds.remove(player.getUUID()) == null) {
                return;
            }
            PowerService.stopAnimation(player);
            PowerService.playAnimation(player, "impact_strike");
            ServerLevel level = player.serverLevel();
            Vec3 dir = player.getLookAngle();
            Vec3 apex = player.getEyePosition().add(dir.scale(0.8));
            double reach = 18.0 + 30.0 * charge;
            double near = 2.5 + 2.0 * charge;
            double far = 6.0 + 10.0 * charge;
            float damage = BASE * (1.0F + 3.0F * charge);
            lastTargets = 0;
            for (LivingEntity e : AimUtil.livingInFrustum(player, apex, dir, reach, near, far)) {
                if (!PowerKit.enemy(player, e)) {
                    continue;
                }
                e.invulnerableTime = 0;
                if (PowerKit.hit(player, e, damage)) {
                    lastTargets++;
                    double k = 2.0 + 3.0 * charge;
                    e.push(dir.x * k, 0.5 + 0.5 * charge, dir.z * k);
                    e.hurtMarked = true;
                }
            }
            // la onda visible, mas grande cuanto mas carga
            var rnd = level.random;
            int n = (int) (120 + 500 * charge);
            for (int i = 0; i < n; i++) {
                double f = rnd.nextDouble() * reach;
                double w = near + (far - near) * (f / reach);
                double a = rnd.nextDouble() * Math.PI * 2;
                double r = Math.sqrt(rnd.nextDouble()) * w;
                Vec3 side = dir.cross(new Vec3(0, 1, 0));
                side = side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize();
                Vec3 up = side.cross(dir).normalize();
                Vec3 p = apex.add(dir.scale(f)).add(side.scale(Math.cos(a) * r)).add(up.scale(Math.sin(a) * r));
                level.sendParticles(i % 4 == 0 ? ParticleTypes.FLAME : ParticleTypes.CLOUD, p.x, p.y, p.z, 0, dir.x, dir.y, dir.z, 0.6 + charge);
            }
            Fx.sound(level, player.position(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 3.0F, 0.5F);
            Fx.sound(level, player.position(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 2.0F + charge, 0.6F);
            Fx.shake(level, player.position(), 48.0, 0.3F + 0.7F * charge, 8, 16);
            lastBlocks = 0;
            if (charge >= 0.999F) {
                lastBlocks = this.devastate(level, apex, dir);
            }
            this.startCooldown(player, 900);
        }

        /** A carga maxima: cilindro frontal enorme (radio 9, 48 bloques) de destruccion permanente, repartida en ticks. */
        private int devastate(ServerLevel level, Vec3 apex, Vec3 dir) {
            double radius = 9.0;
            double length = 48.0;
            Set<BlockPos> blocks = new HashSet<>();
            Vec3 axis = dir.normalize();
            for (double f = 1.0; f <= length; f += 0.7) {
                Vec3 c = apex.add(axis.scale(f));
                double r = Math.min(radius, 2.5 + f * 0.5);
                int R = Mth.ceil(r);
                BlockPos cb = BlockPos.containing(c);
                for (int dx = -R; dx <= R; dx++) {
                    for (int dy = -R; dy <= R; dy++) {
                        for (int dz = -R; dz <= R; dz++) {
                            Vec3 p = new Vec3(cb.getX() + dx + 0.5, cb.getY() + dy + 0.5, cb.getZ() + dz + 0.5);
                            Vec3 rel = p.subtract(apex);
                            double along = rel.dot(axis);
                            if (along < 0.5 || along > length) {
                                continue;
                            }
                            double perp = rel.subtract(axis.scale(along)).length();
                            if (perp <= Math.min(radius, 2.5 + along * 0.5)) {
                                blocks.add(cb.offset(dx, dy, dz));
                            }
                        }
                    }
                }
            }
            return WipsDestruction.destroy(level, blocks, 9);
        }
    }

    // ---------------- ticks, caidas y limpieza ----------------

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
            Grit.tick(p);
            int[] f = BurningFists.ACTIVE.get(p.getUUID());
            if (f != null) {
                BurningFists.tick(p, f);
            }
            ImpactLeap.Leap l = ImpactLeap.LEAPS.get(p.getUUID());
            if (l != null) {
                ImpactLeap.tick(p, l);
            }
        }
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && ImpactLeap.leaping(p)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        Grit.BAR.remove(id);
        Grit.LAST_SRC.remove(id);
        BurningFists.ACTIVE.remove(id);
        ImpactLeap.LEAPS.remove(id);
    }

    @SubscribeEvent
    public static void onDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            Grit.BAR.remove(p.getUUID());
            Grit.sync(p, 0);
            BurningFists.ACTIVE.remove(p.getUUID());
            ImpactLeap.LEAPS.remove(p.getUUID());
        }
    }
}
