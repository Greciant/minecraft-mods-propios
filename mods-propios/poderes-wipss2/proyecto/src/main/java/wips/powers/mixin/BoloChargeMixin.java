package wips.powers.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Bolo Barrage: la carga usaba el destello a escala de dragon (3.5), que en forma humana quemaba
 * la pantalla a blanco. Clase anonima de la carga de BOLO_BREATH en SeiryuClientFx (Grand Line 0.3.0).
 */
@Mixin(targets = "com.devilfruits.fruits.uo_uo_seiryu.client.SeiryuClientFx$5", remap = false)
public abstract class BoloChargeMixin {
    @ModifyConstant(method = "start", constant = @Constant(floatValue = 3.5F))
    private float wips$humanSizedCharge(float v) {
        return 1.3F;
    }
}
