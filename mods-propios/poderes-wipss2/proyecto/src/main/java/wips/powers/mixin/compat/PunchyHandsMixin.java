package wips.powers.mixin.compat;

import com.awakenedmod.awakened.client.AwakenedAnimations;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Punchy: mientras una habilidad de Awakened maneja los brazos en primera persona (Kinetic Charger, golpes,
 * cargas...), Punchy se apaga para que no oculte ni reemplace esos brazos. Al terminar la animacion vuelve solo.
 */
@Mixin(targets = "punchy.config.PunchyConfig", remap = false)
public abstract class PunchyHandsMixin {
    @Inject(method = "isModEnabled", at = @At("HEAD"), cancellable = true)
    private static void wips$yieldToPowers(CallbackInfoReturnable<Boolean> cir) {
        if (AwakenedAnimations.libraryOwnsFirstPerson()) {
            cir.setReturnValue(false);
        }
    }
}
