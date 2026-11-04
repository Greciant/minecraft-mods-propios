package wips.powers.mixin;

import com.awakenedmod.awakened.core.power.Power;
import com.awakenedmod.awakened.core.power.PowerRegistry;
import java.util.Collection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import wips.powers.WipsPowers;

@Mixin(value = PowerRegistry.class, remap = false)
public abstract class PowerRegistryMixin {
    @Inject(method = "all", at = @At("RETURN"), cancellable = true)
    private static void wips$all(CallbackInfoReturnable<Collection<Power>> cir) {
        cir.setReturnValue(cir.getReturnValue().stream().filter(p -> WipsPowers.KEEP.contains(p.id())).toList());
    }

    @Inject(method = "obtainable", at = @At("HEAD"), cancellable = true)
    private static void wips$obtainable(Power power, CallbackInfoReturnable<Boolean> cir) {
        if (!WipsPowers.KEEP.contains(power.id())) {
            cir.setReturnValue(false);
        }
    }
}
