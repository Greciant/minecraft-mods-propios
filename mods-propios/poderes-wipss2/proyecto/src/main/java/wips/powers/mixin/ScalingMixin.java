package wips.powers.mixin;

import com.awakenedmod.awakened.content.scaler.ScalerFx;
import com.awakenedmod.awakened.content.scaler.Scaling;
import com.awakenedmod.awakened.content.scaler.SizeStage;
import com.awakenedmod.awakened.core.data.PowerData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wips.powers.fx.WipsFx;

/**
 * Scaler: al encoger no se pierde velocidad (pedido original) y en fase gigante se camina MAS rapido
 * para que se sienta fluido (un gigante a velocidad normal se veia lentisimo). Ademas, cada pisada
 * gigante manda su onda de impacto visual.
 */
@Mixin(value = Scaling.class, remap = false)
public abstract class ScalingMixin {
    /** Multiplicador de velocidad por fase gigante (1.0 = velocidad normal del personaje). */
    private static final float BRUTE_SPEED = 1.2F;
    private static final float TITAN_SPEED = 1.45F;
    private static final float COLOSSUS_SPEED = 1.75F;

    @Redirect(method = "applyAttributes", at = @At(value = "INVOKE",
            target = "Lcom/awakenedmod/awakened/content/scaler/SizeStage;speedMult()F"))
    private static float wips$stageSpeed(SizeStage stage) {
        return switch (stage) {
            case BRUTE -> BRUTE_SPEED;
            case TITAN -> TITAN_SPEED;
            case COLOSSUS -> wips.powers.scaler.ScalerMega.current > 0 ? wips.powers.scaler.ScalerMega.speed(wips.powers.scaler.ScalerMega.current) : COLOSSUS_SPEED;
            default -> 1.0F;
        };
    }

    /** Pisada gigante: el sonido del mp3 del usuario en vez del paso de ravager. */
    @org.spongepowered.asm.mixin.injection.ModifyArg(method = "tickFootsteps", at = @At(value = "INVOKE",
            target = "Lcom/awakenedmod/awakened/core/Fx;sound(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V"), index = 2)
    private static net.minecraft.sounds.SoundEvent wips$giantStep(net.minecraft.sounds.SoundEvent original) {
        return wips.powers.WipsSounds.GIANT_STEP.get();
    }

    @org.spongepowered.asm.mixin.injection.ModifyArg(method = "tickFootsteps", at = @At(value = "INVOKE",
            target = "Lcom/awakenedmod/awakened/core/Fx;sound(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V"), index = 5)
    private static float wips$giantStepPitch(float pitch) {
        return Math.min(1.1F, pitch * 1.35F);
    }

    @Inject(method = "tickFootsteps", at = @At(value = "INVOKE",
            target = "Lcom/awakenedmod/awakened/content/scaler/ScalerFx;screenShake(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;DI)V"))
    private static void wips$stepFx(ServerPlayer player, PowerData data, SizeStage stage, CallbackInfo ci) {
        WipsFx.around(player, "scaler_step", player.position(), Vec3.ZERO, stage.scale(), player.getRandom().nextLong(), stage.ordinal());
    }
}
