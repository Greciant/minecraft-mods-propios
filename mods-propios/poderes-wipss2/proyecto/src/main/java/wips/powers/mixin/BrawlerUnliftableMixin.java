package wips.powers.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import wips.powers.fighter.FighterPower;

/** Durante el Reel In del Luchador nada es "demasiado grande para levantar": tambien se arrastra a jefes. */
@Mixin(targets = "com.awakenedmod.awakened.content.brawler.BrawlerTargets", remap = false)
public abstract class BrawlerUnliftableMixin {
    @Inject(method = "unliftable", at = @At("HEAD"), cancellable = true)
    private static void wips$unliftable(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
        if (FighterPower.reeling) {
            cir.setReturnValue(false);
        }
    }
}
