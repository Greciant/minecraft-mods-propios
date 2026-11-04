package wips.powers.graviton;

import com.awakenedmod.awakened.content.graviton.GravitySupport;
import com.awakenedmod.awakened.core.Harm;
import com.awakenedmod.awakened.core.power.Ability;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import wips.powers.WipsPowers;
import wips.powers.fx.WipsFx;

/**
 * Graviton ranura 5: Chibaku Tensei (Devastacion Planetaria).
 * 1) Lanza una singularidad que sube despacio. 2) Arriba atrae todo en un radio enorme.
 * 3) Lo atrapado queda comprimido en un planetoide alrededor del nucleo con dano continuo.
 * 4) Implosion: el planetoide se rompe, todo cae y al tocar el suelo hay una gran onda expansiva.
 */
@EventBusSubscriber(modid = WipsPowers.MOD_ID)
public final class ChibakuTensei extends Ability {
    public static final String ID = "graviton_chibaku_tensei";
    private static final float COST = 80.0F;
    private static final int UNLOCK = 10;
    private static final int COOLDOWN = 900;

    // ---- tiempos (ticks) ----
    static final int RISE = 60;
    static final int PULL_FROM = 30;
    static final int IMPLODE = 210;
    static final int FALL = 16;
    // ---- tamanos ----
    static final double RISE_HEIGHT = 14.0;
    static final double PULL_RADIUS = 22.0;
    static final double SHELL = 3.2;
    private static final float CRUSH_DAMAGE = 3.0F;
    private static final int CRUSH_EVERY = 10;
    private static final float IMPLODE_DAMAGE = 8.0F;
    private static final float SLAM_DAMAGE = 10.0F;
    private static final double SLAM_RADIUS = 9.0;

    private static final List<Cast> CASTS = new CopyOnWriteArrayList<>();

    private static final class Cast {
        final ServerPlayer caster;
        final ServerLevel level;
        final Vec3 origin;
        final long seed;
        final Map<UUID, Vec3> captured = new LinkedHashMap<>();
        int age;
        Vec3 ground;

        Cast(ServerPlayer caster, Vec3 origin, long seed) {
            this.caster = caster;
            this.level = caster.serverLevel();
            this.origin = origin;
            this.seed = seed;
        }

        Vec3 core() {
            float u = Mth.clamp((float) this.age / RISE, 0f, 1f);
            float eased = 1f - (1f - u) * (1f - u) * (1f - u);
            return this.origin.add(0.0, RISE_HEIGHT * eased, 0.0);
        }
    }

    public ChibakuTensei() {
        super(ID, COST, UNLOCK);
    }

    @Override
    public Component displayName() {
        return Component.translatable("ability.wipspowers." + ID);
    }

    @Override
    public Component description() {
        return Component.translatable("ability.wipspowers." + ID + ".desc");
    }

    @Override
    public String activationAnimation(ServerPlayer player) {
        return null;
    }

    @Override
    public boolean activate(ServerPlayer player) {
        for (Cast c : CASTS) {
            if (c.caster == player) {
                return this.fail(player, "ability.wipspowers.graviton_chibaku_tensei.busy");
            }
        }
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
        Vec3 origin = player.getEyePosition().add(flat.scale(3.0));
        long seed = player.getRandom().nextLong();
        Cast cast = new Cast(player, origin, seed);
        CASTS.add(cast);
        ServerLevel level = player.serverLevel();
        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 2.0F, 0.5F);
        WipsFx.at(level, player.getId(), "chibaku", origin, Vec3.ZERO, (float) PULL_RADIUS, seed, IMPLODE + FALL + 40);
        this.startCooldown(player, COOLDOWN);
        return true;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (CASTS.isEmpty()) {
            return;
        }
        Iterator<Cast> it = CASTS.iterator();
        List<Cast> done = new ArrayList<>();
        while (it.hasNext()) {
            Cast c = it.next();
            if (c.level.getServer() != event.getServer()) {
                continue;
            }
            if (!tick(c)) {
                done.add(c);
            }
        }
        CASTS.removeAll(done);
    }

    /** @return false cuando termino */
    private static boolean tick(Cast c) {
        c.age++;
        ServerLevel level = c.level;
        Vec3 core = c.core();
        if (c.age < IMPLODE) {
            if (c.age % 20 == 0) {
                level.playSound(null, core.x, core.y, core.z, SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 3.0F, 0.4F);
            }
            if (c.age >= PULL_FROM) {
                pull(c, core);
            }
            hold(c, core);
            return true;
        }
        if (c.age == IMPLODE) {
            implode(c, core);
            return true;
        }
        if (c.age == IMPLODE + FALL) {
            slam(c);
            return false;
        }
        return c.age < IMPLODE + FALL;
    }

    private static List<LivingEntity> targets(Cast c, Vec3 core, double radius) {
        List<LivingEntity> near = c.level.getEntitiesOfClass(LivingEntity.class, new AABB(core, core).inflate(radius),
                e -> e != c.caster && e.isAlive() && !e.isSpectator() && e.distanceToSqr(core) <= radius * radius);
        return Harm.only(c.caster, new ArrayList<>(near));
    }

    /** Atraccion: mas fuerte cerca del nucleo; al llegar a la capa queda atrapado. */
    private static void pull(Cast c, Vec3 core) {
        float ramp = Mth.clamp((c.age - PULL_FROM) / 40f, 0f, 1f);
        for (LivingEntity e : targets(c, core, PULL_RADIUS)) {
            if (c.captured.containsKey(e.getUUID())) {
                continue;
            }
            Vec3 to = core.subtract(e.getBoundingBox().getCenter());
            double d = to.length();
            if (d <= SHELL + 0.6) {
                Vec3 off = d < 1.0E-3 ? new Vec3(0, SHELL, 0) : to.normalize().scale(-SHELL);
                c.captured.put(e.getUUID(), off);
                continue;
            }
            double strength = (0.06 + 0.16 * (1.0 - d / PULL_RADIUS)) * ramp;
            Vec3 v = e.getDeltaMovement().add(to.normalize().scale(strength));
            double max = 1.3;
            if (v.lengthSqr() > max * max) {
                v = v.normalize().scale(max);
            }
            e.setDeltaMovement(v);
            e.hurtMarked = true;
            e.fallDistance = 0;
        }
    }

    /** Planetoide: lo atrapado queda pegado a la capa, girando lento, con dano por aplastamiento. */
    private static void hold(Cast c, Vec3 core) {
        if (c.captured.isEmpty()) {
            return;
        }
        double spin = 0.02;
        double cs = Math.cos(spin);
        double sn = Math.sin(spin);
        Iterator<Map.Entry<UUID, Vec3>> it = c.captured.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Vec3> en = it.next();
            LivingEntity e = c.level.getEntity(en.getKey()) instanceof LivingEntity le ? le : null;
            if (e == null || !e.isAlive()) {
                it.remove();
                continue;
            }
            Vec3 o = en.getValue();
            o = new Vec3(o.x * cs - o.z * sn, o.y, o.x * sn + o.z * cs);
            en.setValue(o);
            Vec3 at = core.add(o).subtract(0.0, e.getBbHeight() * 0.5, 0.0);
            e.setDeltaMovement(Vec3.ZERO);
            e.fallDistance = 0;
            if (e instanceof ServerPlayer sp) {
                sp.teleportTo(c.level, at.x, at.y, at.z, sp.getYRot(), sp.getXRot());
            } else {
                e.moveTo(at.x, at.y, at.z, e.getYRot(), e.getXRot());
                if (e instanceof Mob mob) {
                    mob.getNavigation().stop();
                }
            }
            if (c.age % CRUSH_EVERY == 0) {
                GravitySupport.crush(c.level, c.caster, e, CRUSH_DAMAGE);
            }
        }
    }

    private static void implode(Cast c, Vec3 core) {
        ServerLevel level = c.level;
        level.playSound(null, core.x, core.y, core.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 4.0F, 0.5F);
        level.playSound(null, core.x, core.y, core.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 3.0F, 0.6F);
        for (Map.Entry<UUID, Vec3> en : c.captured.entrySet()) {
            if (level.getEntity(en.getKey()) instanceof LivingEntity e && e.isAlive()) {
                GravitySupport.crush(level, c.caster, e, IMPLODE_DAMAGE);
                Vec3 out = en.getValue().normalize();
                e.setDeltaMovement(out.x * 0.35, -1.4, out.z * 0.35);
                e.hurtMarked = true;
            }
        }
        // el punto del suelo bajo el nucleo, para la onda final
        int gy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(core.x), Mth.floor(core.z));
        c.ground = new Vec3(core.x, Math.min(gy, core.y), core.z);
        WipsFx.at(level, c.caster.getId(), "chibaku_implode", core, c.ground.subtract(core), (float) SHELL, c.seed, FALL);
    }

    private static void slam(Cast c) {
        ServerLevel level = c.level;
        Vec3 g = c.ground;
        level.playSound(null, g.x, g.y, g.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 5.0F, 0.35F);
        for (LivingEntity e : targets(c, g, SLAM_RADIUS)) {
            double t = Math.sqrt(e.distanceToSqr(g)) / SLAM_RADIUS;
            GravitySupport.crush(level, c.caster, e, (float) (SLAM_DAMAGE * (1.0 - 0.6 * t)));
            GravitySupport.shove(e, g, 1.6 * (1.0 - 0.5 * t), 0.6);
        }
        WipsFx.at(level, c.caster.getId(), "chibaku_slam", g, Vec3.ZERO, (float) SLAM_RADIUS, c.seed, 0);
    }

    @Override
    public void clearPlayer(ServerPlayer player) {
        CASTS.removeIf(c -> c.caster == player && c.age < PULL_FROM);
        super.clearPlayer(player);
    }
}
