package wips.powers.ice;

import com.awakenedmod.awakened.content.vfx.Vfx;
import com.awakenedmod.awakened.core.Fx;
import com.awakenedmod.awakened.core.Harm;
import com.awakenedmod.awakened.core.TickFreeze;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import wips.powers.WipsPowers;
import wips.powers.kit.PowerKit;

/**
 * Pasiva del Hielo: congelacion acumulativa (estilo Sejuani).
 *  - Cada impacto valido de una habilidad de Hielo suma 1 carga al objetivo (las cargas vencen a los 4 s sin golpes).
 *  - Con 3 cargas: congelado 1 s (implementacion propia: el mob deja de tickear con TickFreeze de Awakened y queda
 *    clavado en su posicion; un jugador queda clavado e impedido de atacar), dentro de un cubo de hielo visible, y
 *    recibe "Escarcha": dano periodico tipo veneno de la pasiva durante 4 s, independiente de nuevos golpes.
 *  - Mientras esta congelado no acumula cargas; al descongelarse empieza de 0. La Escarcha se renueva, no se apila.
 * La congelacion de 5 s de H5 usa el mismo congelamiento pero no la pasiva (sin cargas ni Escarcha).
 */
@EventBusSubscriber(modid = WipsPowers.MOD_ID)
public final class Frost {
    public static final int CHARGES_TO_FREEZE = 3;
    public static final int CHARGE_TIMEOUT = 80;
    public static final int PASSIVE_FREEZE = 20;
    public static final int DOT_TICKS = 80;
    public static final int DOT_EVERY = 10;
    public static final float DOT_DAMAGE = 1.2F;
    public static final int ICE = 0x9FE8FF;

    private static final class Marks {
        int charges;
        long expires;
    }

    private static final class Frozen {
        final LivingEntity target;
        final ServerLevel level;
        final Vec3 pin;
        long until;
        final boolean mob;
        net.minecraft.world.entity.Entity cube;

        Frozen(LivingEntity target, long until) {
            this.target = target;
            this.level = (ServerLevel) target.level();
            this.pin = target.position();
            this.until = until;
            this.mob = !(target instanceof Player);
        }
    }

    private static final class Dot {
        final LivingEntity target;
        final ServerPlayer owner;
        long until;
        long next;

        Dot(LivingEntity target, ServerPlayer owner) {
            this.target = target;
            this.owner = owner;
        }
    }

    private static final Map<UUID, Marks> MARKS = new HashMap<>();
    private static final Map<UUID, Frozen> FROZEN = new HashMap<>();
    private static final Map<UUID, Dot> DOTS = new HashMap<>();

    /** Autotest. */
    public static int freezes;
    public static int dotHits;

    private Frost() {
    }

    public static boolean isFrozen(LivingEntity e) {
        return FROZEN.containsKey(e.getUUID());
    }

    public static int charges(LivingEntity e) {
        Marks m = MARKS.get(e.getUUID());
        return m == null || m.expires < e.level().getGameTime() ? 0 : m.charges;
    }

    /** Impacto valido de Hielo: suma cargas; a las 3 congela 1 s y aplica Escarcha. */
    public static void charge(ServerPlayer owner, LivingEntity target, int amount) {
        if (!PowerKit.enemy(owner, target) || isFrozen(target)) {
            return;
        }
        long now = target.level().getGameTime();
        Marks m = MARKS.computeIfAbsent(target.getUUID(), k -> new Marks());
        if (m.expires < now) {
            m.charges = 0;
        }
        m.charges += amount;
        m.expires = now + CHARGE_TIMEOUT;
        ServerLevel level = (ServerLevel) target.level();
        Vec3 top = target.position().add(0, target.getBbHeight() + 0.4, 0);
        for (int i = 0; i < Math.min(m.charges, CHARGES_TO_FREEZE); i++) {
            double a = i * (Math.PI * 2 / 3) + now * 0.2;
            level.sendParticles(ParticleTypes.SNOWFLAKE, top.x + Math.cos(a) * 0.4, top.y, top.z + Math.sin(a) * 0.4, 2, 0.02, 0.02, 0.02, 0);
        }
        if (m.charges >= CHARGES_TO_FREEZE) {
            MARKS.remove(target.getUUID());
            freeze(owner, target, PASSIVE_FREEZE);
            dot(owner, target);
        }
    }

    /** Congela (implementacion propia). Si ya estaba congelado, extiende hasta el mayor de los dos. */
    public static void freeze(ServerPlayer owner, LivingEntity target, int ticks) {
        if (!PowerKit.enemy(owner, target)) {
            return;
        }
        long now = target.level().getGameTime();
        Frozen f = FROZEN.get(target.getUUID());
        ServerLevel level = (ServerLevel) target.level();
        if (f != null) {
            if (now + ticks > f.until) {
                f.until = now + ticks;
                if (f.cube != null) {
                    f.cube.discard();
                }
                f.cube = cube(level, target, ticks);
            }
            return;
        }
        f = new Frozen(target, now + ticks);
        FROZEN.put(target.getUUID(), f);
        MARKS.remove(target.getUUID());
        if (f.mob) {
            TickFreeze.freeze(target, true);
        } else {
            Harm.effect(owner, target, new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 250, false, false));
        }
        target.setDeltaMovement(Vec3.ZERO);
        target.hurtMarked = true;
        target.setTicksFrozen(Math.max(target.getTicksFrozen(), target.getTicksRequiredToFreeze() + ticks));
        f.cube = cube(level, target, ticks);
        Fx.sound(level, target.position(), SoundEvents.GLASS_PLACE, SoundSource.PLAYERS, 1.2F, 0.6F);
        Fx.sound(level, target.position(), SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, 1.0F, 0.8F);
        freezes++;
    }

    /** Cubo de hielo visible (entidad de VFX de Awakened, sin colision) del tamano del objetivo. */
    private static net.minecraft.world.entity.Entity cube(ServerLevel level, LivingEntity target, int ticks) {
        double w = target.getBbWidth() + 0.35;
        double h = target.getBbHeight() + 0.3;
        level.sendParticles(ParticleTypes.SNOWFLAKE, target.getX(), target.getY() + h / 2, target.getZ(), 20, w * 0.5, h * 0.4, w * 0.5, 0.02);
        return Vfx.blockTracked(level, target.position().add(0, target.getBbHeight() / 2.0, 0), Blocks.ICE, w, h, w, ticks, 0.0F);
    }

    private static void dot(ServerPlayer owner, LivingEntity target) {
        Dot d = DOTS.computeIfAbsent(target.getUUID(), k -> new Dot(target, owner));
        long now = target.level().getGameTime();
        d.until = now + DOT_TICKS; // se renueva, no se apila
        if (d.next < now) {
            d.next = now + DOT_EVERY;
        }
    }

    private static void thaw(Frozen f) {
        if (f.cube != null && !f.cube.isRemoved()) {
            f.cube.discard();
        }
        if (f.mob) {
            TickFreeze.release(f.target, true);
        } else {
            MobEffectInstance lock = f.target.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
            if (lock != null && lock.getAmplifier() == 250) {
                f.target.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            }
        }
        if (f.target.isAlive()) {
            f.level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, Blocks.ICE.defaultBlockState()),
                    f.target.getX(), f.target.getY() + f.target.getBbHeight() / 2, f.target.getZ(), 24, 0.4, 0.5, 0.4, 0.1);
            Fx.sound(f.level, f.target.position(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0F, 1.2F);
        }
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (!FROZEN.isEmpty()) {
            Iterator<Frozen> it = FROZEN.values().iterator();
            while (it.hasNext()) {
                Frozen f = it.next();
                boolean gone = !f.target.isAlive() || f.target.isRemoved() || f.target.level() != f.level;
                if (gone || f.level.getGameTime() >= f.until) {
                    thaw(f);
                    it.remove();
                    continue;
                }
                f.target.setDeltaMovement(Vec3.ZERO);
                if (f.target.position().distanceToSqr(f.pin) > 1.0E-4) {
                    if (f.target instanceof ServerPlayer sp) {
                        sp.teleportTo(f.pin.x, f.pin.y, f.pin.z);
                    } else {
                        f.target.setPos(f.pin.x, f.pin.y, f.pin.z);
                    }
                }
                f.target.resetFallDistance();
                f.target.hurtMarked = true;
            }
        }
        if (!DOTS.isEmpty()) {
            Iterator<Dot> it = DOTS.values().iterator();
            while (it.hasNext()) {
                Dot d = it.next();
                long now = d.target.level().getGameTime();
                if (!d.target.isAlive() || d.target.isRemoved() || now > d.until || d.owner.isRemoved()) {
                    it.remove();
                    continue;
                }
                if (now >= d.next) {
                    d.next = now + DOT_EVERY;
                    d.target.invulnerableTime = 0; // el veno no compite con el cooldown de golpe
                    if (PowerKit.hit(d.owner, d.target, DOT_DAMAGE)) {
                        dotHits++;
                    }
                    ((ServerLevel) d.target.level()).sendParticles(ParticleTypes.SNOWFLAKE, d.target.getX(), d.target.getY() + d.target.getBbHeight() * 0.6,
                            d.target.getZ(), 4, 0.3, 0.3, 0.3, 0.01);
                }
            }
        }
        if (!MARKS.isEmpty() && event.getServer().getTickCount() % 100 == 0) {
            long now = event.getServer().overworld().getGameTime();
            MARKS.values().removeIf(m -> m.expires < now - 200);
        }
    }

    /** Un congelado no puede pegar (mobs ya no tickean; esto cubre a los jugadores y a golpes en curso). */
    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof LivingEntity attacker && event.getSource().getDirectEntity() == attacker
                && FROZEN.containsKey(attacker.getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        FROZEN.values().forEach(Frost::thaw);
        FROZEN.clear();
        DOTS.clear();
        MARKS.clear();
    }
}
