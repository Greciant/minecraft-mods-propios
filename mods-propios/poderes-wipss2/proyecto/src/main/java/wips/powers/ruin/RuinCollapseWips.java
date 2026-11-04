package wips.powers.ruin;

import com.awakenedmod.awakened.content.ruin.RuinCues;
import com.awakenedmod.awakened.content.ruin.RuinStacks;
import com.awakenedmod.awakened.core.AimUtil;
import com.awakenedmod.awakened.core.Dmg;
import com.awakenedmod.awakened.core.Fx;
import com.awakenedmod.awakened.core.Harm;
import com.awakenedmod.awakened.core.PowerService;
import com.awakenedmod.awakened.core.power.Ability;
import com.awakenedmod.awakened.core.progression.MasteryScaling;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import wips.powers.combat.WipsDamage;
import wips.powers.terrain.WipsTerrain;

/**
 * Ruin 5 - Collapse (rehecha). Golpe al suelo: una onda de podredumbre sale del jugador y el terreno se desmorona
 * en anillos (radio 10 a 20 segun maestria, 4 de hondo). Todo lo que este en la zona cae, recibe dano, Lentitud,
 * Marchitamiento y cargas de Ruin. El jugador no cae: su columna se mantiene. Los bloques vuelven a los 15 s.
 */
public final class RuinCollapseWips extends Ability {
    private static final int RESTORE_TICKS = 300;
    private static final float MAX_HARDNESS = 50.0F;
    private static final int DEPTH = 4;
    private static final int GREY = 0x8F8F86;

    private static final class Wave {
        final BlockPos centre;
        final double radius;
        final float damage;
        final Set<UUID> hit = new HashSet<>();
        int ring;

        Wave(BlockPos centre, double radius, float damage) {
            this.centre = centre;
            this.radius = radius;
            this.damage = damage;
        }
    }

    private final Map<UUID, Wave> waves = this.activeState();

    public RuinCollapseWips() {
        super("ruin_collapse", 70.0F, 10);
    }

    private static double radius(ServerPlayer player) {
        int lvl = PowerService.data(player).masteryLevel();
        double r = Mth.lerp(Mth.clamp((lvl - 10) / 10.0, 0.0, 1.0), 10.0, 20.0);
        return r * (1.0 + MasteryScaling.abilityPower(player) * 0.5);
    }

    @Override
    public boolean activate(ServerPlayer player) {
        if (this.waves.containsKey(player.getUUID())) {
            return this.fail(player, "awakened.fail.busy");
        }
        if (!player.onGround()) {
            return this.fail(player, "awakened.ruin.collapse_no_ground");
        }
        ServerLevel level = player.serverLevel();
        float damage = MasteryScaling.scaledDamage(12.0F, player, this.balanceKey());
        this.waves.put(player.getUUID(), new Wave(player.blockPosition(), radius(player), damage));
        PowerService.playAnimation(player, "ruin_grip_release");
        Fx.sound(level, player.position(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 1.0F, 0.6F);
        Fx.sound(level, player.position(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.0F, 0.5F);
        this.startCooldown(player, 600);
        return true;
    }

    @Override
    public void serverTick(ServerPlayer player) {
        Wave wave = this.waves.get(player.getUUID());
        if (wave == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        // un anillo de 1 bloque por tick
        int r0 = wave.ring;
        int r1 = wave.ring + 1;
        wave.ring++;
        List<BlockPos> blocks = new ArrayList<>();
        int cx = wave.centre.getX();
        int cz = wave.centre.getZ();
        for (int dx = -r1; dx <= r1; dx++) {
            for (int dz = -r1; dz <= r1; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d < r0 || d >= r1 || d < 1.6) {
                    continue; // la columna del jugador queda en pie
                }
                int top = wave.centre.getY() - 1;
                // sigue el relieve: busca el suelo de esa columna cerca de la altura del jugador
                BlockPos col = new BlockPos(cx + dx, top + 3, cz + dz);
                for (int k = 0; k < 7 && level.getBlockState(col).isAir(); k++) {
                    col = col.below();
                }
                for (int y = 0; y < DEPTH; y++) {
                    blocks.add(col.below(y));
                }
            }
        }
        WipsTerrain.crumble(level, player, blocks, RESTORE_TICKS + wave.ring * 3, MAX_HARDNESS, 4);
        Vec3 c = Vec3.atBottomCenterOf(wave.centre);
        for (int i = 0; i < 24; i++) {
            double a = i * (Math.PI * 2 / 24);
            Vec3 p = c.add(Math.cos(a) * r1, 0.2, Math.sin(a) * r1);
            level.sendParticles(Fx.dust(GREY, 2.2F), p.x, p.y, p.z, 2, 0.3, 0.4, 0.3, 0.02);
            if (i % 3 == 0) {
                level.sendParticles(ParticleTypes.ASH, p.x, p.y + 0.5, p.z, 4, 0.3, 0.5, 0.3, 0.02);
            }
        }
        if (wave.ring % 3 == 0) {
            Fx.sound(level, c, SoundEvents.GRAVEL_BREAK, SoundSource.PLAYERS, 1.5F, 0.4F);
        }
        for (LivingEntity e : Harm.only(player, AimUtil.livingAround(player, c, r1 + 0.5))) {
            if (e == player || !wave.hit.add(e.getUUID())) {
                continue;
            }
            Dmg.hurt(e, level, Dmg.by(player, WipsDamage.ABILITY), wave.damage);
            Harm.effect(player, e, new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 2));
            Harm.effect(player, e, new MobEffectInstance(MobEffects.WITHER, 80, 1));
            if (RuinStacks.add(e, 3, player, e.position())) {
                RuinCues.stackLanded(level, e, e.position());
            }
            e.setDeltaMovement(e.getDeltaMovement().add(0, -0.8, 0));
            e.hurtMarked = true;
        }
        if (r1 >= wave.radius) {
            this.waves.remove(player.getUUID());
            Fx.screenShake(level, c, wave.radius + 12, 20, GREY, true);
        }
    }

    @Override
    public boolean requiresFootingWhenRealistic() {
        return true;
    }

    @Override
    public void clearPlayer(ServerPlayer player) {
        this.waves.remove(player.getUUID());
        super.clearPlayer(player);
    }

    @Override
    public String activationAnimation(ServerPlayer player) {
        return null;
    }
}
