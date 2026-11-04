package com.aurachad;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ChadConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue THIRD_PERSON;
    public static final ModConfigSpec.DoubleValue CAMERA_DISTANCE;
    public static final ModConfigSpec.BooleanValue LOCK_MOVEMENT;
    public static final ModConfigSpec.BooleanValue MUSIC;
    public static final ModConfigSpec.BooleanValue SILHOUETTE;

    static {
        var b = new ModConfigSpec.Builder();
        THIRD_PERSON = b.comment("Pasar a camara frontal en 3a persona durante el modo chad").define("thirdPersonCamera", true);
        CAMERA_DISTANCE = b.comment("Distancia de la camara (bloques) durante el modo chad").defineInRange("cameraDistance", 3.2, 1.5, 8.0);
        LOCK_MOVEMENT = b.comment("Bloquear el movimiento propio mientras dura").define("lockMovement", true);
        MUSIC = b.comment("Reproducir la musica").define("music", true);
        SILHOUETTE = b.comment("Silueta oscura con contorno neon (como el video); false = cuerpo azul translucido").define("silhouette", true);
        SPEC = b.build();
    }

    private ChadConfig() {}
}
