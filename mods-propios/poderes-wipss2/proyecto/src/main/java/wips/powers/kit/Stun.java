package wips.powers.kit;

import com.awakenedmod.awakened.core.Harm;
import com.awakenedmod.awakened.core.TickFreeze;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import wips.powers.WipsPowers;

/**
 * Aturdimiento / inmovilizacion de los poderes nuevos (pelota de slime, baba pegajosa, Luchador): el objetivo queda
 * clavado en su lugar, sin poder pegar. Los mobs ademas dejan de pensar (TickFreeze suave de Awakened, que deja
 * correr el cooldown de golpe para que el dano periodico siga entrando). Se renueva, no se apila.
 */
@EventBusSubscriber(modid = WipsPowers.MOD_ID)
public final class Stun {
    private static final class Held {
        final LivingEntity target;
        final ServerLevel level;
        Vec3 pin;
        long until;
        final boolean mob;
        final boolean allowFall;

        Held(LivingEntity t, long until, boolean allowFall) {
            this.target = t;
            this.level = (ServerLevel) t.level();
            this.pin = t.position();
            this.until = until;
            this.mob = !(t instanceof Player);
            this.allowFall = allowFall;
        }
    }

    private static final Map<UUID, Held> HELD = new HashMap<>();
    /** Autotest. */
    public static int applied;

    private Stun() {
    }

    public static boolean stunned(LivingEntity e) {
        return HELD.containsKey(e.getUUID());
    }

    /** Aturde ticks. allowFall: si puede seguir cayendo (pegado al suelo) o queda flotando donde esta. */
    public static void apply(ServerPlayer owner, LivingEntity target, int ticks, boolean allowFall) {
        if (!PowerKit.enemy(owner, target)) {
            return;
        }
        long until = target.level().getGameTime() + ticks;
        Held h = HELD.get(target.getUUID());
        if (h != null) {
            h.until = Math.max(h.until, until);
            return;
        }
        h = new Held(target, until, allowFall);
        HELD.put(target.getUUID(), h);
        if (h.mob) {
            TickFreeze.freeze(target, true);
        } else {
            Harm.effect(owner, target, new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 250, false, false));
        }
        applied++;
    }

    private static void release(Held h) {
        if (h.mob) {
            TickFreeze.release(h.target, true);
        } else {
            MobEffectInstance lock = h.target.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
            if (lock != null && lock.getAmplifier() == 250) {
                h.target.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            }
        }
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (HELD.isEmpty()) {
            return;
        }
        Iterator<Held> it = HELD.values().iterator();
        while (it.hasNext()) {
            Held h = it.next();
            if (!h.target.isAlive() || h.target.isRemoved() || h.target.level() != h.level || h.level.getGameTime() >= h.until) {
                release(h);
                it.remove();
                continue;
            }
            Vec3 p = h.target.position();
            double y = h.allowFall ? Math.min(p.y, h.pin.y) : h.pin.y;
            if (h.allowFall && !h.target.onGround() && h.mob) {
                y = p.y - 0.25; // los mobs congelados no caen solos: bajarlos hasta el suelo
                if (!h.level.noCollision(h.target, h.target.getBoundingBox().move(0, y - p.y, 0))) {
                    y = p.y;
                }
                h.pin = new Vec3(h.pin.x, y, h.pin.z);
            }
            if (Math.abs(p.x - h.pin.x) + Math.abs(p.z - h.pin.z) + Math.abs(p.y - y) > 1.0E-3) {
                if (h.target instanceof ServerPlayer sp) {
                    sp.teleportTo(h.pin.x, y, h.pin.z);
                } else {
                    h.target.setPos(h.pin.x, y, h.pin.z);
                }
            }
            h.target.setDeltaMovement(Vec3.ZERO);
            h.target.hurtMarked = true;
            if (h.level.getGameTime() % 8 == 0) {
                h.level.sendParticles(ParticleTypes.CRIT, h.target.getX(), h.target.getY() + h.target.getBbHeight() + 0.3, h.target.getZ(),
                        3, 0.25, 0.05, 0.25, 0.0);
            }
        }
    }

    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof LivingEntity attacker && event.getSource().getDirectEntity() == attacker
                && HELD.containsKey(attacker.getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        HELD.values().forEach(Stun::release);
        HELD.clear();
    }
}
