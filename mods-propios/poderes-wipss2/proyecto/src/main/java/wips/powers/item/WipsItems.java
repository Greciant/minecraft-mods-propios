package wips.powers.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import wips.powers.WipsPowers;

/** Items propios: el amuleto de estamina (3 niveles). */
public final class WipsItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(WipsPowers.MOD_ID);

    public static final DeferredItem<StaminaCharm> STAMINA_CHARM_1 = ITEMS.registerItem("stamina_charm_1",
            p -> new StaminaCharm(p.stacksTo(1).rarity(Rarity.UNCOMMON), 0.25F));
    public static final DeferredItem<StaminaCharm> STAMINA_CHARM_2 = ITEMS.registerItem("stamina_charm_2",
            p -> new StaminaCharm(p.stacksTo(1).rarity(Rarity.RARE), 0.5F));
    public static final DeferredItem<StaminaCharm> STAMINA_CHARM_3 = ITEMS.registerItem("stamina_charm_3",
            p -> new StaminaCharm(p.stacksTo(1).rarity(Rarity.EPIC), 1.0F));

    private WipsItems() {
    }

    /** Amuleto: con llevarlo encima (inventario, mano secundaria o el collar de Curios) regenera mas rapido la energia del poder. */
    public static final class StaminaCharm extends Item {
        private final float bonus;

        public StaminaCharm(Properties properties, float bonus) {
            super(properties);
            this.bonus = bonus;
        }

        public float bonus() {
            return this.bonus;
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("item.wipspowers.stamina_charm.tip", Math.round(this.bonus * 100)).withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.translatable("item.wipspowers.stamina_charm.tip2").withStyle(ChatFormatting.GRAY));
        }

        @Override
        public boolean isFoil(ItemStack stack) {
            return this.bonus >= 1.0F;
        }
    }
}
