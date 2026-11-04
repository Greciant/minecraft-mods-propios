package wips.powers.mixin;

import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** El atributo de escala vanilla tope en 16; el Scaler mega llega a 25, asi que se sube el tope a 32. */
@Mixin(value = RangedAttribute.class, remap = false)
public abstract class ScaleAttributeMaxMixin {
    @Inject(method = "sanitizeValue", at = @At("HEAD"), cancellable = true)
    private void wips$scaleCap(double value, CallbackInfoReturnable<Double> cir) {
        if ((Object) this == Attributes.SCALE.value()) {
            cir.setReturnValue(Double.isNaN(value) ? 0.0625 : Mth.clamp(value, 0.0625, 32.0));
        }
    }
}
