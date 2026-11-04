package wips.powers.mixin;

import com.awakenedmod.awakened.content.scaler.Scaling;
import com.awakenedmod.awakened.content.scaler.SizeStage;
import com.awakenedmod.awakened.core.PowerService;
import com.awakenedmod.awakened.core.data.PowerData;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import wips.powers.scaler.ScalerMega;

/** Aplica el multiplicador mega a la escala objetivo y maneja el colapso por falta de espacio/aguante. */
@Mixin(value = Scaling.class, remap = false)
public abstract class ScalingMegaMixin {
    @Inject(method = "update", at = @At("HEAD"))
    private static void wips$readMega(ServerPlayer player, PowerData data, CallbackInfo ci) {
        ScalerMega.current = Scaling.stage(data) == SizeStage.COLOSSUS ? ScalerMega.level(data) : 0;
    }

    @Redirect(method = "update", at = @At(value = "INVOKE", target = "Lcom/awakenedmod/awakened/content/scaler/SizeStage;scale()F"))
    private static float wips$megaScale(SizeStage stage) {
        return ScalerMega.scale(stage, ScalerMega.current);
    }

    /** Si falta espacio o aguante estando en mega, baja un nivel mega en vez de saltar a Titan. */
    @Inject(method = "collapseOneStage", at = @At("HEAD"), cancellable = true)
    private static void wips$collapseMega(ServerPlayer player, PowerData data, SizeStage from, String reasonKey,
                                          CallbackInfoReturnable<SizeStage> cir) {
        int level = ScalerMega.level(data);
        if (from == SizeStage.COLOSSUS && level > 0) {
            data.setVar(ScalerMega.VAR, (float) (level - 1));
            ScalerMega.current = level - 1;
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(reasonKey), true);
            PowerService.sync(player);
            cir.setReturnValue(from);
        }
    }

    @Inject(method = "clear", at = @At("TAIL"))
    private static void wips$clearMega(ServerPlayer player, CallbackInfo ci) {
        PowerService.data(player).setVar(ScalerMega.VAR, 0.0F);
        ScalerMega.current = 0;
    }
}
