package wips.powers.mixin.impact;

import com.impactframe.client.ImpactFrameClient;
import com.impactframe.client.ImpactTier;
import com.impactframe.client.ImpactType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import wips.powers.crit.ClientCrit;

/** Ridiculous Impact Frames: solo dispara cuando wipspowers marca un critico (nada automatico). */
@Mixin(value = ImpactFrameClient.class, remap = false)
public abstract class ImpactFireGateMixin {
    @Inject(method = "fire(Lcom/impactframe/client/ImpactType;Lcom/impactframe/client/ImpactTier;FLnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;FZ)Z",
            at = @At("HEAD"), cancellable = true)
    private static void wips$onlyCrits(ImpactType type, ImpactTier tier, float scale, Entity focus, Vec3 pos, float half, boolean bars,
                                       CallbackInfoReturnable<Boolean> cir) {
        if (!ClientCrit.allow) {
            cir.setReturnValue(false);
        }
    }
}
