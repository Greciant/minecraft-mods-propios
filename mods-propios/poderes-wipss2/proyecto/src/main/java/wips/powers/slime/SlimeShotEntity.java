package wips.powers.slime;

import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import wips.powers.kit.PowerKit;
import wips.powers.kit.WipsEntities;

/** Gota de baba disparada (Slime 3): dano + ralentizacion del 70 % que se renueva sin acumularse. */
public class SlimeShotEntity extends ThrowableItemProjectile {
    public static final float DAMAGE = 2.0F;
    /** Autotest. */
    public static int hits;

    public SlimeShotEntity(EntityType<? extends SlimeShotEntity> type, Level level) {
        super(type, level);
    }

    public static void fire(ServerLevel level, ServerPlayer owner) {
        SlimeShotEntity s = new SlimeShotEntity(WipsEntities.SLIME_SHOT.get(), level);
        s.setOwner(owner);
        s.setPos(owner.getX(), owner.getEyeY() - 0.2, owner.getZ());
        s.shootFromRotation(owner, owner.getXRot(), owner.getYRot(), 0.0F, 1.9F, 2.5F);
        level.addFreshEntity(s);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.SLIME_BALL;
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        if (!(this.getOwner() instanceof ServerPlayer owner) || !(hit.getEntity() instanceof LivingEntity target)) {
            return;
        }
        target.invulnerableTime = 0;
        if (PowerKit.hit(owner, target, DAMAGE)) {
            hits++;
            SlimePower.Slow.apply(target, 30);
            SlimePower.Passive.onHit(owner, target);
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (this.level() instanceof ServerLevel sl) {
            sl.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.SLIME_BALL)), this.getX(), this.getY(), this.getZ(),
                    6, 0.1, 0.1, 0.1, 0.08);
            this.discard();
        }
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
