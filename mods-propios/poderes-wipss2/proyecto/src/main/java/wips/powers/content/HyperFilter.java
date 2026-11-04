package wips.powers.content;

import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * Hyper 'N Hiei's Shenanigans recortado. Quedan los hechizos Kamehameha, Cruel Sun, Infinity, Hollow Mask y
 * World Cutting Slash; el jefe Mahoraga con su Rueda del General Divino y sus bolsas de botin (Ritual Spoils);
 * el pergamino de las 10 Sombras (ahora 1 % de drop de cualquier mob, sin receta) y el Ritual Altar, que aparece
 * en un santuario pequeno por todo el mundo (wipspowers:ritual_shrine). Lo demas (armaduras, armas, Shibuya y
 * su estructura, el santuario de Excalibur) queda fuera.
 */
public final class HyperFilter {
    public static final String MOD = "hyper_n_hieis_shenanigans";
    private static final Set<String> REMOVED_SPELLS = Set.of(
            "disintegration", "hell_flare", "hollow_purple", "field_of_flowers", "geppo", "crescent_shift", "crimson_requiem");
    private static final Set<String> KEEP_ITEMS = Set.of(
            "ten_shadows_scroll", "mahoraga_wheel", "mahoraga_pouch_grade_one", "mahoraga_pouch_special_grade",
            "mahoraga_pouch_divine_general", "shibuya_altar");

    private HyperFilter() {
    }

    public static boolean removedSpell(ResourceLocation id) {
        return id != null && id.getNamespace().equals(MOD) && REMOVED_SPELLS.contains(id.getPath());
    }

    public static boolean hiddenItem(String path) {
        return !KEEP_ITEMS.contains(path);
    }
}
