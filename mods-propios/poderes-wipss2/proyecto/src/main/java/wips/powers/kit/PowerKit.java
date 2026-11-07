package wips.powers.kit;

import com.awakenedmod.awakened.core.Dmg;
import com.awakenedmod.awakened.core.Harm;
import com.awakenedmod.awakened.core.PowerService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import wips.powers.combat.WipsDamage;

/** Utilidades comunes de los poderes nuevos (Hielo, Slime, Luchador): objetivos validos, golpes y poder activo. */
public final class PowerKit {
    private PowerKit() {
    }

    /** El jugador tiene este poder activo ahora? */
    public static boolean has(ServerPlayer player, String powerId) {
        var data = PowerService.data(player);
        return data != null && powerId.equals(data.powerId());
    }

    /** Enemigo valido: vivo, no el dueno, no sus mascotas/invocaciones, no soportes de armadura, y lo permiten equipos/PvP. */
    public static boolean enemy(ServerPlayer owner, Entity e) {
        if (!(e instanceof LivingEntity le) || le == owner || !le.isAlive() || le.isSpectator() || le instanceof ArmorStand) {
            return false;
        }
        if (e instanceof OwnableEntity own && owner.getUUID().equals(own.getOwnerUUID())) {
            return false;
        }
        if (e instanceof TamableAnimal t && t.isOwnedBy(owner)) {
            return false;
        }
        if (e instanceof wips.powers.slime.MiniSlimeEntity m && owner.getUUID().equals(m.ownerId())) {
            return false;
        }
        if (e instanceof net.minecraft.world.entity.player.Player p && (p.isCreative() || p.isSpectator())) {
            return false;
        }
        return Harm.allowed(owner, e);
    }

    /** Enemigos dentro de una esfera, los mas cercanos primero, como mucho max. */
    public static List<LivingEntity> enemies(ServerPlayer owner, ServerLevel level, Vec3 center, double radius, int max) {
        List<LivingEntity> out = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius),
                e -> enemy(owner, e) && e.position().add(0, e.getBbHeight() * 0.5, 0).distanceToSqr(center) <= radius * radius));
        if (out.size() > max) {
            out.sort(Comparator.comparingDouble(e -> e.distanceToSqr(center)));
            return out.subList(0, max);
        }
        return out;
    }

    /** Golpe de poder: pasa por Dmg de Awakened, asi lo escala el nivel del jugador (WipsScaling) una sola vez. */
    public static boolean hit(ServerPlayer owner, LivingEntity target, float baseDamage) {
        if (!enemy(owner, target)) {
            return false;
        }
        return Dmg.hurt(target, owner.serverLevel(), Dmg.by(owner, WipsDamage.ABILITY), baseDamage);
    }
}
