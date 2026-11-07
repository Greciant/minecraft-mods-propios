package wips.powers.mixin.impact;

import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ridiculous Impact Frames manda un paquete al atacante en cada golpe sin mirar si es un jugador falso (deployers de
 * Create, maquinas, etc.): su conexion no tiene canal y el servidor se cae con NullPointerException. Aca se saltea
 * el aviso cuando el atacante es un FakePlayer; para jugadores reales no cambia nada.
 */
@Mixin(targets = "com.impactframe.server.AttackDamageFeedback", remap = false)
public abstract class ImpactFakePlayerMixin {
    @Inject(method = "onLivingDamage", at = @At("HEAD"), cancellable = true)
    private static void wips$skipFake(LivingDamageEvent.Post event, CallbackInfo ci) {
        if (event.getSource().getEntity() instanceof FakePlayer) {
            ci.cancel();
        }
    }
}
