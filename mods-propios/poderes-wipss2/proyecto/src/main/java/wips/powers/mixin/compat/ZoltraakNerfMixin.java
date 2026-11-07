package wips.powers.mixin.compat;

import io.redspace.ironsspellbooks.api.spells.SchoolType;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Zoltraak: Cinematic Edition (Frieren Flight): nerf masivo. Todo el dano de sus hechizos lanzados por jugadores
 * (Zoltraak, Corrupted Zoltraak, las barreras de disparos y Gargantua) pasa por ZoltraakDamage.apply: se multiplica
 * por 0,2 y nunca pasa de 60 por golpe. Zoltraak legendario (nivel 10, modo grande) = 304 -> 60.
 * El jefe Qual (sin dueno jugador) no cambia.
 */
@Mixin(targets = "com.frierenflight.zoltraakcinematic.ZoltraakDamage", remap = false)
public abstract class ZoltraakNerfMixin {
    private static final float SCALE = 0.2F;
    private static final float MAX = 60.0F;

    @ModifyVariable(method = "apply(Lnet/minecraft/world/entity/LivingEntity;FLnet/minecraft/world/damagesource/DamageSource;Lio/redspace/ironsspellbooks/api/spells/SchoolType;Z)Z",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static float wips$nerf(float amount, LivingEntity target, float sameAmount, DamageSource source, SchoolType school, boolean pierce) {
        if (source != null && source.getEntity() instanceof Player) {
            return Math.min(MAX, amount * SCALE);
        }
        return amount;
    }
}
