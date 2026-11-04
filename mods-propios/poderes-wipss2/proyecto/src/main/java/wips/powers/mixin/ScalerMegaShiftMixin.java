package wips.powers.mixin;

import com.awakenedmod.awakened.content.scaler.ScalerFx;
import com.awakenedmod.awakened.content.scaler.SizeChangeAbility;
import com.awakenedmod.awakened.content.scaler.Scaling;
import com.awakenedmod.awakened.content.scaler.SizeStage;
import com.awakenedmod.awakened.core.Headroom;
import com.awakenedmod.awakened.core.PowerService;
import com.awakenedmod.awakened.core.data.PowerData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import wips.powers.scaler.ScalerMega;

/** Crecer estando en Colossus sube de nivel mega; encoger baja primero los niveles mega. */
@Mixin(value = SizeChangeAbility.class, remap = false)
public abstract class ScalerMegaShiftMixin {
    @Inject(method = "shift", at = @At("HEAD"), cancellable = true)
    private void wips$mega(ServerPlayer player, int dir, boolean selfCharge, CallbackInfoReturnable<Boolean> cir) {
        PowerData data = PowerService.data(player);
        if (Scaling.stage(data) != SizeStage.COLOSSUS) {
            return;
        }
        int level = ScalerMega.level(data);
        int next = level + (dir > 0 ? 1 : -1);
        if (dir < 0 && level == 0) {
            return; // de Colossus a Titan: lo hace Awakened
        }
        if (next > ScalerMega.max()) {
            player.displayClientMessage(Component.translatable("awakened.scaler.at_top"), true);
            cir.setReturnValue(false);
            return;
        }
        float to = ScalerMega.scale(SizeStage.COLOSSUS, next);
        if (dir > 0 && !Headroom.fits(player, to)) {
            player.displayClientMessage(Component.translatable("awakened.scaler.no_room"), true);
            cir.setReturnValue(false);
            return;
        }
        data.setVar(ScalerMega.VAR, (float) next);
        data.setVar("scaler_last_shift", (float) player.serverLevel().getGameTime());
        ScalerFx.shift(player, dir > 0, Math.max(to, ScalerMega.scale(SizeStage.COLOSSUS, level)));
        player.displayClientMessage(Component.literal(ScalerMega.name(next)), true);
        PowerService.sync(player);
        cir.setReturnValue(true);
    }
}
