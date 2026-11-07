package wips.powers.mixin.compat;

import net.weaponleveling.api.LevelingAPI;
import net.weaponleveling.data.levelable_item.LevelableAttribute;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import wips.powers.compat.WeaponLevelCaps;

/** Lukas' Weapon Leveling: el bonus por nivel no pasa los topes del pack (ver WeaponLevelCaps). */
@Mixin(value = LevelingAPI.class, remap = false)
public abstract class WeaponLevelCapMixin {
    @Inject(method = "calculateModifierAmount", at = @At("RETURN"), cancellable = true)
    private static void wips$cap(double originalAmount, int level, LevelableAttribute att, CallbackInfoReturnable<Double> cir) {
        double capped = WeaponLevelCaps.cap(att.attribute(), originalAmount, cir.getReturnValue());
        if (capped != cir.getReturnValue()) {
            cir.setReturnValue(capped);
        }
    }
}
