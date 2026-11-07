package wips.powers.mixin;

import com.awakenedmod.awakened.core.ItemGrant;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wips.powers.content.ItemPurge;

/** Toda entrega directa de Awakened (recompensas, reliquias, admin) pasa por ItemGrant.to: lo quitado no llega. */
@Mixin(value = ItemGrant.class, remap = false)
public abstract class ItemGrantPurgeMixin {
    @Inject(method = "to", at = @At("HEAD"), cancellable = true)
    private static void wips$to(Player player, ItemStack stack, CallbackInfo ci) {
        if (ItemPurge.banned(stack)) {
            ItemPurge.count(stack);
            ci.cancel();
        }
    }
}
