package wips.powers.mixin;

import com.devilfruits.fruits.uo_uo_seiryu.client.SeiryuClientFx;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Blast Breath hibrido: rayo algo mas delgado y definido (1.7 -> 1.25). */
@Mixin(value = SeiryuClientFx.class, remap = false)
public abstract class BreathWidthMixin {
    @ModifyConstant(method = "breath", constant = @Constant(floatValue = 1.7F))
    private static float wips$thinnerBeam(float v) { return 1.25F; }
}
