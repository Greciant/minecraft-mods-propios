package wips.powers.mixin;

import com.devilfruits.client.vfx.VfxSettings;
import com.devilfruits.core.config.CoreClientConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Grand Line baja TODOS sus efectos a la calidad LOW (los viejos) en cuanto detecta un shader de
 * Iris activo. Por eso en WIPSS2 (con shaders) se veian los efectos de siempre y en el arnes de
 * prueba (sin shaders) los nuevos. Aqui se respeta la calidad elegida en la config, con tope HIGH
 * cuando hay shader (ULTRA agrega distorsion de lente que choca con algunos shaders).
 */
@Mixin(value = VfxSettings.class, remap = false)
public abstract class VfxShaderTierMixin {
    @Inject(method = "tier", at = @At("HEAD"), cancellable = true)
    private static void wips$keepQualityWithShaders(CallbackInfoReturnable<CoreClientConfig.CinematicQuality> cir) {
        CoreClientConfig.CinematicQuality chosen = CoreClientConfig.CINEMATIC_QUALITY.get();
        if (VfxSettings.shaderPackActive() && chosen == CoreClientConfig.CinematicQuality.ULTRA) {
            cir.setReturnValue(CoreClientConfig.CinematicQuality.HIGH);
        } else {
            cir.setReturnValue(chosen);
        }
    }
}
