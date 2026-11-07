package wips.powers.slime;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import wips.powers.kit.PowerKit;
import wips.powers.kit.WipsEntities;

/**
 * Mini slime aliado (Slime 1). Cada uno con su propia vida (12), no se divide, no suelta nada ni da XP, no se guarda en
 * el mundo y nunca ataca al dueno ni a sus aliados: solo persigue al objetivo elegido por el dueno (o, si no hay,
 * a lo que ataque al dueno / el hostil mas cercano). Pega por contacto con dano de poder (escala con el nivel).
 */
public class MiniSlimeEntity extends Slime {
    public static final float CONTACT_DAMAGE = 2.5F;
    public static final int LIFE = 1200;
    private ServerPlayer owner;
    private UUID ownerId;
    private int hitCooldown;
    private int hopDelay;
    /** Autotest. */
    public static int hits;

    public MiniSlimeEntity(EntityType<? extends Slime> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.MAX_HEALTH, 12.0)
                .add(Attributes.ATTACK_DAMAGE, 2.0).add(Attributes.FOLLOW_RANGE, 24.0);
    }

    public static MiniSlimeEntity spawn(ServerLevel level, ServerPlayer owner, double x, double y, double z) {
        MiniSlimeEntity s = new MiniSlimeEntity(WipsEntities.MINI_SLIME.get(), level);
        s.owner = owner;
        s.ownerId = owner.getUUID();
        s.setSize(1, true);
        s.getAttribute(Attributes.MAX_HEALTH).setBaseValue(12.0);
        s.setHealth(12.0F);
        s.xpReward = 0;
        s.setPersistenceRequired();
        s.moveTo(x, y, z, owner.getYRot(), 0);
        level.addFreshEntity(s);
        return s;
    }

    public static List<MiniSlimeEntity> owned(ServerPlayer owner) {
        List<MiniSlimeEntity> out = new ArrayList<>();
        for (MiniSlimeEntity s : owner.serverLevel().getEntitiesOfClass(MiniSlimeEntity.class, owner.getBoundingBox().inflate(64))) {
            if (owner.getUUID().equals(s.ownerId) && s.isAlive()) {
                out.add(s);
            }
        }
        return out;
    }

    public UUID ownerId() {
        return this.ownerId;
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        // fuera el "atacar jugadores" vanilla: el objetivo lo decide el dueno (ver tick)
        this.targetSelector.removeAllGoals(g -> true);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            return;
        }
        if (this.owner == null || this.owner.isRemoved() || !this.owner.isAlive() || this.owner.level() != this.level()
                || this.tickCount > LIFE) {
            this.poof();
            return;
        }
        LivingEntity t = this.getTarget();
        if (t != null && (!t.isAlive() || !PowerKit.enemy(this.owner, t) || t.distanceToSqr(this) > 32 * 32)) {
            this.setTarget(null);
            t = null;
        }
        if (t == null && this.tickCount % 10 == 0) {
            t = SlimePower.chosenTarget(this.owner);
            if (t == null) {
                t = this.autoTarget();
            }
            if (t != null) {
                this.setTarget(t);
            }
        }
        if (t == null && this.distanceToSqr(this.owner) > 20 * 20) {
            this.moveTo(this.owner.getX(), this.owner.getY(), this.owner.getZ()); // no se pierde
        }
        if (this.hitCooldown > 0) {
            this.hitCooldown--;
        }
        // movimiento propio: saltos hacia el objetivo (o hacia el dueno si quedo lejos); no depende de la IA vanilla
        Vec3 goal = t != null ? t.position() : this.distanceToSqr(this.owner) > 6 * 6 ? this.owner.position() : null;
        if (goal != null && this.onGround() && --this.hopDelay <= 0) {
            Vec3 d = goal.subtract(this.position());
            double len = Math.sqrt(d.x * d.x + d.z * d.z);
            if (len > 0.4) {
                float yaw = (float) (Math.atan2(d.z, d.x) * (180.0 / Math.PI)) - 90.0F;
                this.setYRot(yaw);
                this.yBodyRot = yaw;
                double sp = Math.min(0.45, 0.15 + len * 0.05);
                this.setDeltaMovement(d.x / len * sp, 0.42, d.z / len * sp);
                this.hasImpulse = true;
                this.playSound(SoundEvents.SLIME_JUMP_SMALL, 0.6F, 1.4F);
            }
            this.hopDelay = 6 + this.random.nextInt(5);
        }
        if (t != null && this.hitCooldown == 0 && this.getBoundingBox().inflate(0.35).intersects(t.getBoundingBox())) {
            t.invulnerableTime = 0;
            if (PowerKit.hit(this.owner, t, CONTACT_DAMAGE)) {
                hits++;
                SlimePower.Passive.onHit(this.owner, t);
            }
            this.hitCooldown = 12;
            this.playSound(SoundEvents.SLIME_ATTACK, 1.0F, 1.4F);
        }
    }

    private LivingEntity autoTarget() {
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity e : PowerKit.enemies(this.owner, (ServerLevel) this.level(), this.owner.position(), 12, 16)) {
            boolean hostile = e instanceof Enemy || e instanceof Mob m && m.getTarget() == this.owner || this.owner.getLastHurtByMob() == e;
            if (hostile && !(e instanceof MiniSlimeEntity)) {
                double d = e.distanceToSqr(this);
                if (d < bestD) {
                    bestD = d;
                    best = e;
                }
            }
        }
        return best;
    }

    private void poof() {
        if (this.level() instanceof ServerLevel sl) {
            sl.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.SLIME_BALL)), this.getX(), this.getY() + 0.3, this.getZ(),
                    10, 0.2, 0.2, 0.2, 0.1);
        }
        this.discard();
    }

    // ---- nada de dano vanilla, division, loot, XP ni desaparicion por dificultad ----

    @Override
    protected boolean isDealsDamage() {
        return false;
    }

    @Override
    public void playerTouch(Player player) {
    }

    @Override
    public void remove(RemovalReason reason) {
        this.setSize(1, false); // tamano 1 nunca se divide
        super.remove(reason);
    }

    @Override
    protected boolean shouldDropLoot() {
        return false;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public void checkDespawn() {
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.owner != null && (source.getEntity() == this.owner || source.getEntity() instanceof MiniSlimeEntity)) {
            return false; // el dueno y sus otros slimes no los lastiman
        }
        return super.hurt(source, amount);
    }
}
