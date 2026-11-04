package com.haki.client;

import java.lang.reflect.Method;

import net.minecraft.world.entity.Entity;
import net.neoforged.fml.ModList;

/** Convivencia con otros mods (sin dependencias de compilacion): hoy, ceder la pose a aurachad mientras esta en modo chad. */
public final class HakiCompat {
    private HakiCompat() {}

    private static boolean chadLoaded = ModList.get() != null && ModList.get().isLoaded("aurachad");
    private static Method chadActive;

    /** true mientras la entidad esta en modo chad de aurachad (su pose manda). */
    public static boolean chadActive(Entity e) {
        if (!chadLoaded) return false;
        try {
            if (chadActive == null) chadActive = Class.forName("com.aurachad.client.ChadApi").getMethod("isChadActive", Entity.class);
            return (boolean) chadActive.invoke(null, e);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            chadLoaded = false; // API distinta: no volver a intentarlo
            return false;
        }
    }
}
