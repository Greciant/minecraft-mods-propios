package wips.powers.mixin;

import com.awakenedmod.awakened.content.pyrokinetic.FlameTone;
import com.awakenedmod.awakened.content.pyrokinetic.PyroFlames;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wips.powers.fx.WipsFx;

/** Pyrokinetic: cada explosion de fuego manda su anillo de ignicion y onda de calor. */
@Mixin(value = PyroFlames.class, remap = false)
public abstract class PyroBurstMixin {
    @Inject(method = "burst(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;DLcom/awakenedmod/awakened/content/pyrokinetic/FlameTone;)V", at = @At("HEAD"))
    private static void wips$burst(ServerLevel level, Vec3 center, double radius, FlameTone tone, CallbackInfo ci) {
        WipsFx.at(level, -1, "pyro_burst", center, new Vec3(0, 1, 0), (float) radius, level.random.nextLong(), Math.round(tone.blueFraction() * 100));
    }

    @Inject(method = {"burst(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;DLcom/awakenedmod/awakened/content/pyrokinetic/FlameTone;Lnet/minecraft/world/phys/Vec3;)V",
            "burstSilent"}, at = @At("HEAD"))
    private static void wips$burstNormal(ServerLevel level, Vec3 center, double radius, FlameTone tone, Vec3 normal, CallbackInfo ci) {
        WipsFx.at(level, -1, "pyro_burst", center, normal == null ? new Vec3(0, 1, 0) : normal, (float) radius, level.random.nextLong(), Math.round(tone.blueFraction() * 100));
    }
}
