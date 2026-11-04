package wips.powers.mixin;

import com.awakenedmod.awakened.content.scaler.SeizeControl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Clamber: con mas alcance, el impulso hacia el objetivo necesita mas tiempo antes de rendirse. */
@Mixin(value = SeizeControl.class, remap = false)
public abstract class ClamberDashMixin {
    @ModifyConstant(method = "tickDash", constant = @Constant(intValue = 20))
    private static int wips$longerDash(int ticks) {
        return 70;
    }
}
