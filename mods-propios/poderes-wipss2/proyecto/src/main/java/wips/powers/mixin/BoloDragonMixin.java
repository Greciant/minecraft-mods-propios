package wips.powers.mixin;

import com.devilfruits.core.ability.AbilityContext;
import com.devilfruits.fruits.uo_uo_seiryu.SeiryuAbilities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import wips.powers.bridge.GlBridgeAbility;

/**
 * Seiryuu 4 - Bolo Barrage = Bolo Breath de la tercera forma (dragon) a pleno: mas meteoros, lluvia mas ancha
 * y mas lejana, crateres mas grandes y hondos (rompe bloques). Solo para la tecnica de wipspowers; la fruta
 * de Grand Line queda igual.
 */
@Mixin(value = AbilityContext.class, remap = false)
public abstract class BoloDragonMixin {
    @Shadow
    public abstract com.devilfruits.core.ability.Ability ability();

    @Inject(method = "extra", at = @At("RETURN"), cancellable = true)
    private void wips$massiveBolo(String name, CallbackInfoReturnable<Double> cir) {
        if (this.ability() != SeiryuAbilities.BOLO_BREATH) {
            return;
        }
        boolean ours;
        synchronized (GlBridgeAbility.OURS) {
            ours = GlBridgeAbility.OURS.contains((Object) this);
        }
        if (!ours) {
            return;
        }
        double v = cir.getReturnValue();
        double k = switch (name) {
            case "meteors" -> 2.3;            // 14 -> 32 meteoros
            case "meteorArea" -> 1.8;         // radio 9 -> 16
            case "meteorReach" -> 1.6;
            case "meteorCraterRadius" -> 1.8; // crater de 5 -> 9 bloques de ancho
            case "meteorCraterDepth" -> 1.6;
            case "impactRadius" -> 1.5;
            case "burnSeconds" -> 1.5;
            default -> 1.0;
        };
        if (k != 1.0) {
            cir.setReturnValue(v * k);
        }
    }
}
