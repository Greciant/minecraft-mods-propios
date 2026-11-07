package wips.powers.mixin.compat;

import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import java.util.Collection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import wips.powers.content.HyperFilter;

/** La pestana creativa de Hyper 'N Hiei no muestra pergaminos de los hechizos quitados. */
@Mixin(targets = "com.hypernhieis.spells.SpellScrolls", remap = false)
public abstract class HyperScrollTabMixin {
    @ModifyVariable(method = "fill", at = @At("HEAD"), argsOnly = true)
    private static Collection<? extends AbstractSpell> wips$onlyKept(Collection<? extends AbstractSpell> spells) {
        return spells.stream().filter(s -> !HyperFilter.removedSpell(s.getSpellResource())).toList();
    }
}
