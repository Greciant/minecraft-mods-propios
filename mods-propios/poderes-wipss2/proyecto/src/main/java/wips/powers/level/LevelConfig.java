package wips.powers.level;

import net.neoforged.neoforge.common.ModConfigSpec;

/** config/wipspowers-nivel.toml: curva de XP, escalado de dano y valores de cada fuente de XP. */
public final class LevelConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue MAX_LEVEL;
    public static final ModConfigSpec.DoubleValue XP_BASE;
    public static final ModConfigSpec.DoubleValue XP_FACTOR;
    public static final ModConfigSpec.DoubleValue XP_EXPONENT;
    public static final ModConfigSpec.DoubleValue DMG_MIN;
    public static final ModConfigSpec.DoubleValue DMG_MAX;
    public static final ModConfigSpec.DoubleValue DMG_CURVE;
    public static final ModConfigSpec.BooleanValue DMG_ABILITY_POWER;
    public static final ModConfigSpec.DoubleValue XP_ORE;
    public static final ModConfigSpec.DoubleValue XP_LOG;
    public static final ModConfigSpec.DoubleValue XP_PLANT;
    public static final ModConfigSpec.DoubleValue XP_HARVEST;
    public static final ModConfigSpec.DoubleValue XP_KILL_PER_HP;
    public static final ModConfigSpec.DoubleValue XP_KILL_MAX;
    public static final ModConfigSpec.DoubleValue XP_CHUNK;
    public static final ModConfigSpec.DoubleValue XP_BIOME;
    public static final ModConfigSpec.DoubleValue XP_LOOT;
    public static final ModConfigSpec.DoubleValue XP_SURVIVE_MINUTE;
    public static final ModConfigSpec.DoubleValue XP_SURVIVE_DAY;
    public static final ModConfigSpec.IntValue WINDOW_SECONDS;
    public static final ModConfigSpec.IntValue FREE_EVENTS;
    public static final ModConfigSpec.DoubleValue CAP_PER_MINUTE;
    public static final ModConfigSpec.DoubleValue VARIETY_BONUS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("curva");
        MAX_LEVEL = b.comment("Nivel maximo del jugador").defineInRange("nivelMaximo", 50, 2, 200);
        XP_BASE = b.comment("XP para pasar del nivel L al L+1 = base + factor * L^exponente")
                .defineInRange("xpBase", 40.0, 1.0, 1.0E6);
        XP_FACTOR = b.defineInRange("xpFactor", 5.0, 0.0, 1.0E6);
        XP_EXPONENT = b.defineInRange("xpExponente", 2.1, 0.5, 4.0);
        b.pop().push("dano");
        DMG_MIN = b.comment("Multiplicador de dano de TODOS los poderes del jugador: min + (max-min) * ((L-1)/(max-1))^curva")
                .defineInRange("multiplicadorNivel1", 0.30, 0.01, 10.0);
        DMG_MAX = b.defineInRange("multiplicadorNivelMaximo", 1.50, 0.01, 20.0);
        DMG_CURVE = b.comment("1 = lineal; mas de 1 = sube lento al principio y rapido al final")
                .defineInRange("curva", 1.35, 0.3, 4.0);
        DMG_ABILITY_POWER = b.comment("Sumar el 'ability power' de Awakened (puntos de personaje) como (1 + AP)")
                .define("incluirAbilityPower", true);
        b.pop().push("fuentes");
        XP_ORE = b.comment("Mena rota (por nivel de rareza: carbon x1, hierro/cobre x1.5, oro/redstone/lapis x2, diamante/esmeralda x4, ancient debris x5)")
                .defineInRange("mena", 2.0, 0.0, 1000.0);
        XP_LOG = b.defineInRange("tronco", 0.5, 0.0, 1000.0);
        XP_PLANT = b.defineInRange("plantar", 0.3, 0.0, 1000.0);
        XP_HARVEST = b.defineInRange("cosecharMaduro", 1.0, 0.0, 1000.0);
        XP_KILL_PER_HP = b.comment("Matar: XP por punto de vida maxima del mob (pacificos x0.4)").defineInRange("matarPorVida", 0.25, 0.0, 100.0);
        XP_KILL_MAX = b.defineInRange("matarMaximo", 60.0, 0.0, 10000.0);
        XP_CHUNK = b.comment("Explorar: chunk que nadie habia visitado").defineInRange("chunkNuevo", 1.5, 0.0, 1000.0);
        XP_BIOME = b.comment("Explorar: bioma que el jugador nunca habia pisado").defineInRange("biomaNuevo", 40.0, 0.0, 10000.0);
        XP_LOOT = b.comment("Lootear: cofre/contenedor con loot table abierto por primera vez").defineInRange("cofreConLoot", 20.0, 0.0, 10000.0);
        XP_SURVIVE_MINUTE = b.comment("Supervivencia: por minuto vivo y activo").defineInRange("minutoVivo", 1.0, 0.0, 1000.0);
        XP_SURVIVE_DAY = b.comment("Supervivencia: por dia de juego completo sin morir").defineInRange("diaSinMorir", 30.0, 0.0, 10000.0);
        b.pop().push("antiexploit");
        WINDOW_SECONDS = b.comment("Ventana de rendimientos decrecientes por fuente").defineInRange("ventanaSegundos", 300, 30, 3600);
        FREE_EVENTS = b.comment("Eventos de una misma fuente en la ventana antes de empezar a rendir menos").defineInRange("eventosLibres", 40, 1, 10000);
        CAP_PER_MINUTE = b.comment("Tope de XP por minuto, como fraccion de la XP del nivel actual (y minimo 30)")
                .defineInRange("topePorMinuto", 0.25, 0.01, 10.0);
        VARIETY_BONUS = b.comment("Bonus por variedad: +x por cada fuente distinta activa en la ventana (hasta 4)")
                .defineInRange("bonusVariedad", 0.10, 0.0, 1.0);
        b.pop();
        SPEC = b.build();
    }

    private LevelConfig() {
    }
}
