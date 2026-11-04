package wips.powers.crit;

import net.neoforged.fml.ModList;

/** Cliente: el critico es lo unico que puede disparar un impact frame. */
public final class ClientCrit {
    /** true solo mientras se procesa un critico; el mixin de Impact Frames bloquea todo lo demas. */
    public static boolean allow;

    private ClientCrit() {
    }

    public static void receive(Criticals.Payload p) {
        if (!ModList.get().isLoaded("impactframe")) {
            return;
        }
        Bridge.fire(p.targetId(), p.damage());
    }

    /** Clase aparte para no cargar clases de Impact Frames si el mod no esta. */
    private static final class Bridge {
        static void fire(int target, float damage) {
            allow = true;
            try {
                // damage > 15 hace que Impact Frames use el impacto pesado con bandas de cine
                com.impactframe.client.ImpactFrameClient.onHighDamageAttack(target, Math.max(16.0F, damage));
            } finally {
                allow = false;
            }
        }
    }
}
