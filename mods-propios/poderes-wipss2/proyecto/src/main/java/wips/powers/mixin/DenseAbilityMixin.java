package wips.powers.mixin;

import com.awakenedmod.awakened.content.graviton.DenseAbility;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import wips.powers.graviton.PressureField;

/** Graviton ranura 4: el mantener de Dense ahora tambien abre el area de presion (PressureField). */
@Mixin(value = DenseAbility.class, remap = false)
public abstract class DenseAbilityMixin {
    @Shadow
    @Final
    private Map<UUID, ?> holds;

    @Inject(method = "activate", at = @At("RETURN"))
    private void wips$open(ServerPlayer player, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            PressureField.start(player);
        }
    }

    @Inject(method = "serverTick", at = @At("HEAD"))
    private void wips$press(ServerPlayer player, CallbackInfo ci) {
        if (this.holds.containsKey(player.getUUID())) {
            PressureField.tick(player);
        }
    }

    @Inject(method = "release", at = @At("HEAD"))
    private void wips$close(ServerPlayer player, CallbackInfo ci) {
        PressureField.stop(player);
    }

    @Inject(method = "clearPlayer", at = @At("HEAD"))
    private void wips$clear(ServerPlayer player, CallbackInfo ci) {
        PressureField.stop(player);
    }
}
