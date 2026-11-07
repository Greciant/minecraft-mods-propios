package wips.powers.level;

import com.awakenedmod.awakened.core.power.Ability;
import com.awakenedmod.awakened.core.power.Power;
import com.awakenedmod.awakened.core.power.PowerRegistry;
import com.awakenedmod.awakened.core.progression.AbilityBalance;
import com.awakenedmod.awakened.core.progression.AbilityScaling;
import com.awakenedmod.awakened.core.progression.BalanceConfig;
import wips.powers.WipsPowers;

/** Audit: dano declarado de cada habilidad de cada poder del pack, a nivel 1, 10, 25, 35 y 50 con la formula unica. */
public final class BalanceDump {
    private BalanceDump() {
    }

    public static void log() {
        StringBuilder sb = new StringBuilder("\n[wipspowers] AUDIT balance (dano base declarado x balance x nivel):\n");
        sb.append(String.format("  multiplicador de nivel: L1 %.2f | L10 %.2f | L25 %.2f | L35 %.2f | L50 %.2f%n",
                WipsScaling.levelMultiplier(1), WipsScaling.levelMultiplier(10), WipsScaling.levelMultiplier(25),
                WipsScaling.levelMultiplier(35), WipsScaling.levelMultiplier(50)));
        for (Power p : PowerRegistry.all()) {
            for (Ability a : p.abilities()) {
                double base = -1;
                for (AbilityScaling.Entry e : a.scalingInfo().entries()) {
                    if ("damage".equals(e.labelKey())) {
                        base = e.base();
                    }
                }
                double bal = AbilityBalance.damageMultiplier(a.balanceKey()) * BalanceConfig.damageMultiplier();
                if (base < 0) {
                    sb.append(String.format("  %-16s %-34s (sin dano declarado) balance x%.2f%n", p.id(), a.id(), bal));
                } else {
                    sb.append(String.format("  %-16s %-34s base %6.1f -> L1 %6.1f | L25 %6.1f | L50 %6.1f%n", p.id(), a.id(), base,
                            base * bal * WipsScaling.levelMultiplier(1), base * bal * WipsScaling.levelMultiplier(25),
                            base * bal * WipsScaling.levelMultiplier(50)));
                }
            }
        }
        WipsPowers.LOGGER.info(sb.toString());
    }
}
