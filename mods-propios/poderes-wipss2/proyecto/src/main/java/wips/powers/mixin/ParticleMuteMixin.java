package wips.powers.mixin;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import wips.powers.fx.AwakenBurst;

/** Descarta particulas del servidor solo mientras AwakenBurst.MUTE (el instante del estallido viejo del despertar). */
@Mixin(ServerLevel.class)
public abstract class ParticleMuteMixin {
    @Inject(method = "sendParticles(Lnet/minecraft/core/particles/ParticleOptions;DDDIDDDD)I", at = @At("HEAD"), cancellable = true)
    private <T extends ParticleOptions> void wips$mute(T type, double x, double y, double z, int count, double dx, double dy, double dz,
                                                       double speed, CallbackInfoReturnable<Integer> cir) {
        if (AwakenBurst.MUTE) {
            cir.setReturnValue(0);
        }
    }

    @Inject(method = "sendParticles(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/core/particles/ParticleOptions;ZDDDIDDDD)Z",
            at = @At("HEAD"), cancellable = true)
    private <T extends ParticleOptions> void wips$mutePlayer(ServerPlayer player, T type, boolean force, double x, double y, double z, int count,
                                                             double dx, double dy, double dz, double speed, CallbackInfoReturnable<Boolean> cir) {
        if (AwakenBurst.MUTE) {
            cir.setReturnValue(false);
        }
    }
}
