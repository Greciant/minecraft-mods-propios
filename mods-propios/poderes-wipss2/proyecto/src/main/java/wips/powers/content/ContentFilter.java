package wips.powers.content;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import wips.powers.item.WipsItems;

/**
 * Contenido quitado del pack: no sale en ninguna pestana del creativo ni en la busqueda. Para JEI/EMI estan
 * en el tag c:hidden_from_recipe_viewers (data/c/tags/item). Los items siguen registrados para no romper
 * mundos guardados, pero ya no tienen receta, ni loot, ni spawn.
 */
public final class ContentFilter {
    /** Awakened: solo quedan los catalizadores (orbes para cambiar de poder). */
    private static final Set<String> AWAKENED_KEEP = Set.of(
            "catalyst", "focused_catalyst", "honed_catalyst", "refined_catalyst", "precise_catalyst", "perfect_catalyst");

    private ContentFilter() {
    }

    public static boolean hidden(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        String ns = id.getNamespace();
        if (ns.equals("awakened")) {
            return !AWAKENED_KEEP.contains(id.getPath());
        }
        if (ns.equals(LegendaryMonstersFilter.MOD)) {
            return LegendaryMonstersFilter.hiddenItem(item, id.getPath());
        }
        return false;
    }

    public static void onBuildTabs(BuildCreativeModeTabContentsEvent event) {
        removeFrom(event, event.getParentEntries(), CreativeModeTab.TabVisibility.PARENT_TAB_ONLY);
        removeFrom(event, event.getSearchEntries(), CreativeModeTab.TabVisibility.SEARCH_TAB_ONLY);
        ResourceLocation key = event.getTabKey().location();
        if (key.getNamespace().equals("awakened")) {
            event.accept(WipsItems.STAMINA_CHARM_1.get());
            event.accept(WipsItems.STAMINA_CHARM_2.get());
            event.accept(WipsItems.STAMINA_CHARM_3.get());
        }
    }

    private static void removeFrom(BuildCreativeModeTabContentsEvent event, Iterable<ItemStack> entries, CreativeModeTab.TabVisibility vis) {
        List<ItemStack> drop = new ArrayList<>();
        for (ItemStack s : entries) {
            Item item = s.getItem();
            boolean egg = item instanceof SpawnEggItem e && LegendaryMonstersFilter.hiddenEntity(e.getType(s));
            if (hidden(item) || egg) {
                drop.add(s);
            }
        }
        for (ItemStack s : drop) {
            event.remove(s, vis);
        }
    }
}
