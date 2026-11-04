package wips.powers.mixin;

import com.awakenedmod.awakened.content.AwakenedNpcs;
import com.awakenedmod.awakened.core.npc.NpcKits;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wips.powers.npc.ElementalMageKit;
import wips.powers.npc.MageTheme;

/** Un mago por cada poder obtenible, ademas del de Pyrokinetic que trae Awakened. */
@Mixin(value = AwakenedNpcs.class, remap = false)
public abstract class MageKitsMixin {
    @Inject(method = "registerAll", at = @At("TAIL"))
    private static void wips$mages(CallbackInfo ci) {
        for (MageTheme theme : MageTheme.all()) {
            NpcKits.register(new ElementalMageKit(theme));
        }
    }
}
