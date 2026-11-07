package wips.powers.graviton;

import com.awakenedmod.awakened.core.Dmg;
import com.awakenedmod.awakened.core.Harm;
import com.awakenedmod.awakened.core.power.Ability;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import wips.powers.WipsPowers;
import wips.powers.combat.WipsDamage;
import wips.powers.terrain.WipsDestruction;

/**
 * Graviton ranura 5: SHINRA TENSEI. El jugador se eleva un instante y libera una onda de repulsion esferica con el
 * como epicentro, en todas direcciones: todo lo alcanzado sale despedido hacia afuera con dano, y los bloques del
 * radio se destruyen de forma PERMANENTE (WipsDestruction, el mismo sistema que Ruin). La onda crece unos bloques
 * por tick, con polvo que se expande hacia afuera en cantidad proporcional al radio.
 */
@EventBusSubscriber(modid = WipsPowers.MOD_ID)
public final class ShinraTensei extends Ability {
    public static final String ID = "graviton_shinra_tensei";
    private static final float COST = 80.0F;
    private static final int UNLOCK = 10;
    private static final int COOLDOWN = 700;

    static final int LIFT = 12;
    static final double RADIUS = 20.0;
    static final double GROWTH = 2.5;
    static final float DAMAGE = 16.0F;
    static final double PUSH = 3.2;
    static final int FALL_GUARD = 100;

    private static final List<Cast> CASTS = new CopyOnWriteArrayList<>();

    static final class Cast {
        final ServerPlayer caster;
        final ServerLevel level;
        final Set<UUID> hit = new HashSet<>();
        int age;
        Vec3 centre;
        double radius;
        int blocks;

        Cast(ServerPlayer caster) {
            this.caster = caster;
            this.level = caster.serverLevel();
        }
    }

    public ShinraTensei() {
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
                return this.failSilently(player);
            }
        }
        CASTS.add(new Cast(player));
        ServerLevel level = player.serverLevel();
        player.setDeltaMovement(player.getDeltaMovement().x * 0.2, 0.75, player.getDeltaMovement().z * 0.2);
        player.hurtMarked = true;
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.6F, 0.5F);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1, player.getZ(), 60, 0.6, 1.0, 0.6, 0.3);
        this.startCooldown(player, COOLDOWN);
        return true;
    }

    /** Para el autotest: ultima cantidad de bloques agendados por un lanzamiento. */
    public static int lastBlocks;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (CASTS.isEmpty()) {
            return;
        }
        List<Cast> done = new ArrayList<>();
        for (Cast c : CASTS) {
            if (c.level.getServer() != event.getServer()) {
                continue;
            }
            if (!tick(c)) {
                done.add(c);
            }
        }
        CASTS.removeAll(done);
    }

    private static boolean tick(Cast c) {
        ServerPlayer p = c.caster;
        if (p.isRemoved() || p.level() != c.level) {
            return false;
        }
        c.age++;
        if (c.age <= FALL_GUARD) {
            p.fallDistance = 0.0F;
        }
        if (!p.isAlive()) {
            return false;
        }
        if (c.age < LIFT) {
            // subida y frenado: queda flotando un instante en lo alto
            if (c.age > LIFT / 2) {
                p.setDeltaMovement(p.getDeltaMovement().multiply(0.3, 0.3, 0.3));
                p.hurtMarked = true;
            }
            c.level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 6, 0.4, 0.6, 0.4, 0.02);
            return true;
        }
        if (c.centre == null) {
            c.centre = p.position().add(0, 1.0, 0);
            c.radius = 1.0;
            c.level.playSound(null, c.centre.x, c.centre.y, c.centre.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 3.0F, 0.6F);
            c.level.playSound(null, c.centre.x, c.centre.y, c.centre.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 3.0F, 0.5F);
            c.level.sendParticles(ParticleTypes.FLASH, c.centre.x, c.centre.y, c.centre.z, 1, 0, 0, 0, 0);
        }
        if (c.age < LIFT + 6) {
            p.setDeltaMovement(Vec3.ZERO);
            p.hurtMarked = true;
        }
        if (c.radius >= RADIUS) {
            if (c.radius < RADIUS + 1) {
                lastBlocks = c.blocks;
                c.radius += 10; // marca de fin, el guardia de caida sigue
            }
            return c.age <= FALL_GUARD;
        }
        double r0 = c.radius;
        double r1 = Math.min(RADIUS, c.radius + GROWTH);
        c.radius = r1;
        shell(c, r0, r1);
        push(c, r1);
        dust(c, r1);
        return true;
    }

    /** Cascara esferica r0 <= d < r1: a la cola de destruccion permanente. */
    private static void shell(Cast c, double r0, double r1) {
        List<BlockPos> list = new ArrayList<>();
        BlockPos o = BlockPos.containing(c.centre);
        int R = Mth.ceil(r1);
        double a = r0 * r0;
        double b = r1 * r1;
        for (int dx = -R; dx <= R; dx++) {
            for (int dy = -R; dy <= R; dy++) {
                for (int dz = -R; dz <= R; dz++) {
                    double d = dx * dx + dy * dy + dz * dz;
                    if (d >= a && d < b) {
                        list.add(o.offset(dx, dy, dz));
                    }
                }
            }
        }
        c.blocks += WipsDestruction.destroy(c.level, list, 7);
    }

    private static void push(Cast c, double r1) {
        AABB box = new AABB(c.centre, c.centre).inflate(r1);
        List<Entity> near = c.level.getEntities(c.caster, box, e -> e.isAlive() && !e.isSpectator()
                && e.position().distanceToSqr(c.centre) <= r1 * r1);
        for (Entity e : near) {
            if (!c.hit.add(e.getUUID()) || !Harm.allowed(c.caster, e)) {
                continue;
            }
            Vec3 away = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(c.centre);
            double dist = Math.max(0.5, away.length());
            Vec3 dir = away.normalize();
            double falloff = 1.0 - 0.5 * Math.min(1.0, dist / RADIUS);
            Harm.push(c.caster, e, dir.x * PUSH * falloff, 0.45 + Math.max(0, dir.y) * PUSH * 0.5 * falloff, dir.z * PUSH * falloff);
            if (e instanceof LivingEntity le) {
                Dmg.hurt(le, c.level, Dmg.by(c.caster, WipsDamage.ABILITY), (float) (DAMAGE * falloff));
            }
        }
    }

    /** Polvo que sale hacia afuera; mas particulas cuanto mas grande el anillo. */
    private static void dust(Cast c, double r) {
        int n = Mth.clamp((int) (r * 10), 20, 220);
        var rnd = c.level.random;
        for (int i = 0; i < n; i++) {
            double th = rnd.nextDouble() * Math.PI * 2;
            double ph = (rnd.nextDouble() - 0.35) * Math.PI * 0.6; // mas cerca del suelo que del cielo
            Vec3 dir = new Vec3(Math.cos(th) * Math.cos(ph), Math.sin(ph), Math.sin(th) * Math.cos(ph));
            Vec3 at = c.centre.add(dir.scale(r));
            // count 0: los tres ultimos valores son la velocidad (hacia afuera)
            c.level.sendParticles(i % 3 == 0 ? ParticleTypes.CAMPFIRE_COSY_SMOKE : ParticleTypes.CLOUD, at.x, at.y, at.z, 0,
                    dir.x, dir.y * 0.4, dir.z, 0.6 + rnd.nextDouble() * 0.5);
        }
        if (((int) r) % 5 == 0) {
            c.level.playSound(null, c.centre.x, c.centre.y, c.centre.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 2.0F, 0.4F);
        }
    }

    @Override
    public void clearPlayer(ServerPlayer player) {
        CASTS.removeIf(c -> c.caster == player);
        super.clearPlayer(player);
    }
}
