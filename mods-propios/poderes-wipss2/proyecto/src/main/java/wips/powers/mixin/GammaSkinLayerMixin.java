package wips.powers.mixin;

import com.awakenedmod.awakened.client.render.GammaSkinLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Gamma: sin el tinte verde de la piel al enfurecerse/crecer. */
@Mixin(value = GammaSkinLayer.class, remap = false)
public abstract class GammaSkinLayerMixin {
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;FFFFFF)V",
            at = @At("HEAD"), cancellable = true)
    private void wips$noGreen(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                              float a, float b, float c, float d, float e, float f, CallbackInfo ci) {
        ci.cancel();
    }
}
