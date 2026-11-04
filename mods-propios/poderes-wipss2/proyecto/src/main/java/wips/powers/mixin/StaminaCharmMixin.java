package wips.powers.mixin;

import com.awakenedmod.awakened.core.stamina.StaminaSystem;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import wips.powers.item.StaminaCharms;

/** Amuleto de estamina: +25 / +50 / +100 % a la regeneracion de energia de Awakened. */
@Mixin(value = StaminaSystem.class, remap = false)
public abstract class StaminaCharmMixin {
    @Inject(method = "regenPerSecond", at = @At("RETURN"), cancellable = true)
    private static void wips$charm(Player player, CallbackInfoReturnable<Float> cir) {
        float b = StaminaCharms.bonus(player);
        if (b > 0.0F) {
            cir.setReturnValue(cir.getReturnValue() * (1.0F + b));
        }
    }
}
