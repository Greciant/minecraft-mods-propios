package com.haki;

/** Todos los parametros visuales del Haki en un solo lugar (solo afectan al cliente). */
public final class HakiVisuals {
    private HakiVisuals() {}

    // ---- Estilo visual (cambiar DEFAULT_STYLE; para probar sin recompilar: variable de entorno HAKI_STYLE o -Dhaki.style) ----
    //  1 = sprites: animaciones pre-renderizadas con Blender (tools/blender_fx.py) sobre quads
    //  2 = procedural: geometria generada en codigo (rayos fractales, grietas, espirales, humo, brasas...)
    //  3 = hibrido "referencia": sprites + sigilo runico, anillos orbitales, rocas flotantes, columnas de luz,
    //      rayos del cielo y estallido inicial (inspirado en la apertura de la puerta de LOTMCraft)
    public static final int STYLE_SPRITES = 1, STYLE_PROCEDURAL = 2, STYLE_HYBRID = 3;
    public static final int DEFAULT_STYLE = STYLE_PROCEDURAL;
    public static final int STYLE = pickStyle();

    private static int pickStyle() {
        String s = System.getenv("HAKI_STYLE");
        if (s == null) s = System.getProperty("haki.style");
        try {
            int v = Integer.parseInt(s.trim());
            if (v >= 1 && v <= 3) return v;
        } catch (RuntimeException ignored) { }
        return DEFAULT_STYLE;
    }

    // ---- Red ----
    public static final int AFFECTED_SYNC_TICKS = 10;  // cada cuanto el servidor manda la lista de afectados
    public static final int AFFECTED_MAX_IDS    = 256; // tope de entidades por paquete

    // ---- Rayos radiales (cada "casilla" repite: sale disparado, se desvanece y reaparece en otra direccion) ----
    public static final int    BOLT_SLOTS          = 18;    // casillas de rayo por lanzador (visibles a la vez: ~55 %)
    public static final int    BOLT_FP_SLOTS       = 8;     // en primera persona (los propios) hay menos
    public static final int    BOLT_PERIOD_MIN     = 10;    // ticks de un ciclo completo (aparece + pausa); cada casilla usa 10..14
    public static final int    BOLT_LIFE_MIN       = 5;     // ticks que vive cada rayo
    public static final int    BOLT_LIFE_MAX       = 9;
    public static final float  BOLT_SHOOT_FRAC     = 0.22f; // fraccion de la vida que tarda en alcanzar el largo completo
    public static final float  BOLT_HOLD_FRAC      = 0.35f; // hasta aqui opacidad plena; despues se desvanece
    public static final float  BOLT_MIN_LENGTH     = 3.0f;  // bloques
    public static final float  BOLT_MAX_LENGTH     = 8.0f;
    public static final float  BOLT_START_OFFSET   = 0.6f;  // empiezan algo separados del pecho
    public static final int    BOLT_LEVELS         = 3;     // subdivisiones (desplazamiento del punto medio): 2^3 = 8 tramos
    public static final float  BOLT_JITTER         = 0.10f; // desvio lateral maximo del primer nivel, relativo al largo
    public static final float  BOLT_ROUGHNESS      = 0.58f; // cuanto se reduce el desvio en cada nivel
    public static final float  BOLT_WIDTH          = 0.16f; // ancho del nucleo negro en la base
    public static final float  BOLT_TIP_WIDTH      = 0.016f;// ancho en la punta
    public static final float  BOLT_GLOW_SCALE     = 3.6f;  // el halo rojo es tantas veces mas ancho que el nucleo
    public static final float  BOLT_GLOW_MIN       = 0.05f; // ancho minimo del halo (para que la punta tambien brille)
    public static final float  BOLT_GLOW_ALPHA     = 0.75f;
    public static final float  BOLT_FLICKER        = 0.4f;  // parpadeo de opacidad (0 = fijo); cambia 10 veces por segundo
    public static final float  BOLT_SHAPE_FLICKER  = 0.22f; // cuanto tiembla la forma entre parpadeos (relativo al desvio)
    public static final int    BOLT_FORKS_MAX      = 2;     // ramas secundarias por rayo
    public static final float  BOLT_FORK_CHANCE    = 0.75f;
    public static final float  BOLT_FORK_LEN_MIN   = 0.25f; // relativo al largo del rayo principal
    public static final float  BOLT_FORK_LEN_MAX   = 0.45f;
    public static final float  BOLT_FORK_SPREAD    = 0.9f;  // apertura respecto al rayo principal (0 = paralelas)
    public static final float  BOLT_FORK_WIDTH     = 0.55f; // ancho de la rama respecto al rayo en el punto de union
    public static final int    BOLT_FORK_LEVELS    = 2;
    public static final float  SPARK_CHANCE        = 0.7f;  // probabilidad de chispa blanca en cada rayo por parpadeo
    public static final float  SPARK_SIZE_MIN      = 0.16f;
    public static final float  SPARK_SIZE_MAX      = 0.34f;
    public static final double BOLT_RENDER_DISTANCE = 96.0;
    // Vista propia: primera persona omite los que suben o apuntan a la camara; tercera persona los que apuntan a la camara
    public static final float  BOLT_FP_MAX_UP       = 0.25f; // componente vertical maxima de la direccion
    public static final float  BOLT_FP_MAX_FRONT    = 0.3f;  // coseno maximo respecto a hacia donde mira el jugador
    public static final float  BOLT_TP_MAX_FRONT    = 0.8f;  // coseno maximo respecto a la camara
    public static final float  BOLT_FP_START_OFFSET = 1.2f;
    public static final float  BOLT_FP_WIDTH_SCALE  = 0.6f;

    // ---- Estrella de impacto ----
    public static final float STAR_SIZE          = 7.0f;   // radio en bloques
    public static final int   STAR_GROW_TICKS    = 10;     // ~0,5 s
    public static final float STAR_SPIN_DEG      = 1.5f;   // grados por tick
    public static final double STAR_RAYCAST_DOWN = 4.0;    // busca el suelo hasta 4 bloques abajo

    // ---- Aparicion / desaparicion del efecto del lanzador ----
    public static final int CASTER_FADE_IN_TICKS  = 4;
    public static final int CASTER_FADE_OUT_TICKS = 12;

    // ---- Pantalla ----
    public static final int   SCREEN_FADE_IN_TICKS  = 14;   // ~0,7 s
    public static final int   SCREEN_FADE_OUT_TICKS = 18;   // ~0,9 s
    public static final float TINT_ALPHA            = 0.28f;
    public static final int   TINT_RGB              = 0xB00000;
    public static final float VIGNETTE_ALPHA        = 0.85f;
    public static final int   FLASH_TICKS           = 5;
    public static final float FLASH_ALPHA           = 0.75f;

    // ---- Entrada cinematografica (pantalla casi negra + vetas rojas + zoom) ----
    // Si marea: INTRO_ENABLED = false o bajar INTRO_STRENGTH (0..1) / INTRO_FOV_ZOOM
    public static final boolean INTRO_ENABLED      = true;
    public static final float   INTRO_STRENGTH     = 1.0f;  // escala oscuridad, vetas y zoom
    public static final int     INTRO_TICKS        = 8;     // ~0,4 s
    public static final float   INTRO_DARK_ALPHA   = 0.88f;
    public static final float   INTRO_STREAK_ALPHA = 0.9f;
    public static final float   INTRO_STREAK_SCALE = 1.1f;  // tamano de las vetas respecto a la pantalla
    public static final float   INTRO_FOV_ZOOM     = 0.12f; // 12 % de acercamiento maximo

    // ---- Temblor de camara ----
    public static final float SHAKE_DEGREES = 0.9f;
    public static final float SHAKE_SPEED   = 1.6f;

    // ---- Ojos rojos del lanzador (en pixeles de la skin: la cabeza mide 8x8x8) ----
    public static final float EYE_GLOW_SIZE    = 0.26f;  // lado del brillo (bloques)
    public static final float EYE_ROW_FROM_TOP = 4.5f;   // altura de los ojos: fila 4 de 8 contando desde arriba (centro del pixel)
    public static final float EYE_SPACING_PX   = 2.0f;   // separacion de cada ojo al centro de la cara (pareja de pixeles de ojo: columnas 1-2 y 5-6)
    public static final float EYE_FORWARD_PX   = 0.9f;   // por delante de la cara (la capa exterior llega a 0,5)
    public static final int   EYE_RGB          = 0xFF2010;
    public static final float EYE_FLICKER      = 0.15f;  // parpadeo de intensidad (0 = fijo)

    // ---- Onda de choque (cupula) ----
    public static final float DOME_RADIUS      = (float) HakiServer.RADIUS; // radio real del Haki
    public static final int   DOME_GROW_TICKS  = 20;    // ~1 s de 0 al radio completo
    public static final float DOME_ALPHA       = 0.3f;
    public static final int   DOME_SEGMENTS    = 16;    // alrededor (malla barata)
    public static final int   DOME_RINGS       = 6;     // de la base a la cima
    public static final int   PULSE_INTERVAL   = HakiServer.DAMAGE_INTERVAL; // un pulso por golpe de dano
    public static final int   PULSE_TICKS      = 10;    // duracion de cada pulso
    public static final float PULSE_ALPHA      = 0.18f;
    public static final float RING_WIDTH       = 0.7f;  // anillo en el suelo
    public static final int   RING_SEGMENTS    = 32;

    // ---- Esquirlas en entidades afectadas ----
    public static final int   SHARD_COUNT         = 5;
    public static final int   SHARD_SEGMENTS      = 5;
    public static final float SHARD_WIDTH         = 0.16f;
    public static final float SHARD_JITTER        = 0.18f;
    public static final int   SHARD_LIFE_TICKS    = 8;     // cada esquirla crece desde el suelo, se desvanece y reaparece (desfasadas)
    public static final int   SHARD_FADE_TICKS    = 20;    // se desvanecen el ultimo segundo del stun
    public static final double SHARD_RENDER_DISTANCE = 48.0;

    // ---- Estallido inicial (todos los rayos salen a la vez los primeros ticks) ----
    public static final int   BURST_TICKS       = 8;
    public static final float BURST_LEN_MUL     = 1.6f;

    // ---- Rayos en sprites (estilos 1 y 3): una lamina del flipbook por rayo, de frente a la camara ----
    public static final int   SPRITE_BOLT_SLOTS    = 18;
    public static final int   SPRITE_BOLT_FP_SLOTS = 7;
    public static final float SPRITE_BOLT_WIDTH    = 0.27f; // ancho de la lamina respecto a su largo (la imagen es 4:1)
    public static final float SPRITE_FRAMES_PER_TICK = 1.5f;
    public static final float SPRITE_RING_HALF     = (float) HakiServer.RADIUS; // la hoja de la onda llega al borde
    public static final int   SPRITE_RING_TICKS    = 24;
    public static final float SPRITE_CRACK_HALF    = 9.0f;
    public static final int   SPRITE_CRACK_TICKS   = 16;
    public static final float SPRITE_BURST_SIZE    = 11.0f; // lado de la explosion radial en el pecho
    public static final int   SPRITE_BURST_TICKS   = 14;

    // ---- Sigilo runico y emblema (estilo 3) ----
    public static final float SIGIL_GROUND_HALF    = 3.2f;
    public static final float SIGIL_GROUND_ALPHA   = 0.85f;
    public static final float SIGIL_SPIN_DEG       = 0.6f;   // grados por tick
    public static final float SIGIL_HEAD_SIZE      = 1.3f;   // emblema sobre la cabeza
    public static final float SIGIL_HEAD_ABOVE     = 1.0f;

    // ---- Anillos orbitales ornamentales (estilos 2 y 3) ----
    public static final int   BAND_SEGMENTS        = 24;     // cada segmento muestra 2 periodos de la textura
    public static final int   BAND_GROW_TICKS      = 30;
    public static final float BAND_ALPHA           = 0.9f;
    // radio, altura del centro sobre el suelo, alto de la banda, inclinacion (grados), velocidad (rad/tick)
    public static final float[][] BANDS = {
        {7.0f, 2.2f, 0.50f, 0f, 0.016f}, {10.5f, 4.6f, 0.40f, 0f, -0.010f},
        {9.0f, 4.2f, 0.45f, 35f, 0.013f}, {6.0f, 3.0f, 0.40f, -50f, -0.019f},
    };

    // ---- Columnas de luz (estilos 2 y 3) ----
    public static final int   BEAM_COUNT           = 8;
    public static final float BEAM_HEIGHT          = 26.0f;
    public static final float BEAM_WIDTH           = 1.4f;
    public static final float BEAM_MIN_R           = 7.0f;
    public static final float BEAM_MAX_R           = 13.0f;
    public static final float BEAM_ALPHA           = 0.34f;

    // ---- Rocas flotantes orbitando (estilos 2 y 3) ----
    public static final int   DEBRIS_ORBIT_COUNT   = 20;
    public static final float DEBRIS_MIN_R         = 3.0f;
    public static final float DEBRIS_MAX_R         = 10.0f;
    public static final float DEBRIS_MAX_H         = 6.5f;
    public static final float DEBRIS_MIN_SIZE      = 0.25f;
    public static final float DEBRIS_MAX_SIZE      = 0.75f;

    // ---- Espirales de energia alrededor del cuerpo ----
    public static final int   SPIRAL_COUNT         = 6;
    public static final float SPIRAL_HEIGHT        = 2.7f;
    public static final float SPIRAL_RADIUS        = 0.62f;
    public static final float SPIRAL_WIDTH         = 0.10f;

    // ---- Grietas procedurales en el suelo (estilo 2) ----
    public static final int   CRACK_COUNT          = 10;
    public static final float CRACK_MIN_LENGTH     = 2.5f;
    public static final float CRACK_MAX_LENGTH     = 7.5f;
    public static final float CRACK_WIDTH          = 0.22f;
    public static final int   CRACK_GROW_TICKS     = 14;

    // ---- Rayos gigantes del cielo (estilos 2 y 3) ----
    public static final int   SKY_SLOTS            = 2;
    public static final int   SKY_PERIOD           = 46;
    public static final int   SKY_LIFE             = 7;
    public static final float SKY_HEIGHT           = 28.0f;
    public static final float SKY_MIN_R            = 4.0f;
    public static final float SKY_MAX_R            = 12.0f;
    public static final float SKY_WIDTH            = 0.46f;

    // ---- Humo y brasas (estilos 2 y 3) ----
    public static final int   SMOKE_COUNT          = 7;
    public static final float SMOKE_ALPHA          = 0.32f;
    public static final int   EMBER_COUNT          = 30;

    // ---- Pulido del estilo 2 ----
    public static final float TIP_SPARK_SIZE       = 0.6f;  // destello blanco en la cabeza de cada rayo mientras sale disparado
    public static final int   ARC_SLOTS            = 5;     // arcos que corren por el suelo
    public static final int   ARC_PERIOD_MIN       = 12;
    public static final int   ARC_LIFE_MIN         = 5;
    public static final int   ARC_LIFE_MAX         = 9;
    public static final float ARC_MIN_LENGTH       = 4.0f;
    public static final float ARC_MAX_LENGTH       = 11.0f;
    public static final float ARC_WIDTH            = 0.16f;
    public static final float PULSE_RING_ALPHA     = 0.7f;  // anillo del suelo que acompana a cada pulso de la cupula

    public static final int   SKY_FLASH_TICKS      = 5;
    public static final float SKY_FLASH_ALPHA      = 0.32f;
    public static final float SKY_FLASH_RANGE      = 22.0f; // mas lejos que esto no destella la pantalla
    // Nivel de detalle por distancia: lejos se dibujan menos rayos y no hay capas de ambiente
    public static final double LOD_FAR_DISTANCE    = 40.0;
    public static final float  RELEASE_DROP        = 1.0f;  // las rocas caen esta fraccion de su altura al soltar el Haki
    public static final int   CRACK_LEVELS         = 3;     // tramos por grieta: 2^n
    public static final int   ARC_LEVELS           = 4;

    // ---- Modelos 3D (rocas, pinchos de obsidiana, corona) hechos con Blockbench ----
    public static final boolean MESH_ENABLED       = true;  // false: vuelven los billboards planos
    public static final float   ROCK_SCALE         = 1.5f;  // la roca mide ~0,5 bloques a escala 1; se multiplica por su tamano
    public static final int     GSHARD_COUNT       = 12;    // pinchos de obsidiana que brotan del suelo
    public static final float   GSHARD_MIN_R       = 2.2f;
    public static final float   GSHARD_MAX_R       = 10.0f;
    public static final float   GSHARD_MIN_H       = 0.9f;  // altura final en bloques
    public static final float   GSHARD_MAX_H       = 2.6f;
    public static final float   GSHARD_LEAN        = 0.28f; // inclinacion hacia fuera (rad)
    public static final int     GSHARD_EMERGE_TICKS = 7;
    public static final float   CROWN_SCALE        = 0.9f;
    public static final float   CROWN_ABOVE        = 0.85f;
    public static final float   CROWN_SPIN         = 0.05f; // rad por tick

    // ---- Pose de lanzamiento del jugador (mientras mantiene el Haki; se aplica con un Mixin al final de setupAnim) ----
    public static final boolean POSE_ENABLED       = true;
    public static final float   POSE_ARM_SPREAD    = 1.05f; // brazos abiertos (rad desde el cuerpo)
    public static final float   POSE_ARM_FORWARD   = -0.2f; // los brazos un poco hacia delante/arriba
    public static final float   POSE_HEAD_BACK     = -0.28f;// cabeza hacia atras (mirando algo mas arriba)
    public static final float   POSE_BODY_LEAN     = -0.10f;// pecho hacia delante = cuerpo ligeramente arqueado
    public static final float   POSE_LEG_SPREAD    = 0.12f; // piernas algo separadas
    public static final float   POSE_RUMBLE        = 0.035f;// temblor de los brazos (rad)
    public static final float   POSE_RUMBLE_SPEED  = 1.7f;
    public static final float   MESH_TINT          = 1.25f; // brillo de los modelos 3D (1 = el de la textura)
}
