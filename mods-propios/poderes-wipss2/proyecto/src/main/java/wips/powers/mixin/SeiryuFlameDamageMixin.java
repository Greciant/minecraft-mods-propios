package wips.powers.mixin;

import com.devilfruits.core.util.Targeting;
import com.devilfruits.fruits.uo_uo_seiryu.UoUoSeiryu;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import wips.powers.combat.WipsDamage;

/**
 * Seiryuu (Flame Cloud, Bolo, Blast Breath): el dano de llama de Grand Line esta en #is_fire y los mobs
 * inmunes al fuego no lo recibian. Se cambia por wipspowers:ability_fire al momento de pegar.
 */
@Mixin(value = Targeting.class, remap = false)
public abstract class SeiryuFlameDamageMixin {
    @ModifyVariable(method = "hurt(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/entity/Entity;FLnet/minecraft/resources/ResourceKey;)Z",
            at = @At("HEAD"), argsOnly = true)
    private static ResourceKey<DamageType> wips$noFireImmunity(ResourceKey<DamageType> type) {
        return type == UoUoSeiryu.FLAME_DAMAGE ? WipsDamage.ABILITY_FIRE : type;
    }
}
