package wips.powers.graviton;

import com.awakenedmod.awakened.core.Harm;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import wips.powers.WipsPowers;
import wips.powers.fx.WipsFx;

/**
 * Graviton ranura 4 (Dense): mientras se mantiene, un area grande de presion alrededor del usuario.
 * Quien este dentro acumula exposicion tick a tick y se ralentiza hasta quedar inmovil.
 * Al salir, la exposicion baja de a poco (no se libera de golpe).
 */
@EventBusSubscriber(modid = WipsPowers.MOD_ID)
public final class PressureField {
    /** Radio del area: empieza en RADIUS_MIN y crece hasta RADIUS (bloques) mientras se mantiene. */
    public static final double RADIUS = 48.0;
    private static final double RADIUS_MIN = 6.0;
    /** Ticks de mantener hasta alcanzar el radio y la fuerza maxima (160 = 8 s; con la stamina base de Awakened se aguanta unos 10 s). */
    private static final int GROW_TICKS = 160;
    /** Alto minimo del area; crece con el radio (un tercio). */
    private static final double HEIGHT = 5.0;
    /** Exposicion necesaria para quedar inmovil; se acumula mas rapido cuanto mas tiempo lleva mantenido. */
    private static final int IMMOBILIZE_TICKS = 60;
    /** Ralentizado inmediato al entrar al area (antes de acumular exposicion). */
    private static final double BASE_SLOW = 0.2;
    /** Cuanto baja la exposicion por tick fuera del area. */
    private static final int DECAY = 3;
    /** Cada cuantos ticks se refresca el efecto visual en los clientes. */
    private static final int VFX_EVERY = 8;

    private static final ResourceLocation SLOW_ID = ResourceLocation.fromNamespaceAndPath(WipsPowers.MOD_ID, "pressure_slow");
    private static final ResourceLocation JUMP_ID = ResourceLocation.fromNamespaceAndPath(WipsPowers.MOD_ID, "pressure_jump");
    private static final ResourceLocation FLY_ID = ResourceLocation.fromNamespaceAndPath(WipsPowers.MOD_ID, "pressure_fly");

    private static final Map<UUID, Integer> ACTIVE = new ConcurrentHashMap<>();
    private static final Map<UUID, Exposure> EXPOSED = new ConcurrentHashMap<>();

    private static final class Exposure {
        final ServerLevel level;
        final UUID id;
        int ticks;
        long lastSeen;

        Exposure(ServerLevel level, UUID id) {
            this.level = level;
            this.id = id;
        }
    }

    private PressureField() {
    }

    public static void start(ServerPlayer caster) {
        ACTIVE.put(caster.getUUID(), 0);
        WipsFx.around(caster, "pressure", caster.position(), Vec3.ZERO, (float) RADIUS_MIN, caster.getUUID().getLeastSignificantBits(), VFX_EVERY + 6);
    }

    /** Fraccion de crecimiento segun el tiempo mantenido (0..1). */
    private static double grow(int age) {
        return Math.min(1.0, (double) age / GROW_TICKS);
    }

    public static double radius(int age) {
        return RADIUS_MIN + (RADIUS - RADIUS_MIN) * grow(age);
    }

    public static void stop(ServerPlayer caster) {
        if (ACTIVE.remove(caster.getUUID()) != null) {
            WipsFx.around(caster, "pressure", caster.position(), Vec3.ZERO, (float) RADIUS, 0L, 0);
        }
    }

    public static boolean active(ServerPlayer caster) {
        return ACTIVE.containsKey(caster.getUUID());
    }

    /** Lo llama DenseAbility.serverTick (mixin) mientras se mantiene la tecnica. */
    public static void tick(ServerPlayer caster) {
        Integer age = ACTIVE.computeIfPresent(caster.getUUID(), (k, v) -> v + 1);
        if (age == null) {
            return;
        }
        ServerLevel level = caster.serverLevel();
        long now = level.getGameTime();
        Vec3 c = caster.position();
        double grow = grow(age);
        double R = radius(age);
        double H = Math.max(HEIGHT, R / 3.0);
        // cuanto mas tiempo mantenido, mas exposicion por tick (1x al inicio, 3x a los 8 s)
        int gain = 1 + (int) Math.round(2.0 * grow);
        List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class,
                new AABB(c.x - R, c.y - H, c.z - R, c.x + R, c.y + H, c.z + R),
                e -> e != caster && e.isAlive() && !e.isSpectator() && horiz2(e.position(), c) <= R * R);
        for (LivingEntity e : Harm.only(caster, new ArrayList<>(near))) {
            Exposure ex = EXPOSED.computeIfAbsent(e.getUUID(), k -> new Exposure(level, k));
            ex.ticks = Math.min(IMMOBILIZE_TICKS, ex.ticks + gain);
            ex.lastSeen = now;
            apply(e, ex.ticks);
        }
        if (age % VFX_EVERY == 0) {
            WipsFx.around(caster, "pressure", c, Vec3.ZERO, (float) R, caster.getUUID().getLeastSignificantBits(), VFX_EVERY + 6);
        }
    }

    /** Fuera del area: la exposicion se disipa y se quitan los modificadores al llegar a cero. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (EXPOSED.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        Iterator<Exposure> it = EXPOSED.values().iterator();
        while (it.hasNext()) {
            Exposure ex = it.next();
            if (ex.level.getServer() != server) {
                continue;
            }
            if (ex.level.getGameTime() - ex.lastSeen <= 1) {
                continue;
            }
            ex.ticks -= DECAY;
            LivingEntity e = ex.level.getEntity(ex.id) instanceof LivingEntity le ? le : null;
            if (ex.ticks <= 0 || e == null || !e.isAlive()) {
                if (e != null) {
                    clear(e);
                }
                it.remove();
            } else {
                apply(e, ex.ticks);
            }
        }
    }

    private static void apply(LivingEntity e, int ticks) {
        double f = Math.min(1.0, (double) ticks / IMMOBILIZE_TICKS);
        // se nota desde el primer tick (BASE_SLOW) y aplasta hasta inmovilizar
        double slow = Math.min(1.0, BASE_SLOW + (1.0 - BASE_SLOW) * f);
        // efecto de lentitud visible (iconito y particulas), nivel segun la fuerza
        int amp = (int) Math.min(5, Math.floor(slow * 6));
        e.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 12, amp, false, true, true));
        set(e, Attributes.MOVEMENT_SPEED, SLOW_ID, -slow);
        set(e, Attributes.JUMP_STRENGTH, JUMP_ID, -slow);
        set(e, Attributes.FLYING_SPEED, FLY_ID, -slow);
        if (!(e instanceof ServerPlayer)) {
            Vec3 v = e.getDeltaMovement();
            double keep = 1.0 - slow;
            e.setDeltaMovement(v.x * keep, v.y > 0 ? v.y * keep : v.y, v.z * keep);
            if (f >= 1.0 && e instanceof Mob mob) {
                mob.getNavigation().stop();
            }
        } else if (f >= 1.0) {
            Vec3 v = e.getDeltaMovement();
            e.setDeltaMovement(0.0, Math.min(0.0, v.y), 0.0);
            e.hurtMarked = true;
        }
    }

    private static void clear(LivingEntity e) {
        set(e, Attributes.MOVEMENT_SPEED, SLOW_ID, 0.0);
        set(e, Attributes.JUMP_STRENGTH, JUMP_ID, 0.0);
        set(e, Attributes.FLYING_SPEED, FLY_ID, 0.0);
    }

    private static void set(LivingEntity e, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr,
                            ResourceLocation id, double amount) {
        AttributeInstance inst = e.getAttribute(attr);
        if (inst == null) {
            return;
        }
        inst.removeModifier(id);
        if (Math.abs(amount) > 1.0E-4) {
            inst.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    private static double horiz2(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return dx * dx + dz * dz;
    }
}
