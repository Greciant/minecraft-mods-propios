package wips.powers.mixin;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Arreglo de estabilidad: proyectiles con el item de recogida vacio (la Phantom Dagger de Legendary Monsters)
 * tiraban "Cannot encode empty ItemStack" en cada autoguardado y no se guardaban. Se guarda un compuesto vacio;
 * al cargar, vanilla usa el item por defecto del proyectil. Nada cambia para flechas con item.
 */
@Mixin(AbstractArrow.class)
public abstract class ArrowEmptyStackSaveMixin {
    @Redirect(method = "addAdditionalSaveData", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;save(Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/nbt/Tag;"))
    private Tag wips$skipEmpty(ItemStack stack, HolderLookup.Provider registries) {
        return stack.isEmpty() ? new CompoundTag() : stack.save(registries);
    }
}
