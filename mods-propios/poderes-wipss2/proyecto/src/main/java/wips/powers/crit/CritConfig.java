package wips.powers.crit;

import net.neoforged.neoforge.common.ModConfigSpec;

/** config/wipspowers-common.toml: criticos con impact frame y permisos de terreno. */
public final class CritConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue CHANCE;
    public static final ModConfigSpec.DoubleValue MULTIPLIER;
    public static final ModConfigSpec.IntValue COOLDOWN_TICKS;
    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.BooleanValue IGNORE_BREAK_VETO;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("critical");
        ENABLED = b.comment("Criticos con impact frame (Ridiculous Impact Frames). false = sin criticos.").define("enabled", true);
        CHANCE = b.comment("Probabilidad de critico por golpe o habilidad de un jugador (0.25 = 25%).").defineInRange("chance", 0.25, 0.0, 1.0);
        MULTIPLIER = b.comment("Multiplicador de dano del critico.").defineInRange("damageMultiplier", 3.0, 1.0, 20.0);
        COOLDOWN_TICKS = b.comment("Ticks minimos entre criticos del mismo jugador (20 = 1 s). Evita que rayos y danos continuos saturen.")
                .defineInRange("cooldownTicks", 20, 0, 1200);
        b.pop();
        b.push("terrain");
        IGNORE_BREAK_VETO = b.comment("Las habilidades de Awakened que rompen bloques (Ruin Breach, etc.) preguntan a los demas mods si se puede romper.",
                "Si algun mod dice que no a todo, salen mensajes como 'Nothing here can rot away'. true = se ignora esa respuesta",
                "(se siguen respetando la proteccion del spawn y el modo aventura). false = comportamiento original de Awakened.")
                .define("ignoreBreakVeto", true);
        b.pop();
        SPEC = b.build();
    }

    private CritConfig() {
    }
}
