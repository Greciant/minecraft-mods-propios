package wips.powers.mixin.compat;

import java.util.function.BiConsumer;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wips.powers.compat.WeaponLevelCaps;

/** Recuerda que item esta leyendo sus modificadores en este hilo: Weapon Leveling calcula el bonus justo despues. */
@Mixin(ItemStack.class)
public abstract class StackModifierTrackMixin {
    @Inject(method = "forEachModifier(Lnet/minecraft/world/entity/EquipmentSlot;Ljava/util/function/BiConsumer;)V", at = @At("HEAD"))
    private void wips$track(EquipmentSlot slot, BiConsumer<Holder<Attribute>, AttributeModifier> action, CallbackInfo ci) {
        WeaponLevelCaps.CURRENT.set((ItemStack) (Object) this);
    }

    @Inject(method = "forEachModifier(Lnet/minecraft/world/entity/EquipmentSlotGroup;Ljava/util/function/BiConsumer;)V", at = @At("HEAD"))
    private void wips$trackGroup(EquipmentSlotGroup slot, BiConsumer<Holder<Attribute>, AttributeModifier> action, CallbackInfo ci) {
        WeaponLevelCaps.CURRENT.set((ItemStack) (Object) this);
    }
}
