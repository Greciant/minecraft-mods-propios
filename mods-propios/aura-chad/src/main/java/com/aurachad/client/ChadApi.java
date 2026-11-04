package com.aurachad.client;

import net.minecraft.world.entity.Entity;

/** API cliente minima para otros mods (p. ej. Haki): consultar si una entidad esta en modo chad. */
public final class ChadApi {
    private ChadApi() {}

    /** true mientras la entidad (jugador propio o remoto) tiene el modo chad activo. */
    public static boolean isChadActive(Entity entity) {
        return entity != null && ChadClient.ageSeconds(entity.getId()) >= 0f;
    }
}
