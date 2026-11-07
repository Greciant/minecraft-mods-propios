package wips.powers.mixin;

import com.awakenedmod.awakened.core.progression.MasteryScaling;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import wips.powers.level.WipsScaling;

/**
 * Dano de poderes del JUGADOR: sin el bonus de maestria ni ability power aca (solo los bonus propios del golpe).
 * El escalado se aplica una sola vez en WipsScaling (nivel de jugador). Los NPC no pasan por este metodo.
 */
@Mixin(value = MasteryScaling.class, remap = false)
public abstract class MasteryScalingMixin {
    @Inject(method = "damagePowerMultiplier(Lnet/minecraft/server/level/ServerPlayer;[F)F", at = @At("HEAD"), cancellable = true)
    private static void wips$player(ServerPlayer player, float[] extras, CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(WipsScaling.extrasMultiplier(extras));
    }
}
