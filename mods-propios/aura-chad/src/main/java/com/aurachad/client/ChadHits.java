package com.aurachad.client;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.aurachad.AuraChadMod;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Golpes de pose (assets/aurachad/chad_hits.json, generado por tools/gen_bbmodel.py): instante, pose y fuerza de cada corte seco.
 * El cliente lanza un estallido de aura cuando la edad del modo chad de cada jugador cruza un golpe.
 */
public final class ChadHits {
    private ChadHits() {}

    public record Hit(int index, float t, String pose, float strength, JsonObject model) {}

    /** Quien quiera enterarse de cada estallido (el autotest): entidad, golpe, edad mostrada (s) y particulas creadas. */
    public interface Listener { void onBurst(int entityId, Hit hit, float shownSeconds, int particles); }
    public static volatile Listener listener;

    /** Duracion (s) del pulso del contorno neon tras cada golpe. */
    public static final float PULSE_SECONDS = 0.3f;

    private static final List<Hit> HITS = new ArrayList<>();
    private static float walkStart = 1e9f;

    static {
        try (var in = ChadHits.class.getResourceAsStream("/assets/" + AuraChadMod.MODID + "/chad_hits.json")) {
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            walkStart = root.get("walk_start").getAsFloat();
            int i = 0;
            for (var e : root.getAsJsonArray("hits")) {
                JsonObject o = e.getAsJsonObject();
                HITS.add(new Hit(i++, o.get("t").getAsFloat(), o.get("pose").getAsString(), o.get("strength").getAsFloat(), o.getAsJsonObject("model")));
            }
        } catch (Exception ex) {
            AuraChadMod.LOGGER.error("No se pudo leer chad_hits.json; sin estallidos de aura", ex);
        }
    }

    public static List<Hit> all() { return HITS; }

    /** Segundo en que empieza la caminata final (las piernas oscilan desde aqui). */
    public static float walkStart() { return walkStart; }

    /** Pulso 0..1.5 del contorno: la fuerza del ultimo golpe, que decae a 0 en PULSE_SECONDS. */
    public static float pulseAt(float seconds) {
        float best = 0f;
        for (Hit h : HITS) {
            float d = seconds - h.t;
            if (d >= 0f && d < PULSE_SECONDS) {
                float k = 1f - d / PULSE_SECONDS;
                best = Math.max(best, h.strength * k * k);
            }
        }
        return best;
    }
}
