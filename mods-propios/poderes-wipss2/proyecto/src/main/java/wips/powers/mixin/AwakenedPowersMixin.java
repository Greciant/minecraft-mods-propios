package wips.powers.mixin;

import com.awakenedmod.awakened.content.AwakenedPowers;
import com.awakenedmod.awakened.core.power.Ability;
import com.awakenedmod.awakened.core.power.Power;
import com.awakenedmod.awakened.core.power.PowerRegistry;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wips.powers.SeiryuuPower;
import wips.powers.graviton.ChibakuTensei;

@Mixin(value = AwakenedPowers.class, remap = false)
public abstract class AwakenedPowersMixin {
    @Inject(method = "registerAll", at = @At("TAIL"))
    private static void wips$afterRegister(CallbackInfo ci) {
        PowerRegistry.register(new SeiryuuPower());
        PowerRegistry.register(new wips.powers.ice.IcePower());
        PowerRegistry.register(new wips.powers.slime.SlimePower());
        // Gamma: Island Shaker ocupa el slot 5 en lugar de Ragdoll Slam (Thunderclap se queda en el 2)
        wips$replace("gamma", "gamma_slam", new SeiryuuPower.IslandShaker());
        // Graviton ranura 5: Shinra Tensei reemplaza a Event Horizon (antes Chibaku Tensei)
        wips$replace("graviton", "event_horizon", new wips.powers.graviton.ShinraTensei());
        // Gamma 2: Thunderclap se puede mantener para cargar (hasta 5 s)
        wips$replace("gamma", "gamma_thunderclap", new wips.powers.gamma.ThunderclapHeld());
        // Ruin 4 y 5 rehechas con terreno propio (las de Awakened no rompian nada en el pack)
        wips$replace("ruin", "ruin_breach", new wips.powers.ruin.RuinBreachWips());
        wips$replace("ruin", "ruin_collapse", new wips.powers.ruin.RuinCollapseWips());
    }

    private static void wips$replace(String powerId, String oldAbility, Ability replacement) {
        Power power = PowerRegistry.get(powerId);
        if (power == null) {
            return;
        }
        try {
            Field f = Power.class.getDeclaredField("abilities");
            f.setAccessible(true);
            List<Ability> list = new ArrayList<>(power.abilities());
            for (int i = 0; i < list.size(); i++) {
                if (oldAbility.equals(list.get(i).id())) {
                    list.set(i, replacement);
                    f.set(power, List.copyOf(list));
                    return;
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("No se pudo reemplazar " + oldAbility + " en " + powerId, e);
        }
    }
}
