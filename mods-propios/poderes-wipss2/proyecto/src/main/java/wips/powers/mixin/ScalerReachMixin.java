package wips.powers.mixin;

import com.awakenedmod.awakened.content.scaler.ClamberSeizeAbility;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Clamber/Seize: el alcance de agarre era fijo (9-10 bloques desde los ojos); en las fases mega los
 * ojos quedan a decenas de bloques y nada entraba. Ahora escala con el tamano del jugador.
 */
@Mixin(value = ClamberSeizeAbility.class, remap = false)
public abstract class ScalerReachMixin {
    private static final String PICK = "Lcom/awakenedmod/awakened/core/AimUtil;aimedLivingSoftPick(Lnet/minecraft/server/level/ServerPlayer;DDLjava/util/function/Predicate;)Lcom/awakenedmod/awakened/core/AimUtil$SoftPick;";

    @ModifyArg(method = {"trySeize", "tryClamber"}, at = @At(value = "INVOKE", target = PICK), index = 1)
    private double wips$reach(ServerPlayer player, double range, double cone, java.util.function.Predicate<?> filter) {
        float s = player.getScale();
        return s <= 5.0F ? range : range * (s / 5.0F) * 1.1;
    }
}
