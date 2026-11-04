package wips.powers.npc;

import com.awakenedmod.awakened.core.Fx;
import com.awakenedmod.awakened.core.power.Power;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import wips.powers.WipsPowers;

/**
 * Despertar de un poder: encima del estallido de Awakened, 2 s de efecto propio del poder: tres anillos de choque
 * que se abren, una espiral doble que sube, particulas del elemento y su sonido. Gamma y Seiryuu (que no tenian
 * efecto propio) suman uno: pulsos de radiacion verde / dragon de fuego y rayo.
 */
@EventBusSubscriber(modid = WipsPowers.MOD_ID)
public final class AwakenFx {
    private record Run(ServerPlayer player, Vec3 at, int color, int accent, ParticleOptions element, ParticleOptions burst, String id, int[] t) {}

    private static final List<Run> RUNS = new ArrayList<>();
    private static final int TICKS = 40;

    private AwakenFx() {
    }

    private static MageTheme theme(String id) {
        for (MageTheme m : MageTheme.all()) {
            if (m.powerId().equals(id)) {
                return m;
            }
        }
        return null;
    }

    public static void erupt(ServerPlayer player, Power power) {
        String id = power.id();
        int color = power.themeColor() & 0xFFFFFF;
        MageTheme m = theme(id);
        ParticleOptions element = m != null ? m.trail() : ("pyrokinetic".equals(id) ? ParticleTypes.FLAME : ParticleTypes.END_ROD);
        ParticleOptions burst = m != null ? m.burst() : ("pyrokinetic".equals(id) ? ParticleTypes.LAVA : ParticleTypes.FLASH);
        int accent = m != null ? m.accent() : 0xFFD27A;
        RUNS.add(new Run(player, player.position(), color, accent, element, burst, id, new int[]{0}));
        ServerLevel level = player.serverLevel();
        SoundEvent sig = m != null ? m.hitSound() : SoundEvents.FIRECHARGE_USE;
        level.playSound(null, player.getX(), player.getY(), player.getZ(), sig, SoundSource.PLAYERS, 1.4F, 0.8F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2F, 1.3F);
        Fx.shake(level, player.position(), 24.0, 0.25F, 4, 14);
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        Iterator<Run> it = RUNS.iterator();
        while (it.hasNext()) {
            Run r = it.next();
            int t = ++r.t()[0];
            if (t > TICKS || r.player().isRemoved()) {
                it.remove();
                continue;
            }
            tick(r, t);
        }
    }

    private static void ring(ServerLevel level, ParticleOptions p, Vec3 c, double radius, int points, double y) {
        for (int i = 0; i < points; i++) {
            double a = i * (Math.PI * 2 / points);
            level.sendParticles(p, c.x + Math.cos(a) * radius, c.y + y, c.z + Math.sin(a) * radius, 1, 0, 0.02, 0, 0);
        }
    }

    private static void tick(Run r, int t) {
        ServerLevel level = r.player().serverLevel();
        Vec3 c = r.at();
        float u = t / (float) TICKS;
        // 1. tres anillos de choque, desfasados, que se abren y se apagan
        for (int k = 0; k < 3; k++) {
            int start = k * 6;
            if (t > start && t < start + 18) {
                double rad = 0.6 + (t - start) * 0.55;
                ring(level, Fx.dust(k == 1 ? r.accent() : r.color(), 2.0F - (t - start) * 0.08F), c, rad, 36 + (int) rad * 4, 0.1 + k * 0.05);
            }
        }
        // 2. espiral doble que sube
        if (t < 30) {
            for (int s = 0; s < 2; s++) {
                double a = t * 0.55 + s * Math.PI;
                double h = t * 0.16;
                double rad = 1.3 - t * 0.03;
                level.sendParticles(Fx.dust(s == 0 ? r.color() : r.accent(), 1.3F), c.x + Math.cos(a) * rad, c.y + h, c.z + Math.sin(a) * rad, 2, 0.02, 0.02, 0.02, 0);
                level.sendParticles(r.element(), c.x + Math.cos(a) * rad, c.y + h, c.z + Math.sin(a) * rad, 1, 0.03, 0.03, 0.03, 0.01);
            }
        }
        // 3. columna central y chispas del elemento
        if (t % 3 == 0) {
            level.sendParticles(r.element(), c.x, c.y + 1.0, c.z, 6, 0.4, 1.0, 0.4, 0.04);
            level.sendParticles(Fx.dust(r.accent(), 0.8F), c.x, c.y + 2.5, c.z, 4, 0.2, 1.5, 0.2, 0.0);
        }
        if (t == 8 || t == 20) {
            level.sendParticles(r.burst(), c.x, c.y + 1.0, c.z, 1, 0, 0, 0, 0);
        }
        // 4. firma de los poderes que no tenian efecto
        if ("gamma".equals(r.id()) && t % 8 == 0) {
            double rad = 2.0 + t * 0.12;
            ring(level, ParticleTypes.GLOW, c, rad, 40, 0.3);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.5F, 1.4F);
        }
        if ("seiryuu".equals(r.id())) {
            double a = t * 0.35;
            double rad = 2.6 - u * 1.2;
            Vec3 head = c.add(Math.cos(a) * rad, 0.5 + u * 5.0, Math.sin(a) * rad);
            level.sendParticles(ParticleTypes.FLAME, head.x, head.y, head.z, 6, 0.15, 0.15, 0.15, 0.02);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, head.x, head.y, head.z, 4, 0.2, 0.2, 0.2, 0.1);
            if (t == 30) {
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                if (bolt != null) {
                    bolt.moveTo(c.add(3, 0, 0));
                    bolt.setVisualOnly(true);
                    level.addFreshEntity(bolt);
                }
                level.playSound(null, c.x, c.y, c.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 1.0F, 1.2F);
            }
        }
    }
}
