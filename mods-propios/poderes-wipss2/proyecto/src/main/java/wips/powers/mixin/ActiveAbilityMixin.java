package wips.powers.mixin;

import com.awakenedmod.awakened.core.PowerService;
import com.awakenedmod.awakened.core.power.Ability;
import com.awakenedmod.awakened.core.power.Power;
import com.awakenedmod.awakened.core.power.PowerRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wips.powers.fx.WipsFx;

/**
 * Efectos continuos: mientras una habilidad esta activa (mantener, canalizar, volar...) cada 3 ticks
 * se avisa "active_<poder>" con la ranura, para que el cliente dibuje su capa sostenida.
 */
@Mixin(targets = "com.awakenedmod.awakened.core.event.AwakenedGameEvents", remap = false)
public abstract class ActiveAbilityMixin {
    private static final int EVERY = 3;

    @Inject(method = "onPlayerTick", at = @At(value = "INVOKE",
            target = "Lcom/awakenedmod/awakened/core/power/Power;passiveTick(Lnet/minecraft/server/level/ServerPlayer;Lcom/awakenedmod/awakened/core/data/PowerData;)V",
            shift = At.Shift.AFTER))
    private static void wips$activeFx(PlayerTickEvent.Post event, CallbackInfo ci) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % EVERY != 0) {
            return;
        }
        Power power = PowerRegistry.get(PowerService.data(player).powerId());
        if (power == null) {
            return;
        }
        for (int i = 0; i < power.abilityCount(); i++) {
            Ability a = power.ability(i);
            if (a != null && a.isActive(player)) {
                WipsFx.around(player, "active_" + power.id(), player.getEyePosition(), player.getLookAngle(), 1.0F,
                        player.getRandom().nextLong(), i);
            }
        }
    }
}
