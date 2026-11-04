package wips.powers.mixin;

import com.devilfruits.client.render.DebrisRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Escombros de Grand Line: trozos mas chicos (0.92 -> 0.6) para que no tapen el golpe. */
@Mixin(value = DebrisRenderer.class, remap = false)
public abstract class DebrisScaleMixin {
    @ModifyConstant(method = "render(Lcom/devilfruits/core/world/DebrisEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            constant = @Constant(floatValue = 0.92F))
    private float wips$smallerDebris(float v) {
        return 0.6F;
    }
}
