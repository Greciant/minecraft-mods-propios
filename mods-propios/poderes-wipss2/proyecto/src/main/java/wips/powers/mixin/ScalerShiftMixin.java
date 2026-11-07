package wips.powers.mixin;

import com.awakenedmod.awakened.content.scaler.ScalerFx;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wips.powers.fx.WipsFx;

/** Scaler: cada cambio de tamano dispara la distorsion visual (anillos que se abren o se cierran). */
@Mixin(value = ScalerFx.class, remap = false)
public abstract class ScalerShiftMixin {
    @Inject(method = "shift", at = @At("TAIL"))
    private static void wips$shiftFx(ServerPlayer player, boolean growing, double silhouetteScale, CallbackInfo ci) {
        if (growing && silhouetteScale >= 2.0) { // al hacerse gigante, primera pisada
            player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), wips.powers.WipsSounds.GIANT_STEP.get(),
                    net.minecraft.sounds.SoundSource.PLAYERS, 1.2F, silhouetteScale >= 4.5 ? 0.8F : 0.95F);
        }
        if (growing) {
            wips.powers.scaler.ScalerWaves.growWave(player, silhouetteScale); // Titan o mas: la onda golpea
        }
        WipsFx.around(player, "scaler_shift", player.position(), Vec3.ZERO, (float) silhouetteScale, player.getRandom().nextLong(), growing ? 1 : 0);
    }
}
