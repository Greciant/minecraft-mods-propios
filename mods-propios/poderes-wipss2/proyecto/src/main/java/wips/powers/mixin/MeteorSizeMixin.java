package wips.powers.mixin;

import com.devilfruits.client.vfx.VfxParams;
import com.devilfruits.fruits.uo_uo_seiryu.client.vfx.MeteorCinematic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Bolo: meteoros y bolas de fuego con mas cuerpo (solo visual; el golpe no cambia). */
@Mixin(value = MeteorCinematic.class, remap = false)
public abstract class MeteorSizeMixin {
    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/devilfruits/client/vfx/VfxParams;power()F"))
    private float wips$biggerBall(VfxParams params) {
        return params.power() * 1.5F;
    }
}
