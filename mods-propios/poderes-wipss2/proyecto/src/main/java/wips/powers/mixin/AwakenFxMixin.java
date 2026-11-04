package wips.powers.mixin;

import com.awakenedmod.awakened.core.power.Power;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wips.powers.npc.AwakenFx;

/** Efecto propio al despertar cada poder (sobre el estallido de Awakened). */
@Mixin(targets = "com.awakenedmod.awakened.core.awaken.AwakeningCinematic", remap = false)
public abstract class AwakenFxMixin {
    @Inject(method = "erupt", at = @At("TAIL"))
    private static void wips$erupt(ServerPlayer player, Power power, CallbackInfo ci) {
        AwakenFx.erupt(player, power);
    }
}
