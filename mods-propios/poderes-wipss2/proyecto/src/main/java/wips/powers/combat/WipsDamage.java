package wips.powers.combat;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;
import wips.powers.WipsPowers;

/**
 * Tipos de dano propios. ability_fire reemplaza al fuego de Pyrokinetic (ON_FIRE) y de Seiryuu
 * (devilfruits:uo_uo_seiryu/flame): esos dos estan en #minecraft:is_fire y todo mob con fireImmune()
 * (casi todos los del Nether y muchos de mods) los ignoraba. Este no esta en #is_fire, asi que pega
 * a todos; los mobs que no son inmunes se siguen prendiendo porque las habilidades llaman a igniteForSeconds.
 */
public final class WipsDamage {
    public static final ResourceKey<DamageType> ABILITY_FIRE = key("ability_fire");
    public static final ResourceKey<DamageType> ABILITY = key("ability");

    private WipsDamage() {
    }

    private static ResourceKey<DamageType> key(String path) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(WipsPowers.MOD_ID, path));
    }
}
