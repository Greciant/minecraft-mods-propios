package wips.powers.mixin;

import com.awakenedmod.awakened.core.caster.PlayerCaster;
import org.spongepowered.asm.mixin.Mixin;
import wips.powers.level.WipsScaling;

/**
 * Habilidades que calculan el dano via Caster (jugador o NPC): para el jugador, sin maestria/AP (el nivel se aplica
 * en WipsScaling). Reemplaza el metodo por defecto de la interfaz Caster solo en PlayerCaster; NpcCaster no cambia.
 */
@Mixin(value = PlayerCaster.class, remap = false)
public abstract class PlayerCasterScalingMixin {
    public float scaledDamage(float base, String abilityKey, float... extraBonusFractions) {
        return WipsScaling.neutral(base, abilityKey, extraBonusFractions);
    }
}
