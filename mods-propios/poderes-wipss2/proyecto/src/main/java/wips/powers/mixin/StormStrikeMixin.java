package wips.powers.mixin;

import com.awakenedmod.awakened.content.stormcaller.StormSupport;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import wips.powers.fx.WipsFx;

/** Storm Caller: cada rayo que cae manda su descarga ramificada y la onda estatica en el suelo. */
@Mixin(value = StormSupport.class, remap = false)
public abstract class StormStrikeMixin {
    @Inject(method = "strikeResult", at = @At("HEAD"))
    private static void wips$strike(ServerLevel level, ServerPlayer cause, Vec3 pos, float damage, double radius,
                                    CallbackInfoReturnable<StormSupport.Result> cir) {
        WipsFx.at(level, cause == null ? -1 : cause.getId(), "storm_strike", pos, Vec3.ZERO, (float) radius, level.random.nextLong(), 0);
    }
}
