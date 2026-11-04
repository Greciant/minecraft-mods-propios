package wips.powers.mixin;

import com.devilfruits.fruits.uo_uo_seiryu.client.vfx.FlameCloudCinematic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Flame Cloud: el humo que acompana la embestida tapaba al jugador; lo hacemos mas transparente. */
@Mixin(value = FlameCloudCinematic.class, remap = false)
public abstract class FlameCloudSmokeMixin {
    @ModifyConstant(method = "tickRide", constant = @Constant(floatValue = 0.75F))
    private float wips$rideSmoke(float v) {
        return 0.28F;
    }

    @ModifyConstant(method = "pushOff", constant = @Constant(floatValue = 0.8F))
    private float wips$startSmoke(float v) {
        return 0.45F;
    }
}
