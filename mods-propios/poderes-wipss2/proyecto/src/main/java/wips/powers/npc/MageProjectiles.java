package wips.powers.npc;

import com.awakenedmod.awakened.core.npc.NpcCaster;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;
import wips.powers.WipsPowers;

/** Proyectiles de los magos: solo particulas y un rayo de choque por tick (sin entidades nuevas). */
@EventBusSubscriber(modid = WipsPowers.MOD_ID)
public final class MageProjectiles {
    private static final class Shot {
        final NpcCaster caster;
        final MageTheme theme;
        final float damage;
        @Nullable final LivingEntity target;
        final int life;
        Vec3 pos;
        Vec3 vel;
        int age;

        Shot(NpcCaster caster, MageTheme theme, Vec3 pos, Vec3 vel, float damage, @Nullable LivingEntity target, int life) {
            this.caster = caster;
            this.theme = theme;
            this.pos = pos;
            this.vel = vel;
            this.damage = damage;
            this.target = target;
            this.life = life;
        }
    }

    private static final List<Shot> SHOTS = new ArrayList<>();

    private MageProjectiles() {
    }

    public static void launch(NpcCaster caster, MageTheme theme, Vec3 from, Vec3 vel, float damage, @Nullable LivingEntity target, int life) {
        SHOTS.add(new Shot(caster, theme, from, vel, damage, target, life));
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        Iterator<Shot> it = SHOTS.iterator();
        while (it.hasNext()) {
            Shot s = it.next();
            if (step(s)) {
                it.remove();
            }
        }
    }

    /** true = termino. */
    private static boolean step(Shot s) {
        if (s.caster.npc().isRemoved() || ++s.age > s.life) {
            return true;
        }
        ServerLevel level = s.caster.level();
        if (s.target != null && s.target.isAlive()) { // busca un poco al blanco
            Vec3 want = s.target.getBoundingBox().getCenter().subtract(s.pos).normalize().scale(s.vel.length());
            s.vel = s.vel.add(want.subtract(s.vel).scale(0.06));
        }
        Vec3 next = s.pos.add(s.vel);
        BlockHitResult wall = level.clip(new ClipContext(s.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, s.caster.npc()));
        Vec3 end = wall.getType() == HitResult.Type.MISS ? next : wall.getLocation();
        level.sendParticles(s.theme.dust(1.5F), s.pos.x, s.pos.y, s.pos.z, 3, 0.08, 0.08, 0.08, 0);
        level.sendParticles(s.theme.trail(), s.pos.x, s.pos.y, s.pos.z, 1, 0.05, 0.05, 0.05, 0.01);
        LivingEntity hit = null;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(s.pos, end).inflate(0.6), e -> e != s.caster.npc())) {
            if (e.getBoundingBox().inflate(0.35).clip(s.pos, end).isPresent() && s.caster.canHarm(e)) {
                hit = e;
                break;
            }
        }
        if (hit != null || wall.getType() != HitResult.Type.MISS) {
            Vec3 at = hit != null ? hit.getBoundingBox().getCenter() : end;
            level.sendParticles(s.theme.burst(), at.x, at.y, at.z, 1, 0, 0, 0, 0);
            level.sendParticles(s.theme.accentDust(1.8F), at.x, at.y, at.z, 12, 0.4, 0.4, 0.4, 0.05);
            MageMoves.sound(level, at, s.theme.hitSound(), 0.9F, 1.1F);
            if (hit != null) {
                MageMoves.hurt(s.caster, hit, s.damage, s.theme, s.pos, 1.0F);
            }
            return true;
        }
        s.pos = next;
        return false;
    }
}
