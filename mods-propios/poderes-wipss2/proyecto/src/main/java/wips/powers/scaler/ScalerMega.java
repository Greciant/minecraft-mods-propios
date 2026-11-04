package wips.powers.scaler;

import com.awakenedmod.awakened.content.scaler.SizeStage;
import com.awakenedmod.awakened.core.data.PowerData;

/**
 * Fases extra del Scaler por encima de Colossus (x5). No se agregan al enum de Awakened: estando en
 * Colossus, cada "crecer" sube un nivel "mega" que multiplica la escala. Encoger baja primero los
 * niveles mega y despues sigue con las fases normales.
 */
public final class ScalerMega {
    public static final String VAR = "wips_mega";
    /** Multiplicador sobre Colossus (x5): 8, 12, 18 y 25 bloques de escala. */
    static final float[] MULT = {1.0F, 1.6F, 2.4F, 3.6F, 5.0F};
    /** Nombres que se muestran al cambiar de fase. */
    static final String[] NAMES = {"Colossus", "Behemoth", "Leviathan", "Titanomachy", "World Breaker"};
    /** Velocidad por nivel mega (sobre la normal del personaje). */
    static final float[] SPEED = {1.75F, 2.1F, 2.5F, 3.0F, 3.5F};

    /** Lo fija Scaling.update (mixin) para que los calculos de ese tick lo lean. */
    public static int current;

    private ScalerMega() {
    }

    public static int level(PowerData data) {
        return Math.max(0, Math.min(MULT.length - 1, Math.round(data.var(VAR, 0.0F))));
    }

    public static int max() {
        return MULT.length - 1;
    }

    public static float scale(SizeStage stage, int level) {
        return stage == SizeStage.COLOSSUS ? stage.scale() * MULT[level] : stage.scale();
    }

    public static float speed(int level) {
        return SPEED[level];
    }

    public static String name(int level) {
        return NAMES[level];
    }
}
