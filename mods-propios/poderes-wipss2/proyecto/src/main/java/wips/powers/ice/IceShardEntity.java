package wips.powers.ice;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import wips.powers.kit.PowerKit;
import wips.powers.kit.WipsEntities;

/**
 * Tempano teledirigido (Hielo 4). Orbita al dueno; cada 5 ticks busca un enemigo valido a 16 bloques (hostil, o que
 * persiga/haya pegado al dueno). Lo persigue con direccion suave y atraviesa bloques (nunca queda trabado). Al
 * impactar: dano + 1 carga de la pasiva. Si el objetivo muere o desaparece vuelve a orbitar; si la persecucion pasa
 * de 5 s o el tempano vive mas de 30 s, se rompe. No se guarda en el mundo (sin restos al recargar).
 */
public class IceShardEntity extends Entity implements ItemSupplier {
    public static final double RANGE = 16.0;
    public static final float DAMAGE = 6.0F;
    private static final int LIFE = 600;
    private static final int CHASE_MAX = 100;
    private static final ItemStack LOOK = new ItemStack(Items.PACKED_ICE);

    private ServerPlayer owner;
    private UUID ownerId;
    private int slot;
    private LivingEntity target;
    private int chase;
    /** Autotest. */
    public static int hits;

    public IceShardEntity(EntityType<? extends IceShardEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static void spawn(ServerLevel level, ServerPlayer owner, int slot) {
        IceShardEntity s = new IceShardEntity(WipsEntities.ICE_SHARD.get(), level);
        s.owner = owner;
        s.ownerId = owner.getUUID();
        s.slot = slot;
        s.setPos(s.orbitPos(0));
        level.addFreshEntity(s);
    }

    public static List<IceShardEntity> owned(ServerPlayer owner) {
        List<IceShardEntity> out = new ArrayList<>();
        for (IceShardEntity s : owner.serverLevel().getEntitiesOfClass(IceShardEntity.class, owner.getBoundingBox().inflate(RANGE * 2))) {
            if (owner.getUUID().equals(s.ownerId) && !s.isRemoved()) {
                out.add(s);
            }
        }
        return out;
    }

    public int slot() {
        return this.slot;
    }

    private Vec3 orbitPos(int age) {
        double a = age * 0.12 + this.slot * (Math.PI * 2 / IcePower.IceShards.MAX);
        return this.owner.position().add(Math.cos(a) * 1.4, 1.7 + Math.sin(age * 0.2 + this.slot) * 0.15, Math.sin(a) * 1.4);
    }

    private boolean valid(LivingEntity e) {
        return e != null && e.isAlive() && !e.isRemoved() && e.level() == this.level() && PowerKit.enemy(this.owner, e)
                && e.distanceToSqr(this.owner) <= (RANGE + 8) * (RANGE + 8);
    }

    private LivingEntity findTarget() {
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity e : PowerKit.enemies(this.owner, (ServerLevel) this.level(), this.owner.position(), RANGE, 24)) {
            boolean hostile = e instanceof Enemy || e instanceof Mob m && m.getTarget() == this.owner
                    || e.getLastHurtByMob() == this.owner || this.owner.getLastHurtByMob() == e;
            if (!hostile || Frost.isFrozen(e) && this.level().random.nextBoolean()) {
                continue;
            }
            double d = e.distanceToSqr(this.owner);
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            return;
        }
        ServerLevel level = (ServerLevel) this.level();
        if (this.owner == null || this.owner.isRemoved() || !this.owner.isAlive() || this.owner.level() != level
                || !PowerKit.has(this.owner, IcePower.ID) || this.tickCount > LIFE) {
            this.shatter(level);
            return;
        }
        if (this.target != null && !this.valid(this.target)) {
            this.target = null; // objetivo muerto o fuera: vuelve a orbitar
            this.chase = 0;
        }
        if (this.target == null && this.tickCount % 5 == 0) {
            this.target = this.findTarget();
            if (this.target != null) {
                level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 0.8F, 1.6F);
            }
        }
        if (this.target == null) {
            Vec3 to = this.orbitPos(this.tickCount);
            this.setDeltaMovement(to.subtract(this.position()).scale(0.5));
            this.setPos(this.position().lerp(to, 0.5));
            return;
        }
        if (++this.chase > CHASE_MAX) {
            this.shatter(level);
            return;
        }
        Vec3 aim = this.target.position().add(0, this.target.getBbHeight() * 0.55, 0);
        Vec3 want = aim.subtract(this.position());
        double dist = want.length();
        double hitR = 0.6 + this.target.getBbWidth() * 0.5;
        if (dist <= hitR) {
            this.target.invulnerableTime = 0;
            if (PowerKit.hit(this.owner, this.target, DAMAGE)) {
                hits++;
                Frost.charge(this.owner, this.target, 1);
            }
            this.shatter(level);
            return;
        }
        Vec3 vel = this.getDeltaMovement();
        Vec3 desired = want.scale(1.0 / dist).scale(0.95);
        vel = vel.scale(0.7).add(desired.scale(0.3));
        if (vel.length() < 0.6) {
            vel = vel.normalize().scale(0.6);
        }
        this.setDeltaMovement(vel);
        this.setPos(this.position().add(vel));
        level.sendParticles(ParticleTypes.SNOWFLAKE, this.getX(), this.getY(), this.getZ(), 1, 0.02, 0.02, 0.02, 0.0);
    }

    private void shatter(ServerLevel level) {
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()), this.getX(), this.getY(), this.getZ(),
                12, 0.2, 0.2, 0.2, 0.1);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.7F, 1.5F);
        this.discard();
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
    public boolean isPickable() {
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
