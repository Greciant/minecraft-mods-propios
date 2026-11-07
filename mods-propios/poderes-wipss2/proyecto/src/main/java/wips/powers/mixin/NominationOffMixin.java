package wips.powers.mixin;

import com.awakenedmod.awakened.client.nominate.NominationCampaign;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Quita "Nominate The Awakening": el boton del menu de pausa, la tarjeta de la pantalla de personaje y los avisos
 * de chat dependen de allowed()/linkVisible()/due(). Con los tres apagados no aparece nada y el resto de la
 * interfaz queda igual (la pantalla de personaje ya acomoda su alto cuando linkVisible() es falso).
 */
@Mixin(value = NominationCampaign.class, remap = false)
public abstract class NominationOffMixin {
    @Inject(method = "allowed", at = @At("HEAD"), cancellable = true)
    private static void wips$allowed(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }

    @Inject(method = "linkVisible", at = @At("HEAD"), cancellable = true)
    private static void wips$linkVisible(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }

    @Inject(method = "due", at = @At("HEAD"), cancellable = true)
    private static void wips$due(CallbackInfoReturnable<NominationCampaign.Kind> cir) {
        cir.setReturnValue(null);
    }
}
