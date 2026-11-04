package com.aurachad.client.emf;

import java.lang.reflect.Method;
import java.util.function.Function;

import com.aurachad.AuraChadMod;
import com.aurachad.client.ChadApi;
import net.minecraft.client.model.EntityModel;
import net.minecraft.world.entity.Entity;
import net.neoforged.fml.ModList;

/**
 * Entity Model Features (EMF) sustituye las partes del jugador por piezas sin cubos, y bendy-lib no tiene nada que doblar.
 * Mientras un jugador esta en modo chad le pedimos a EMF, con su API publica (EMFAnimationApi.registerVanillaModelCondition),
 * que use el modelo vanilla de ese jugador. Es una condicion, no un bloqueo: EMF la consulta en cada render, asi que se suelta
 * sola al terminar el modo, al desconectar o al morir/desaparecer la entidad, y vale para los jugadores que ven al que lo activa.
 * Todo por reflexion: sin EMF no hace nada y ante cualquier fallo queda el comportamiento anterior (sin crash).
 */
public final class ChadEmfBridge {
    private ChadEmfBridge() {}

    public static final String EMF_MODID = "entity_model_features";
    private static final boolean LOADED = ModList.get() != null && ModList.get().isLoaded(EMF_MODID);
    private static boolean registered;
    private static Class<?> api;

    public static boolean registered() { return registered; }

    /** Llamar una vez (setup de cliente). */
    public static void register() {
        if (!LOADED) return;
        try {
            api = Class.forName("traben.entity_model_features.EMFAnimationApi");
            Function<Object, Boolean> condition = e -> e instanceof Entity en && ChadApi.isChadActive(en);
            Method m = api.getMethod("registerVanillaModelCondition", Function.class);
            registered = Boolean.TRUE.equals(m.invoke(null, condition));
            AuraChadMod.LOGGER.info("EMF: modelo vanilla durante el modo chad {}", registered ? "registrado" : "rechazado");
        } catch (Throwable t) {
            registered = false;
            AuraChadMod.LOGGER.warn("EMF: no se pudo registrar el modelo vanilla para el modo chad; los codos no se veran con EMF", t);
        }
    }

    /** Autotest: "personalizado" = EMF esta sustituyendo el modelo, "vanilla" = EMF lo dejo vanilla. */
    public static String describe(EntityModel<?> model) {
        if (!registered) return "EMF no presente";
        try {
            boolean custom = (boolean) api.getMethod("isModelCustomizedByEMF", EntityModel.class).invoke(null, model);
            boolean anim = (boolean) api.getMethod("isModelAnimatedByEMF", EntityModel.class).invoke(null, model);
            return custom ? "modelo de EMF (personalizado" + (anim ? ", animado)" : ")") : "modelo vanilla";
        } catch (Throwable t) { return t.toString(); }
    }
}
