package com.aurachad.client.pal;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.fml.ModList;

/**
 * Puente sin referencias a clases de Player Animation Library: ChadPal solo se carga si PAL esta instalada.
 * Mientras PAL este disponible y la animacion cargada, las poses las pone PAL y el Mixin de respaldo no toca el modelo.
 */
public final class ChadPalBridge {
    private ChadPalBridge() {}

    public static final String PAL_MODID = "player_animation_library";
    private static final boolean LOADED = ModList.get() != null && ModList.get().isLoaded(PAL_MODID);
    private static boolean broken;

    public static boolean available() { return LOADED && !broken; }

    /** true si PAL anima a esta entidad (el Mixin de respaldo debe dejarla en paz). */
    public static boolean drives(Entity e) { return available(); }

    public static void register() {
        if (!LOADED) return;
        try { ChadPal.register(); } catch (Throwable t) { fail("registrar la capa", t); }
    }

    public static void play(Entity e) {
        if (!available() || !(e instanceof AbstractClientPlayer p)) return;
        try { if (!ChadPal.play(p)) fail("reproducir la animacion", null); } catch (Throwable t) { fail("reproducir la animacion", t); }
    }

    public static void stop(Entity e) {
        if (!available() || !(e instanceof AbstractClientPlayer p)) return;
        try { ChadPal.stop(p); } catch (Throwable t) { fail("detener la animacion", t); }
    }

    private static void fail(String what, Throwable t) {
        broken = true;
        com.aurachad.AuraChadMod.LOGGER.error("Player Animation Library: no se pudo {}; se usa el Mixin de respaldo", what, t);
    }
}
