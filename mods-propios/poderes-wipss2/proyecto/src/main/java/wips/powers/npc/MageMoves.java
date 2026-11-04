package wips.powers.npc;

import com.awakenedmod.awakened.content.pyrokinetic.npc.PyroNpcKit;
import com.awakenedmod.awakened.core.npc.NpcCaster;
import com.awakenedmod.awakened.core.npc.NpcContext;
import com.awakenedmod.awakened.core.npc.NpcMove;
import com.awakenedmod.awakened.core.npc.PoweredNpc;
import com.awakenedmod.awakened.core.progression.MasteryScaling;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import wips.powers.combat.WipsDamage;

/** Las cuatro tecnicas de los magos. Todas escalan el dano con la maestria del mago (1-25). */
public final class MageMoves {
    private MageMoves() {
    }

    static float dmg(NpcContext ctx, float base, String key) {
        return MasteryScaling.scaledDamage(base, ctx.npc().mastery(), 0.0F, key);
    }

    static DamageSource source(PoweredNpc npc) {
        return new DamageSource(npc.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(WipsDamage.ABILITY), npc);
    }

    static boolean hurt(NpcCaster caster, LivingEntity victim, float amount, MageTheme theme, Vec3 from, float strength) {
        if (!caster.canHarm(victim)) {
            return false;
        }
        boolean hit = victim.hurt(source(caster.npc()), amount);
        if (hit) {
            theme.apply(victim, from, strength);
            Vec3 push = victim.position().subtract(from);
            push = push.lengthSqr() < 1.0E-4 ? Vec3.ZERO : new Vec3(push.x, 0, push.z).normalize();
            double kb = theme.knockback() * strength;
            victim.push(push.x * kb, Math.abs(kb) * 0.25 + (kb < 0 ? 0.6 : 0.0), push.z * kb);
            victim.hurtMarked = true;
        }
        return hit;
    }

    static void sound(ServerLevel level, Vec3 at, net.minecraft.sounds.SoundEvent s, float vol, float pitch) {
        level.playSound(null, at.x, at.y, at.z, s, SoundSource.HOSTILE, vol, pitch);
    }

    static Vec3 hand(PoweredNpc npc) {
        return npc.getEyePosition().add(npc.getViewVector(1.0F).scale(0.7)).add(0, -0.35, 0);
    }

    static void aim(NpcContext ctx, double speed) {
        Vec3 p = ctx.leadPoint(ctx.caster().eyePos(), speed, 0.0, 3);
        if (p != null) {
            ctx.npc().aimAt(p, 40.0F);
        }
    }

    /** 1 - Proyectil del elemento: rapido, sigue un poco al blanco. */
    public static final class Bolt extends NpcMove {
        private final MageTheme t;
        private int age;

        public Bolt(MageTheme t) {
            super("bolt", 0, 3.0, 24.0, 18);
            this.t = t;
        }

        @Override
        public float staminaCost() {
            return 6.0F;
        }

        @Override
        public String balanceKey() {
            return "mage_bolt";
        }

        @Override
        public float score(NpcContext ctx) {
            return 1.0F;
        }

        @Override
        public void start(NpcContext ctx) {
            this.age = 0;
            aim(ctx, 1.4);
            ctx.npc().showClip(ctx.npc().getRandom().nextBoolean() ? PyroNpcKit.FIREBALL_R : PyroNpcKit.FIREBALL_L);
            sound(ctx.caster().level(), ctx.npc().position(), this.t.castSound(), 0.8F, 1.3F);
        }

        @Override
        public boolean tick(NpcContext ctx) {
            this.age++;
            aim(ctx, 1.4);
            if (this.age == 5) {
                PoweredNpc npc = ctx.npc();
                Vec3 from = hand(npc);
                Vec3 dir = npc.getViewVector(1.0F);
                MageProjectiles.launch(ctx.caster(), this.t, from, dir.scale(1.4), dmg(ctx, 4.0F, this.balanceKey()), ctx.target(), 40);
            }
            return this.age < 10;
        }
    }

    /** 2 - Nova: estallido alrededor cuando el blanco se acerca demasiado. */
    public static final class Nova extends NpcMove {
        private final MageTheme t;
        private int age;

        public Nova(MageTheme t) {
            super("nova", 5, 0.0, 5.5, 70);
            this.t = t;
        }

        @Override
        public float staminaCost() {
            return 12.0F;
        }

        @Override
        public String balanceKey() {
            return "mage_nova";
        }

        @Override
        public float score(NpcContext ctx) {
            return ctx.distance() < 4.0 ? 3.0F : 0.6F;
        }

        @Override
        public boolean needsLineOfSight() {
            return false;
        }

        @Override
        public void start(NpcContext ctx) {
            this.age = 0;
            ctx.npc().showClip(PyroNpcKit.BACKDRAFT);
        }

        @Override
        public boolean tick(NpcContext ctx) {
            this.age++;
            PoweredNpc npc = ctx.npc();
            ServerLevel level = ctx.caster().level();
            Vec3 c = npc.position().add(0, 1.0, 0);
            if (this.age < 9) {
                double r = 2.5 - this.age * 0.25;
                for (int i = 0; i < 10; i++) {
                    double a = i * Math.PI / 5 + this.age * 0.4;
                    level.sendParticles(this.t.dust(1.2F), c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
                }
            } else if (this.age == 9) {
                double radius = 4.5 + npc.mastery() * 0.08;
                level.sendParticles(this.t.burst(), c.x, c.y, c.z, 1, 0, 0, 0, 0);
                for (int i = 0; i < 40; i++) {
                    double a = i * Math.PI / 20;
                    level.sendParticles(this.t.dust(1.8F), c.x + Math.cos(a) * radius, npc.getY() + 0.2, c.z + Math.sin(a) * radius, 1, 0, 0.1, 0, 0);
                    level.sendParticles(this.t.trail(), c.x + Math.cos(a) * radius * 0.6, npc.getY() + 0.4, c.z + Math.sin(a) * radius * 0.6, 1, 0, 0.05, 0, 0.02);
                }
                sound(level, c, this.t.hitSound(), 1.2F, 0.8F);
                float d = dmg(ctx, 6.0F, this.balanceKey());
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(radius), e -> e != npc && e.distanceToSqr(c) <= radius * radius)) {
                    hurt(ctx.caster(), e, d, this.t, npc.position(), 1.2F);
                }
            }
            return this.age < 14;
        }
    }

    /** 3 - Rayo sostenido hacia el blanco (corta en paredes). */
    public static final class Beam extends NpcMove {
        private final MageTheme t;
        private int age;

        public Beam(MageTheme t) {
            super("beam", 10, 5.0, 20.0, 140);
            this.t = t;
        }

        @Override
        public float staminaCost() {
            return 18.0F;
        }

        @Override
        public String balanceKey() {
            return "mage_beam";
        }

        @Override
        public float score(NpcContext ctx) {
            return 1.4F;
        }

        @Override
        public boolean committed() {
            return true;
        }

        @Override
        public double footworkWhileRunning() {
            return 0.2;
        }

        @Override
        public void start(NpcContext ctx) {
            this.age = 0;
            ctx.npc().showClip(PyroNpcKit.FLAME_BEAM);
            sound(ctx.caster().level(), ctx.npc().position(), this.t.castSound(), 1.0F, 0.8F);
        }

        @Override
        public boolean tick(NpcContext ctx) {
            this.age++;
            PoweredNpc npc = ctx.npc();
            ServerLevel level = ctx.caster().level();
            LivingEntity target = ctx.target();
            if (target != null) {
                npc.aimAt(target.getBoundingBox().getCenter(), 6.0F); // gira lento: se puede esquivar
            }
            Vec3 from = hand(npc);
            Vec3 dir = npc.getViewVector(1.0F);
            Vec3 to = from.add(dir.scale(20.0));
            BlockHitResult wall = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, npc));
            if (wall.getType() != HitResult.Type.MISS) {
                to = wall.getLocation();
            }
            double len = from.distanceTo(to);
            if (this.age > 6) {
                for (double s = 0; s < len; s += 0.6) {
                    Vec3 p = from.add(dir.scale(s));
                    level.sendParticles(this.t.dust(1.0F + (float) Math.sin(this.age * 0.6 + s) * 0.3F), p.x, p.y, p.z, 1, 0.04, 0.04, 0.04, 0);
                    if (((int) (s * 10)) % 18 == 0) {
                        level.sendParticles(this.t.trail(), p.x, p.y, p.z, 1, 0.08, 0.08, 0.08, 0.01);
                    }
                }
                if (this.age % 5 == 0) {
                    float d = dmg(ctx, 1.8F, this.balanceKey());
                    Vec3 end = to;
                    for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, end).inflate(0.8), e -> e != npc)) {
                        if (e.getBoundingBox().inflate(0.4).clip(from, end).isPresent()) {
                            hurt(ctx.caster(), e, d, this.t, from, 0.3F);
                        }
                    }
                }
            }
            return this.age < 40;
        }
    }

    /** 4 - Golpe definitivo: marca el suelo bajo el blanco y lo castiga un segundo despues. */
    public static final class Ultimate extends NpcMove {
        private final MageTheme t;
        private int age;
        private Vec3 spot;

        public Ultimate(MageTheme t) {
            super("ultimate", 16, 4.0, 22.0, 360);
            this.t = t;
        }

        @Override
        public float staminaCost() {
            return 30.0F;
        }

        @Override
        public String balanceKey() {
            return "mage_ultimate";
        }

        @Override
        public float score(NpcContext ctx) {
            return 2.0F;
        }

        @Override
        public void start(NpcContext ctx) {
            this.age = 0;
            LivingEntity target = ctx.target();
            this.spot = target != null ? target.position() : ctx.npc().position().add(ctx.npc().getViewVector(1.0F).scale(8));
            ctx.npc().showClip(PyroNpcKit.BARRAGE);
            sound(ctx.caster().level(), this.spot, this.t.castSound(), 1.6F, 0.6F);
        }

        @Override
        public boolean tick(NpcContext ctx) {
            this.age++;
            ServerLevel level = ctx.caster().level();
            double radius = 3.5 + ctx.npc().mastery() * 0.06;
            if (this.age <= 22) {
                // aviso: anillo que se cierra
                double r = radius * (1.0 - this.age / 30.0);
                for (int i = 0; i < 24; i++) {
                    double a = i * Math.PI / 12 + this.age * 0.15;
                    level.sendParticles(this.t.dust(1.4F), this.spot.x + Math.cos(a) * r, this.spot.y + 0.1, this.spot.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
                }
                return true;
            }
            if (this.age == 23) {
                for (int y = 0; y < 14; y++) {
                    level.sendParticles(this.t.accentDust(2.4F), this.spot.x, this.spot.y + y * 0.6, this.spot.z, 6, 0.4, 0.2, 0.4, 0.0);
                    level.sendParticles(this.t.trail(), this.spot.x, this.spot.y + y * 0.6, this.spot.z, 3, 0.5, 0.2, 0.5, 0.05);
                }
                level.sendParticles(this.t.burst(), this.spot.x, this.spot.y + 0.5, this.spot.z, 2, 0.5, 0.2, 0.5, 0);
                sound(level, this.spot, this.t.hitSound(), 2.0F, 0.7F);
                if ("storm_caller".equals(this.t.powerId()) || "seiryuu".equals(this.t.powerId())) {
                    LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                    if (bolt != null) {
                        bolt.moveTo(this.spot);
                        bolt.setVisualOnly(true);
                        level.addFreshEntity(bolt);
                    }
                }
                float d = dmg(ctx, 10.0F, this.balanceKey());
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(this.spot, this.spot).inflate(radius, 3, radius),
                        e -> e != ctx.npc())) {
                    hurt(ctx.caster(), e, d, this.t, this.spot, 1.6F);
                }
            }
            return this.age < 28;
        }
    }
}
