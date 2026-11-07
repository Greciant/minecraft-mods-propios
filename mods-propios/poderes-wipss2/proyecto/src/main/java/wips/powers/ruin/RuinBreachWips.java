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
import java.util.List;
import java.util.Map;
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
import wips.powers.terrain.WipsDestruction;

/**
 * Ruin 4 - Breach (rehecha). Mantener: la mano pudre todo lo que tiene enfrente y abre un tunel de 3x3 que avanza
 * mientras se mantiene (hasta 24-40 bloques segun maestria). Lo que este dentro del tunel recibe dano y cargas
 * de Ruin. Si se apunta a un enemigo cercano, el suelo bajo sus pies se pudre y cae en un pozo.
 * Destruccion PERMANENTE (WipsDestruction), sin restauracion ni proteccion: intencional. No depende del sistema de terreno de Awakened.
 */
public final class RuinBreachWips extends Ability {
    private static final int GREY = 0x8F8F86;

    private static final class Bore {
        final Vec3 dir;
        final Vec3 start;
        final Vec3 right;
        final Vec3 up;
        final int length;
        int step;
        int ticks;

        Bore(Vec3 start, Vec3 dir, int length) {
            this.start = start;
            this.dir = dir;
            this.length = length;
            Vec3 r = dir.cross(new Vec3(0, 1, 0));
            this.right = r.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : r.normalize();
            this.up = this.right.cross(dir).normalize();
        }
    }

    private final Map<UUID, Bore> bores = this.activeState();

    public RuinBreachWips() {
        super("ruin_breach", 18.0F, 7);
    }

    private static int length(ServerPlayer player) {
        int lvl = PowerService.data(player).masteryLevel();
        return Mth.clamp(24 + (lvl - 7) * 2, 24, 40);
    }

    @Override
    public boolean activate(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        LivingEntity body = AimUtil.aimedLivingEntity(player, 7.0, e -> e != player && e.onGround() && Harm.allowed(player, e));
        if (body != null) {
            this.pit(player, level, body);
            this.startCooldown(player, 80);
            return true;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        // si mira al suelo cerca, el tunel arranca bajo los pies (pozo); si no, por delante
        Vec3 start = eye.add(look.scale(1.2));
        Bore bore = new Bore(start, look.normalize(), length(player));
        if (this.carveSlice(player, level, bore, 0) == 0 && this.carveSlice(player, level, bore, 1) == 0
                && this.carveSlice(player, level, bore, 2) == 0) {
            return this.fail(player, "awakened.ruin.nothing_to_rot");
        }
        bore.step = 3;
        this.bores.put(player.getUUID(), bore);
        PowerService.playAnimation(player, "ruin_breach_hold", true);
        Fx.sound(level, start, SoundEvents.SCULK_BLOCK_SPREAD, SoundSource.PLAYERS, 1.2F, 0.6F);
        return true;
    }

    private int carveSlice(ServerPlayer player, ServerLevel level, Bore bore, int d) {
        Vec3 c = bore.start.add(bore.dir.scale(d));
        List<BlockPos> slice = new ArrayList<>(9);
        for (int w = -1; w <= 1; w++) {
            for (int h = -1; h <= 1; h++) {
                slice.add(BlockPos.containing(c.add(bore.right.scale(w)).add(bore.up.scale(h))));
            }
        }
        int n = WipsDestruction.destroy(level, slice, 3);
        Fx.burst(level, Fx.dust(GREY, 1.6F), c, 6, 0.9, 0.02);
        Fx.burst(level, ParticleTypes.ASH, c, 10, 1.0, 0.02);
        return n;
    }

    @Override
    public void serverTick(ServerPlayer player) {
        Bore bore = this.bores.get(player.getUUID());
        if (bore == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (!player.isAlive() || bore.step >= bore.length) {
            this.end(player);
            return;
        }
        bore.ticks++;
        if (bore.ticks % 10 == 0 && !PowerService.charge(player, this, 1.5F)) {
            this.end(player);
            return;
        }
        // dos rebanadas por tick: el tunel avanza rapido
        for (int i = 0; i < 2 && bore.step < bore.length; i++, bore.step++) {
            this.carveSlice(player, level, bore, bore.step);
        }
        Vec3 head = bore.start.add(bore.dir.scale(bore.step));
        if (bore.ticks % 4 == 0) {
            Fx.sound(level, head, SoundEvents.GRAVEL_BREAK, SoundSource.PLAYERS, 1.0F, 0.5F);
        }
        float dmg = MasteryScaling.scaledDamage(3.0F, player, this.balanceKey());
        for (LivingEntity e : Harm.only(player, AimUtil.livingAlong(player, bore.start, head, 2.0))) {
            if (e.invulnerableTime <= 10) {
                Dmg.hurt(e, level, Dmg.by(player, WipsDamage.ABILITY), dmg);
                if (RuinStacks.add(e, 1, player, e.position())) {
                    RuinCues.stackLanded(level, e, e.position());
                }
            }
        }
    }

    private void pit(ServerPlayer player, ServerLevel level, LivingEntity body) {
        BlockPos feet = body.blockPosition();
        List<BlockPos> blocks = new ArrayList<>();
        int r = Math.max(1, Mth.ceil(body.getBbWidth() / 2.0F));
        for (int dy = 1; dy <= 5; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    blocks.add(feet.offset(dx, -dy, dz));
                }
            }
        }
        WipsDestruction.destroy(level, blocks, 2);
        Dmg.hurt(body, level, Dmg.by(player, WipsDamage.ABILITY), MasteryScaling.scaledDamage(6.0F, player, this.balanceKey()));
        Harm.effect(player, body, new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
        if (RuinStacks.add(body, 3, player, body.position())) {
            RuinCues.stackLanded(level, body, body.position());
        }
        body.setDeltaMovement(body.getDeltaMovement().add(0, -0.6, 0));
        body.hurtMarked = true;
        Fx.burst(level, Fx.dust(GREY, 2.0F), body.position(), 30, 1.2, 0.05);
        Fx.sound(level, body.position(), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.PLAYERS, 1.4F, 0.5F);
        PowerService.playAnimation(player, "ruin_grip_release");
    }

    @Override
    public void onRelease(ServerPlayer player) {
        if (this.bores.containsKey(player.getUUID())) {
            this.end(player);
        }
    }

    private void end(ServerPlayer player) {
        if (this.bores.remove(player.getUUID()) != null) {
            PowerService.stopAnimation(player);
            PowerService.playAnimation(player, "ruin_grip_release");
            this.startCooldown(player, 120);
        }
    }

    @Override
    public void clearPlayer(ServerPlayer player) {
        this.bores.remove(player.getUUID());
        super.clearPlayer(player);
    }

    @Override
    public String activationAnimation(ServerPlayer player) {
        return null;
    }
}
