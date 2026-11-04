package wips.powers.mixin;

import com.awakenedmod.awakened.core.PowerService;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wips.powers.fx.WipsFx;

/**
 * Punto unico para los VFX de los poderes de Awakened: cuando una habilidad se activa de verdad
 * (justo antes de dar maestria por uso), avisa a los clientes "cast_<poder>" con la ranura.
 */
@Mixin(value = PowerService.class, remap = false)
public abstract class PowerActivateMixin {
    @Inject(method = "activate", at = @At(value = "INVOKE",
            target = "Lcom/awakenedmod/awakened/core/progression/MasterySystem;awardUse(Lnet/minecraft/server/level/ServerPlayer;)V"))
    private static void wips$castFx(ServerPlayer player, int slot, CallbackInfo ci) {
        String power = PowerService.data(player).powerId();
        WipsFx.around(player, "cast_" + power, player.getEyePosition(), player.getLookAngle(), 1.0F, player.getRandom().nextLong(), slot);
    }
}
