package wips.powers.mixin;

import com.awakenedmod.awakened.client.graviton.dense.DenseHoldRenderer;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Graviton ranura 4: se quita el aura original; la reemplazan las lineas de presion (GravitonClient). */
@Mixin(value = DenseHoldRenderer.class, remap = false)
public abstract class DenseAuraMixin {
    @Inject(method = "onRender", at = @At("HEAD"), cancellable = true)
    private static void wips$noAura(RenderLevelStageEvent event, CallbackInfo ci) {
        ci.cancel();
    }
}
