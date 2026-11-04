package com.haki.mixin;

import com.haki.client.HakiPose;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Aplica la pose de lanzamiento del Haki al final de setupAnim, cuando el modelo ya tiene su animacion normal. */
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void haki$pose(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                           float netHeadYaw, float headPitch, CallbackInfo ci) {
        HakiPose.apply((PlayerModel<?>) (Object) this, entity, ageInTicks);
    }
}
