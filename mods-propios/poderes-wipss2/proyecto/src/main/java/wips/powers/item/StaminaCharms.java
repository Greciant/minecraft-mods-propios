package wips.powers.item;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/** Bono de regeneracion del mejor amuleto que lleve el jugador (no se suman). */
public final class StaminaCharms {
    private StaminaCharms() {
    }

    public static float bonus(Player player) {
        float best = 0.0F;
        for (ItemStack s : player.getInventory().items) {
            best = Math.max(best, of(s));
        }
        for (ItemStack s : player.getInventory().offhand) {
            best = Math.max(best, of(s));
        }
        if (best < 1.0F && ModList.get().isLoaded("curios")) {
            best = Math.max(best, CuriosHook.best(player));
        }
        return best;
    }

    static float of(ItemStack s) {
        return s.getItem() instanceof WipsItems.StaminaCharm c ? c.bonus() : 0.0F;
    }

    /** Separado para no cargar clases de Curios si no esta instalado. */
    static final class CuriosHook {
        static float best(Player player) {
            return top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player)
                    .map(inv -> {
                        float b = 0.0F;
                        for (var r : inv.findCurios(s -> s.getItem() instanceof WipsItems.StaminaCharm)) {
                            b = Math.max(b, of(r.stack()));
                        }
                        return b;
                    }).orElse(0.0F);
        }
    }
}
