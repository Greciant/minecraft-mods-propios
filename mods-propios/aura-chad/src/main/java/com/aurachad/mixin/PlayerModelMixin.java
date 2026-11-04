package com.aurachad.mixin;

import com.aurachad.client.ChadPoses;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Aplica las poses chad al final de setupAnim, cuando el modelo ya tiene su animacion normal. */
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void aurachad$pose(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                               float netHeadYaw, float headPitch, CallbackInfo ci) {
        ChadPoses.apply((PlayerModel<?>) (Object) this, entity);
        com.aurachad.client.bend.ChadBendBridge.apply((PlayerModel<?>) (Object) this, entity);
    }
}
