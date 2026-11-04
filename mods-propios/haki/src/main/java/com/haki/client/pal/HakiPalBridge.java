package com.haki.client.pal;

import com.haki.HakiMod;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.fml.ModList;

/**
 * Puente sin referencias a clases de Player Animation Library: HakiPal solo se carga si PAL esta instalada.
 * Si PAL esta disponible y la animacion se pudo reproducir, la pose la pone PAL y el Mixin de respaldo no toca el modelo.
 * Si algo falla (version incompatible, animacion sin cargar...) se marca como roto y el Mixin toma el relevo.
 */
public final class HakiPalBridge {
    private HakiPalBridge() {}

    public static final String PAL_MODID = "player_animation_library";
    private static final boolean LOADED = ModList.get() != null && ModList.get().isLoaded(PAL_MODID);
    private static boolean broken;

    public static boolean available() { return LOADED && !broken; }

    /** true si PAL anima a esta entidad (el Mixin de respaldo debe dejarla en paz): con PAL sana la pose, incluido su fundido de salida, es siempre de PAL. */
    public static boolean drives(Entity e) { return available() && e instanceof AbstractClientPlayer; }

    /** true si la capa haki:cast de PAL sigue activa para este jugador (reproduciendo o fundiendo). */
    public static boolean layerActive(Entity e) {
        if (!available() || !(e instanceof AbstractClientPlayer p)) return false;
        try { return HakiPal.active(p); } catch (Throwable t) { return false; }
    }

    public static void register() {
        if (!LOADED) return;
        try {
            HakiPal.register();
            HakiMod.LOGGER.info("Player Animation Library detectada: pose de lanzamiento como capa haki:cast (prioridad {})", HakiPal.PRIORITY);
        } catch (Throwable t) { fail("registrar la capa", t); }
    }

    public static void play(Entity e) {
        if (!available() || !(e instanceof AbstractClientPlayer p)) return;
        try {
            if (!HakiPal.play(p)) fail("reproducir la animacion", null);
        } catch (Throwable t) { fail("reproducir la animacion", t); }
    }

    public static void stop(Entity e) {
        if (!available() || !(e instanceof AbstractClientPlayer p)) return;
        try { HakiPal.stop(p); } catch (Throwable t) {
            fail("detener la animacion", t);
            try { HakiPal.forceStop(p); } catch (Throwable ignored) { }
        }
    }

    private static void fail(String what, Throwable t) {
        broken = true;
        HakiMod.LOGGER.error("Player Animation Library: no se pudo {}; se usa el Mixin de respaldo", what, t);
    }
}
