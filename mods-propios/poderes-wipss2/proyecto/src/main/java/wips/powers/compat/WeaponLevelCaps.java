package wips.powers.compat;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;

/**
 * Topes de Lukas' Weapon Leveling para todas las armas y armaduras (vanilla y de mods):
 * - armas: dano base 25 como maximo (lo que dice el tooltip "X de dano de ataque", sin encantamientos ni mejoras);
 * - armaduras: casco 8, peto 15, grebas 12, botas 6 puntos de armadura.
 * Solo se recorta el bonus por nivel: un item que de fabrica ya pasa el tope no pierde nada, y no se tocan
 * durabilidad, efectos especiales ni otros atributos.
 */
public final class WeaponLevelCaps {
    public static final double MAX_WEAPON_DAMAGE = 25.0;
    /** El jugador pega 1 de base: el modificador del arma con 24 da el "25 de dano" del tooltip. */
    private static final double PLAYER_BASE_DAMAGE = 1.0;
    public static final ThreadLocal<ItemStack> CURRENT = new ThreadLocal<>();

    private WeaponLevelCaps() {
    }

    public static double armorCap(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> 8.0;
            case CHEST -> 15.0;
            case LEGS -> 12.0;
            case FEET -> 6.0;
            default -> -1.0;
        };
    }

    /** @param bonus lo que suma Weapon Leveling por nivel sobre originalAmount. */
    public static double cap(Holder<Attribute> attribute, double originalAmount, double bonus) {
        if (bonus <= 0.0) {
            return bonus;
        }
        double top;
        if (attribute.equals(Attributes.ATTACK_DAMAGE)) {
            top = MAX_WEAPON_DAMAGE - PLAYER_BASE_DAMAGE;
        } else if (attribute.equals(Attributes.ARMOR)) {
            ItemStack stack = CURRENT.get();
            Equipable eq = stack == null ? null : Equipable.get(stack);
            top = eq == null ? -1.0 : armorCap(eq.getEquipmentSlot());
            if (top < 0.0) {
                return bonus;
            }
        } else {
            return bonus;
        }
        return Math.max(0.0, Math.min(bonus, top - originalAmount));
    }
}
