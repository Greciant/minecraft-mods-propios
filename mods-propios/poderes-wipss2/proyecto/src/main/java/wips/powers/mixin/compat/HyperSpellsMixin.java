package wips.powers.mixin.compat;

import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import wips.powers.content.HyperFilter;

/** Iron's Spells: los hechizos quitados de Hyper 'N Hiei quedan deshabilitados (sin pergaminos, loot ni inscripcion). */
@Mixin(value = AbstractSpell.class, remap = false)
public abstract class HyperSpellsMixin {
    @Inject(method = "isEnabled", at = @At("HEAD"), cancellable = true)
    private void wips$removed(CallbackInfoReturnable<Boolean> cir) {
        if (HyperFilter.removedSpell(((AbstractSpell) (Object) this).getSpellResource())) {
            cir.setReturnValue(false);
        }
    }
}
