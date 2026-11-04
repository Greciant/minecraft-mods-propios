package wips.powers.mixin;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import wips.powers.combat.WipsDamage;

/**
 * Pyrokinetic: Flame Beam, Backdraft y el suelo ardiendo danaban con ON_FIRE (en #is_fire), que los mobs
 * inmunes al fuego ignoran: por eso el rayo "no hacia dano" a muchos mobs de otros mods. Se cambia por
 * wipspowers:ability_fire (mismo mensaje y quemadura, sin la inmunidad).
 */
public final class PyroFireDamageMixin {
    private static final String ON_FIRE = "Lnet/minecraft/world/damagesource/DamageTypes;ON_FIRE:Lnet/minecraft/resources/ResourceKey;";

    private PyroFireDamageMixin() {
    }

    @Mixin(targets = "com.awakenedmod.awakened.content.pyrokinetic.FlameBeamEngine", remap = false)
    public abstract static class Beam {
        @Redirect(method = "tick", at = @At(value = "FIELD", target = ON_FIRE))
        private static ResourceKey<DamageType> wips$abilityFire() {
            return WipsDamage.ABILITY_FIRE;
        }
    }

    @Mixin(targets = "com.awakenedmod.awakened.content.pyrokinetic.BackdraftEngine", remap = false)
    public abstract static class Backdraft {
        @Redirect(method = "reignite", at = @At(value = "FIELD", target = ON_FIRE))
        private static ResourceKey<DamageType> wips$abilityFire() {
            return WipsDamage.ABILITY_FIRE;
        }
    }

    @Mixin(targets = "com.awakenedmod.awakened.content.pyrokinetic.PyroBurningGround$Bill", remap = false)
    public abstract static class Ground {
        @Redirect(method = "apply", at = @At(value = "FIELD", target = ON_FIRE))
        private ResourceKey<DamageType> wips$abilityFire() {
            return WipsDamage.ABILITY_FIRE;
        }
    }
}
