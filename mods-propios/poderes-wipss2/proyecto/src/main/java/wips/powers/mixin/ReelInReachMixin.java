package wips.powers.mixin;

import com.awakenedmod.awakened.content.brawler.ReelInAbility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wips.powers.fighter.FighterPower;

/**
 * Luchador 2 = "Reel In" original de Brawler (misma captura, gancho y arrastre), ampliado: agarra desde 48 bloques
 * (antes 8-11), a cualquier entidad valida sin limite de tamano (Warden, Ender Dragon, jefes: ver
 * BrawlerUnliftableMixin) y el arrastre dura lo necesario para traerla desde esa distancia, un poco mas rapido.
 * En este pack solo el Luchador usa Reel In (Brawler no es obtenible).
 */
@Mixin(value = ReelInAbility.class, remap = false)
public abstract class ReelInReachMixin {
    @ModifyConstant(method = "pick", constant = @Constant(doubleValue = 10.0))
    private static double wips$searchRadius(double original) {
        return FighterPower.REEL_RANGE + 2.0;
    }

    @ModifyConstant(method = "pick", constant = @Constant(doubleValue = 8.0))
    private static double wips$reach(double original) {
        return FighterPower.REEL_RANGE;
    }

    @ModifyConstant(method = "holdable", constant = @Constant(doubleValue = 11.0))
    private static double wips$leash(double original) {
        return FighterPower.REEL_RANGE + 3.0;
    }

    @ModifyConstant(method = "lambda$drag$0", constant = @Constant(intValue = 20))
    private int wips$dragTicks(int original) {
        return 70;
    }

    @ModifyConstant(method = "lambda$drag$0", constant = @Constant(doubleValue = 1.0, ordinal = 1))
    private double wips$dragStep(double original) {
        return 1.6;
    }

    @ModifyConstant(method = "close", constant = @Constant(intValue = 20))
    private int wips$hitStop(int original) {
        return 70;
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void wips$closeStart(CallbackInfo ci) {
        FighterPower.reeling = true;
    }

    @Inject(method = "close", at = @At("RETURN"))
    private void wips$closeEnd(CallbackInfo ci) {
        FighterPower.reeling = false;
    }
}
