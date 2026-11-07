package wips.powers.mixin;

import com.awakenedmod.awakened.core.npc.NpcConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Magos hasta maestria 25. */
@Mixin(value = NpcConfig.class, remap = false)
public abstract class NpcMasteryCapMixin {
    @Inject(method = "maxMastery", at = @At("RETURN"), cancellable = true)
    private static void wips$cap25(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(25);
    }
}
