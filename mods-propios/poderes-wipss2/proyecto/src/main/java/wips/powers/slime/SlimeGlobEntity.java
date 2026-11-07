package wips.powers.slime;

import java.util.UUID;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import wips.powers.kit.WipsEntities;

/**
 * Gota de baba curativa (pasiva del Slime). Cae junto al enemigo golpeado; solo la puede recoger su dueno (pasando
 * por encima) y cura un porcentaje de su vida maxima. Dura 15 s. No se guarda en el mundo.
 */
public class SlimeGlobEntity extends Entity implements ItemSupplier {
    public static final float HEAL_FRACTION = 0.06F;
    private static final int LIFE = 300;
    private static final ItemStack LOOK = new ItemStack(Items.SLIME_BALL);
    private UUID ownerId;
    /** Autotest. */
    public static int picked;

    public SlimeGlobEntity(EntityType<? extends SlimeGlobEntity> type, Level level) {
        super(type, level);
    }

    public static SlimeGlobEntity spawn(ServerLevel level, ServerPlayer owner, Vec3 at) {
        SlimeGlobEntity g = new SlimeGlobEntity(WipsEntities.SLIME_GLOB.get(), level);
        g.ownerId = owner.getUUID();
        g.setPos(at.x, at.y + 0.5, at.z);
        g.setDeltaMovement((level.random.nextDouble() - 0.5) * 0.2, 0.3, (level.random.nextDouble() - 0.5) * 0.2);
        level.addFreshEntity(g);
        return g;
    }

    public UUID ownerId() {
        return this.ownerId;
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 v = this.getDeltaMovement();
        if (!this.onGround()) {
            v = v.add(0, -0.04, 0);
        }
        this.move(MoverType.SELF, v);
        this.setDeltaMovement(this.onGround() ? v.multiply(0.5, -0.4, 0.5) : v.scale(0.98));
        if (this.level().isClientSide()) {
            return;
        }
        if (this.tickCount > LIFE || this.ownerId == null) {
            this.discard();
            return;
        }
        if (this.tickCount > 10 && this.level().getPlayerByUUID(this.ownerId) instanceof ServerPlayer owner
                && owner.isAlive() && owner.getBoundingBox().inflate(0.6).intersects(this.getBoundingBox())) {
            float before = owner.getHealth();
            owner.heal(owner.getMaxHealth() * HEAL_FRACTION);
            picked++;
            ServerLevel sl = (ServerLevel) this.level();
            sl.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, LOOK), this.getX(), this.getY() + 0.2, this.getZ(), 10, 0.2, 0.2, 0.2, 0.1);
            sl.sendParticles(ParticleTypes.HEART, owner.getX(), owner.getY() + 2.1, owner.getZ(), owner.getHealth() > before ? 2 : 0, 0.3, 0.1, 0.3, 0);
            sl.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.SLIME_SQUISH_SMALL, SoundSource.PLAYERS, 1.0F, 1.2F);
            this.discard();
        }
    }

    @Override
    public ItemStack getItem() {
        return LOOK;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
