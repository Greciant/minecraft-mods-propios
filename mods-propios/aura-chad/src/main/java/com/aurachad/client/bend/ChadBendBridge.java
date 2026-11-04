package com.aurachad.client.bend;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.ModList;

/**
 * Puente sin referencias a bendy-lib: ChadBend solo se carga si bendy-lib esta instalada. Sin ella (o si algo falla) las
 * extremidades quedan rectas y no pasa nada.
 */
public final class ChadBendBridge {
    private ChadBendBridge() {}

    public static final String BENDY_MODID = "bendylib";
    private static final boolean LOADED = ModList.get() != null && ModList.get().isLoaded(BENDY_MODID);
    private static boolean broken;

    public static boolean available() { return LOADED && !broken; }

    public static void apply(PlayerModel<?> model, LivingEntity entity) {
        if (!available()) return;
        try { ChadBend.apply(model, entity); } catch (Throwable t) {
            broken = true;
            com.aurachad.AuraChadMod.LOGGER.error("bendy-lib: no se pudo doblar codos y rodillas; extremidades rectas", t);
        }
    }

    /** Direccion medida del antebrazo/pierna inferior (derecha, arriba, frente) + brazo superior + angulo del codo; null si no hay bend. Autotest. */
    public static float[] measure(PlayerModel<?> model, String bone) {
        if (!available()) return null;
        try { return ChadBend.measure(model, bone); } catch (Throwable t) { return null; }
    }

    public static String describe(PlayerModel<?> model, String bone) {
        if (!available()) return "bendy-lib no disponible";
        try { return ChadBend.describe(model, bone); } catch (Throwable t) { return t.toString(); }
    }
}
