package wips.powers.gamma;

import com.awakenedmod.awakened.content.gamma.GammaFx;
import com.awakenedmod.awakened.content.gamma.Rage;
import com.awakenedmod.awakened.core.AimUtil;
import com.awakenedmod.awakened.core.Dmg;
import com.awakenedmod.awakened.core.Fx;
import com.awakenedmod.awakened.core.Harm;
import com.awakenedmod.awakened.core.PowerService;
import com.awakenedmod.awakened.core.power.Ability;
import com.awakenedmod.awakened.core.progression.MasteryReason;
import com.awakenedmod.awakened.core.progression.MasteryScaling;
import com.awakenedmod.awakened.core.progression.MasterySystem;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Gamma 2 - Thunderclap sostenible. Mantener la tecla junta las manos y acumula fuerza hasta 5 s; al soltar
 * (o al llegar a los 5 s) aplaude. Sin cargar es igual al original; a carga completa hace x3 de dano,
 * x1.8 de alcance y x2 de empuje.
 */
public final class ThunderclapHeld extends Ability {
    private static final int MAX_TICKS = 100;
    private static final double BASE_RADIUS = 10.0;
    private static final float BASE_DAMAGE = 6.0F;
    private static final int GREEN = 7790650;

    private final Map<UUID, int[]> holds = this.activeState();

    public ThunderclapHeld() {
        super("gamma_thunderclap", 15.0F, 2);
    }

    @Override
    public boolean activate(ServerPlayer player) {
        if (this.holds.containsKey(player.getUUID())) {
            return false;
        }
        this.holds.put(player.getUUID(), new int[]{0});
        PowerService.playAnimation(player, "gamma_thunderclap", true);
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
        Vec3 chest = player.position().add(0, player.getBbHeight() * 0.6, 0).add(AimUtil.lookFlat(player).scale(0.6));
        if (ticks % 3 == 0) {
            Fx.burst(level, Fx.dust(GREEN, 0.8F + charge * 1.6F), chest, 2 + (int) (charge * 8), 0.25 + charge * 0.5, 0.02);
            Fx.burst(level, ParticleTypes.ELECTRIC_SPARK, chest, 1 + (int) (charge * 6), 0.3 + charge * 0.4, 0.05);
        }
        if (ticks % 20 == 0) {
            Fx.sound(level, chest, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.5F + charge * 0.5F, 0.6F + charge * 0.8F);
            Fx.shake(level, player.position(), 10.0, 0.04F + 0.08F * charge, 2, 4);
        }
        if (ticks % 10 == 0 && !PowerService.charge(player, this, 1.0F)) {
            this.clap(player, charge);
            return;
        }
        if (ticks >= MAX_TICKS) {
            this.clap(player, 1.0F);
        }
    }

    @Override
    public void onRelease(ServerPlayer player) {
        int[] t = this.holds.get(player.getUUID());
        if (t != null) {
            this.clap(player, Math.min(1.0F, t[0] / (float) MAX_TICKS));
        }
    }

    private void clap(ServerPlayer player, float charge) {
        if (this.holds.remove(player.getUUID()) == null) {
            return;
        }
        PowerService.stopAnimation(player);
        PowerService.playAnimation(player, "gamma_thunderclap");
        ServerLevel level = player.serverLevel();
        float rage = Rage.rage(PowerService.data(player));
        float factor = 0.6F + rage;
        double reach = BASE_RADIUS * factor * (1.0 + 0.8 * charge);
        float damage = MasteryScaling.scaledDamage(BASE_DAMAGE, player, this.balanceKey(), factor - 1.0F) * (1.0F + 2.0F * charge);
        double kb = 0.9 * factor * (1.0 + charge);
        GammaFx.thunderclap(level, player, reach * 0.75, Mth.clamp(rage + charge * 0.5F, 0.0F, 1.0F));
        Vec3 origin = player.position();
        Vec3 flatLook = AimUtil.lookFlat(player);
        int hits = 0;
        LivingEntity first = null;
        for (LivingEntity victim : Harm.only(player, AimUtil.livingAround(player, origin.add(0, player.getBbHeight() * 0.5, 0), reach))) {
            Vec3 to = victim.position().subtract(origin);
            Vec3 flatTo = new Vec3(to.x, 0, to.z);
            if (flatTo.lengthSqr() > 1.0E-4 && flatTo.normalize().dot(flatLook) < 0.12) {
                continue; // solo hacia adelante, como el original
            }
            Dmg.hurt(victim, level, Dmg.by(player, DamageTypes.PLAYER_ATTACK), damage);
            Vec3 push = flatTo.lengthSqr() < 1.0E-4 ? flatLook : flatTo.normalize();
            victim.push(push.x * kb, 0.4 + 0.4 * charge, push.z * kb);
            victim.hurtMarked = true;
            hits++;
            if (first == null) {
                first = victim;
            }
        }
        if (charge >= 0.99F) {
            Fx.sound(level, origin, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1.2F, 0.7F);
        }
        if (hits >= 2) {
            MasterySystem.award(player, MasteryReason.HIT, first);
        }
        this.startCooldown(player, 40 + Math.round(60 * charge));
    }

    @Override
    public void clearPlayer(ServerPlayer player) {
        this.holds.remove(player.getUUID());
        super.clearPlayer(player);
    }

    @Override
    public String activationAnimation(ServerPlayer player) {
        return null;
    }
}
