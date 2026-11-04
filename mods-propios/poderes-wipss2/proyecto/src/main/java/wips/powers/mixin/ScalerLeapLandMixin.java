package wips.powers.mixin;

import com.awakenedmod.awakened.content.scaler.MassLeapAbility;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wips.powers.scaler.ScalerWaves;

/** Mass Leap: guarda la altura maxima del vuelo y al aterrizar suelta la onda gigante. */
@Mixin(value = MassLeapAbility.class, remap = false)
public abstract class ScalerLeapLandMixin {
    @Inject(method = "tickFlight", at = @At("HEAD"))
    private void wips$peak(ServerPlayer player, CallbackInfo ci) {
        ScalerWaves.LEAP_PEAK.merge(player.getUUID(), player.getY(), Math::max);
    }

    @Redirect(method = "tickFlight", at = @At(value = "INVOKE", target = "Ljava/util/Map;remove(Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object wips$landed(Map<?, ?> map, Object key, ServerPlayer player) {
        Object flight = map.remove(key);
        if (flight != null && (player.onGround() || player.isInWater())) {
            ScalerWaves.leapLanded(player);
        } else {
            ScalerWaves.LEAP_PEAK.remove(player.getUUID());
        }
        return flight;
    }
}
