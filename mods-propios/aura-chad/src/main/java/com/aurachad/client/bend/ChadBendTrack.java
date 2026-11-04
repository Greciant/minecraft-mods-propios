package com.aurachad.client.bend;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.aurachad.AuraChadMod;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.Mth;

/**
 * Codos y rodillas de la secuencia (assets/aurachad/chad_bend.json, generado por tools/gen_bbmodel.py): por hueso, claves de
 * tiempo con el bend en grados, el eje del bend en grados y (brazos) la direccion objetivo del antebrazo. Sin imports de bendy-lib.
 */
public final class ChadBendTrack {
    private ChadBendTrack() {}

    public record Track(float[] t, float[] bend, float[] axis, float[] bendPre, float[] axisPre, float[][] target) {}

    private static final Map<String, Track> TRACKS = new HashMap<>();

    static {
        try (var in = ChadBendTrack.class.getResourceAsStream("/assets/" + AuraChadMod.MODID + "/chad_bend.json")) {
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var e : root.entrySet()) {
                JsonObject o = e.getValue().getAsJsonObject();
                float[] t = floats(o.getAsJsonArray("t")), b = floats(o.getAsJsonArray("bend")), a = floats(o.getAsJsonArray("axis"));
                float[] bp = o.has("bendPre") ? floats(o.getAsJsonArray("bendPre")) : b, ap = o.has("axisPre") ? floats(o.getAsJsonArray("axisPre")) : a;
                float[][] tg = null;
                if (o.has("target")) {
                    JsonArray ta = o.getAsJsonArray("target");
                    tg = new float[ta.size()][];
                    for (int i = 0; i < tg.length; i++) tg[i] = ta.get(i).isJsonNull() ? null : floats(ta.get(i).getAsJsonArray());
                }
                TRACKS.put(e.getKey(), new Track(t, b, a, bp, ap, tg));
            }
        } catch (Exception ex) {
            AuraChadMod.LOGGER.error("No se pudo leer chad_bend.json; sin codos ni rodillas", ex);
        }
    }

    private static float[] floats(JsonArray a) {
        float[] r = new float[a.size()];
        for (int i = 0; i < r.length; i++) { JsonElement x = a.get(i); r[i] = x.getAsFloat(); }
        return r;
    }

    public static boolean has(String bone) { return TRACKS.containsKey(bone); }

    /**
     * out[0] = bend (radianes), out[1] = eje (radianes). Meseta: vale el post de la ultima clave con tiempo <= t (el golpe ya cuenta);
     * entre dos claves con valores distintos (pasos de la caminata) smoothstep del post al pre siguiente.
     */
    public static void sample(String bone, float seconds, float[] out) {
        Track k = TRACKS.get(bone);
        if (k == null) { out[0] = 0f; out[1] = 0f; return; }
        float[] ts = k.t;
        int i = 0;
        while (i < ts.length - 1 && seconds >= ts[i + 1]) i++;
        if (i >= ts.length - 1 || seconds < ts[0]) { out[0] = k.bend[Math.min(i, ts.length - 1)]; out[1] = k.axis[Math.min(i, ts.length - 1)]; }
        else {
            float span = ts[i + 1] - ts[i];
            float f = span <= 0f ? 1f : Mth.clamp((seconds - ts[i]) / span, 0f, 1f);
            f = f * f * (3f - 2f * f);
            out[0] = Mth.lerp(f, k.bend[i], k.bendPre[i + 1]);
            out[1] = Mth.lerp(f, k.axis[i], k.axisPre[i + 1]);
        }
        out[0] = (float) Math.toRadians(out[0]);
        out[1] = (float) Math.toRadians(out[1]);
    }

    /** Direccion objetivo del antebrazo (afuera, arriba, frente) de la meseta vigente en t (la del ultimo golpe); null si no hay. */
    public static float[] targetAt(String bone, float seconds) {
        Track k = TRACKS.get(bone);
        if (k == null || k.target == null) return null;
        int best = 0;
        for (int i = 0; i < k.t.length; i++) if (k.t[i] <= seconds) best = i;
        return k.target[best];
    }
}
