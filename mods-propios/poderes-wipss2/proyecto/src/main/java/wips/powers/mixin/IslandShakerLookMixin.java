package wips.powers.mixin;

import com.devilfruits.fruits.gura_gura.client.vfx.ShimaCinematic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Island Shaker: onda y anillo del temblor mas visibles, y polvo con mas cuerpo. */
@Mixin(value = ShimaCinematic.class, remap = false)
public abstract class IslandShakerLookMixin {
    @ModifyArg(method = "tick", at = @At(value = "INVOKE",
            target = "Lcom/devilfruits/client/vfx/fx/GroundWave;ring(IF)Lcom/devilfruits/client/vfx/fx/GroundWave;"), index = 1)
    private float wips$ringAlpha(float alpha) {
        return 0.5F;
    }

    @ModifyArg(method = "tick", at = @At(value = "INVOKE",
            target = "Lcom/devilfruits/client/vfx/fx/GroundWave;dust(IIFFFJ)Lcom/devilfruits/client/vfx/fx/GroundWave;"), index = 0)
    private int wips$dustCount(int count) {
        return 42;
    }

    @ModifyArg(method = "tick", at = @At(value = "INVOKE",
            target = "Lcom/devilfruits/client/vfx/fx/GroundWave;dust(IIFFFJ)Lcom/devilfruits/client/vfx/fx/GroundWave;"), index = 4)
    private float wips$dustSize(float size) {
        return 2.4F;
    }

    @ModifyArg(method = "tick", at = @At(value = "INVOKE",
            target = "Lcom/devilfruits/client/vfx/fx/ShockRing;rim(FI)Lcom/devilfruits/client/vfx/fx/ShockRing;", ordinal = 0), index = 0)
    private float wips$shockRim(float alpha) {
        return 0.3F;
    }
}
