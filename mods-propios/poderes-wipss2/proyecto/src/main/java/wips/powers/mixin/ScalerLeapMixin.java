package wips.powers.mixin;

import com.awakenedmod.awakened.content.scaler.MassLeapAbility;
import com.awakenedmod.awakened.content.scaler.SeizeControl;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mass Leap: bastante mas impulso (horizontal y sobre todo vertical), creciendo con el tamano. */
@Mixin(value = MassLeapAbility.class, remap = false)
public abstract class ScalerLeapMixin {
    /** Multiplicador base del salto y empuje vertical extra (por raiz de la escala). */
    private static final double POWER = 1.8;
    private static final double LIFT = 0.45;
    private static final double LIFT_PER_ROOT_SCALE = 0.22;

    @Inject(method = "launch", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V", shift = At.Shift.AFTER))
    private void wips$strongerLeap(ServerPlayer player, @org.spongepowered.asm.mixin.injection.Coerce Object charge, CallbackInfo ci) {
        double s = Math.max(1.0, player.getScale());
        double grow = 1.0 + 0.25 * Math.log(s);
        Vec3 v = player.getDeltaMovement().scale(POWER * grow);
        player.setDeltaMovement(v.x, v.y + LIFT + LIFT_PER_ROOT_SCALE * Math.sqrt(s), v.z);
        player.hurtMarked = true;
    }
}
