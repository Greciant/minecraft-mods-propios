package wips.powers.mixin;

import com.awakenedmod.awakened.core.power.Power;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wips.powers.fx.AwakenBurst;

/**
 * Un solo efecto al obtener un poder: el estallido de Awakened y el onAwaken de cada poder siguen corriendo (titulo,
 * sonidos, avisos, logica), pero sus particulas, rayos y vfx se descartan (AwakenBurst.MUTE) y al final se
 * reproduce AwakenBurst. El "asentamiento" de particulas posterior tambien se quita.
 */
@Mixin(targets = "com.awakenedmod.awakened.core.awaken.AwakeningCinematic", remap = false)
public abstract class AwakeningCinematicMixin {
    @Inject(method = "erupt", at = @At("HEAD"))
    private static void wips$muteStart(ServerPlayer player, Power power, CallbackInfo ci) {
        AwakenBurst.MUTE = true;
    }

    @Inject(method = "erupt", at = @At("TAIL"))
    private static void wips$muteEnd(ServerPlayer player, Power power, CallbackInfo ci) {
        AwakenBurst.MUTE = false;
        AwakenBurst.play(player, power);
    }

    @Inject(method = "settle", at = @At("HEAD"), cancellable = true)
    private static void wips$settle(ServerPlayer player, Power power, int t, CallbackInfo ci) {
        ci.cancel();
    }
}
