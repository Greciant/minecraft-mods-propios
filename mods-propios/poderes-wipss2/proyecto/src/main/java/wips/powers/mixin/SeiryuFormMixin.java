package wips.powers.mixin;

import com.devilfruits.fruits.uo_uo_seiryu.UoUoSeiryu;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import wips.powers.FormFx;

/** Solo en el cliente: los efectos de Grand Line ven la forma de la tecnica (ver FormFx). */
@Mixin(value = UoUoSeiryu.class, remap = false)
public abstract class SeiryuFormMixin {
    @Inject(method = "isIn", at = @At("HEAD"), cancellable = true)
    private static void wips$visualForm(Player player, String form, CallbackInfoReturnable<Boolean> cir) {
        if (player.level().isClientSide()) {
            String visual = FormFx.clientForm(player);
            if (visual != null) {
                cir.setReturnValue(visual.equals(form));
            }
        }
    }
}
