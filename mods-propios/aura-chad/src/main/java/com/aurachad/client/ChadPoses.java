package com.aurachad.client;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.TreeMap;

import com.aurachad.AuraChadMod;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * Secuencia de poses (~21,8 s) del video: parado timido, brazo extendido, doble biceps de frente, biceps de perfil,
 * biceps con un brazo arriba y se da la vuelta para salir de cuadro. Cada pose es un vector de angulos (radianes).
 * Entre golpes la pose queda quieta y en cada golpe salta de una vez a la nueva (claves {"pre","post"} de Bedrock); solo lo que cambia
 * de forma continua (acercamiento de camara) se interpola, con smoothstep. Convencion: brazo derecho zRot + = hacia afuera, izquierdo zRot - = hacia afuera.
 */
public final class ChadPoses {
    private ChadPoses() {}

    /** Animacion editable en Blockbench (ref/blockbench/chad_poses.bbmodel); se exporta como animacion Bedrock a este JSON. */
    private static final ResourceLocation ANIM = ResourceLocation.fromNamespaceAndPath(AuraChadMod.MODID, "animations/chad.animation.json");

    // Indices
    private static final int RX = 0, RY = 1, RZ = 2, LX = 3, LY = 4, LZ = 5, HX = 6, HY = 7, BX = 8, YAW = 9, SPREAD = 10, CHEST = 11, WALK = 12, ZOOM = 13;
    private static final int N = 14;

    /** indice, hueso, canal, componente, convertir grados a radianes, valor por defecto */
    private static final Object[][] MAP = {
        {RX, "right_arm", "rotation", 0, true, 0f}, {RY, "right_arm", "rotation", 1, true, 0f}, {RZ, "right_arm", "rotation", 2, true, 0f},
        {LX, "left_arm", "rotation", 0, true, 0f}, {LY, "left_arm", "rotation", 1, true, 0f}, {LZ, "left_arm", "rotation", 2, true, 0f},
        {HX, "head", "rotation", 0, true, 0f}, {HY, "head", "rotation", 1, true, 0f}, {BX, "body", "rotation", 0, true, 0f},
        {YAW, "chad_root", "rotation", 1, false, 0f}, {SPREAD, "right_leg", "rotation", 2, true, 0f},
        {CHEST, "body", "scale", 0, false, 1f}, {WALK, "chad_walk", "position", 0, false, 0f}, {ZOOM, "chad_camera", "position", 0, false, 1f},
    };

    private static final float[][] TIMES = new float[N][];
    /** Valor al llegar a la clave (pre) y al salir de ella (post); distintos = corte seco. */
    private static final float[][] PRE = new float[N][];
    private static final float[][] POST = new float[N][];
    public static final float TOTAL_SECONDS;

    static {
        float total = 21.8f;
        try (var in = ChadPoses.class.getResourceAsStream("/assets/" + ANIM.getNamespace() + "/" + ANIM.getPath())) {
            JsonObject anim = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject()
                .getAsJsonObject("animations").getAsJsonObject("chad");
            total = anim.get("animation_length").getAsFloat();
            JsonObject bones = anim.getAsJsonObject("bones");
            for (Object[] m : MAP) {
                int idx = (Integer) m[0];
                JsonObject chan = bones.has((String) m[1]) && bones.getAsJsonObject((String) m[1]).has((String) m[2])
                    ? bones.getAsJsonObject((String) m[1]).getAsJsonObject((String) m[2]) : null;
                TreeMap<Float, float[]> keys = new TreeMap<>();   // t -> {pre, post}
                if (chan != null) {
                    for (var e : chan.entrySet()) {
                        JsonElement v = e.getValue();
                        float pre, post;
                        if (v.isJsonObject()) {
                            JsonObject o = v.getAsJsonObject();
                            post = value(o.has("post") ? o.get("post") : o.get("pre"), m);
                            pre = value(o.has("pre") ? o.get("pre") : o.get("post"), m);
                        } else pre = post = value(v, m);
                        keys.put(Float.parseFloat(e.getKey()), new float[]{pre, post});
                    }
                }
                if (keys.isEmpty()) keys.put(0f, new float[]{(Float) m[5], (Float) m[5]});
                TIMES[idx] = new float[keys.size()];
                PRE[idx] = new float[keys.size()];
                POST[idx] = new float[keys.size()];
                int i = 0;
                for (var e : keys.entrySet()) { TIMES[idx][i] = e.getKey(); PRE[idx][i] = e.getValue()[0]; POST[idx][i++] = e.getValue()[1]; }
            }
        } catch (Exception ex) {
            AuraChadMod.LOGGER.error("No se pudo leer {}; poses por defecto", ANIM, ex);
            for (Object[] m : MAP) {
                TIMES[(Integer) m[0]] = new float[]{0f};
                PRE[(Integer) m[0]] = new float[]{(Float) m[5]};
                POST[(Integer) m[0]] = new float[]{(Float) m[5]};
            }
        }
        TOTAL_SECONDS = total;
    }

    /** Un valor suelto o un vector Bedrock; convierte grados a radianes y aplica el signo de Blockbench al Z de las rotaciones. */
    private static float value(JsonElement v, Object[] m) {
        float f = v.isJsonArray() ? comp(v.getAsJsonArray().get((Integer) m[3])) : comp(v);
        if ("rotation".equals(m[2]) && (Integer) m[3] == 2) f = -f;
        return (Boolean) m[4] ? (float) Math.toRadians(f) : f;
    }

    private static float comp(JsonElement e) {
        JsonPrimitive p = e.getAsJsonPrimitive();
        return p.isNumber() ? p.getAsFloat() : Float.parseFloat(p.getAsString());
    }

    private static final float[] TMP = new float[N];

    /** Meseta: vale el post de la ultima clave con tiempo <= t (el golpe ya cuenta); entre claves con valores distintos (camara) smoothstep del post al pre siguiente. */
    public static void sample(float seconds, float[] out) {
        float t = Mth.clamp(seconds, 0f, TOTAL_SECONDS);
        for (int j = 0; j < N; j++) {
            float[] ts = TIMES[j];
            int i = 0;
            while (i < ts.length - 1 && t >= ts[i + 1]) i++;
            if (i >= ts.length - 1 || t < ts[0]) { out[j] = POST[j][Math.min(i, ts.length - 1)]; continue; }
            float span = ts[i + 1] - ts[i];
            float k = span <= 0f ? 1f : Mth.clamp((t - ts[i]) / span, 0f, 1f);
            k = k * k * (3f - 2f * k);
            out[j] = Mth.lerp(k, POST[j][i], PRE[j][i + 1]);
        }
    }

    /** Multiplicador de la distancia de camara (acercamientos del video). */
    public static float zoomAt(float seconds) {
        sample(seconds, TMP);
        return TMP[ZOOM];
    }

    /** Giro extra (grados) que se suma al cuerpo en el renderizado. */
    public static float yawAt(float seconds) {
        sample(seconds, TMP);
        return TMP[YAW];
    }

    public static void apply(PlayerModel<?> m, LivingEntity entity) {
        // Con Player Animation Library las poses las pone la capa aurachad:chad; este Mixin queda de respaldo
        if (com.aurachad.client.pal.ChadPalBridge.drives(entity)) return;
        float age = ChadClient.ageSeconds(entity.getId());
        if (age < 0f) {
            if (m.body.xScale != 1f || m.rightArm.xScale != 1f) reset(m);
            return;
        }
        float[] p = TMP;
        sample(age, p);
        // Latido de "musculo" en las poses de fuerza
        float flex = 0.5f + 0.5f * Mth.sin(age * 9f);
        float pump = (p[CHEST] - 1f);

        m.rightArm.xRot = p[RX]; m.rightArm.yRot = p[RY]; m.rightArm.zRot = p[RZ] + pump * 0.15f * flex;
        m.leftArm.xRot = p[LX]; m.leftArm.yRot = p[LY]; m.leftArm.zRot = p[LZ] - pump * 0.15f * flex;
        m.head.xRot = p[HX]; m.head.yRot = p[HY]; m.head.zRot = 0f;
        m.body.xRot = p[BX]; m.body.yRot = 0f; m.body.zRot = 0f;

        float walk = p[WALK];
        float sw = Mth.sin((age - ChadHits.walkStart()) * 8f) * 0.8f * walk;   // la caminata arranca de golpe, con la fase en cero
        m.rightLeg.xRot = sw; m.leftLeg.xRot = -sw;
        m.rightLeg.yRot = 0f; m.leftLeg.yRot = 0f;
        m.rightLeg.zRot = p[SPREAD]; m.leftLeg.zRot = -p[SPREAD];
        m.rightLeg.z = 0.1f; m.leftLeg.z = 0.1f;
        m.rightLeg.y = 12f; m.leftLeg.y = 12f;

        float c = p[CHEST] + pump * 0.04f * flex;
        m.body.xScale = c; m.body.zScale = 1f + (c - 1f) * 0.6f;
        // Los brazos engordan un poco con la pose
        float big = 1f + pump * 0.9f;
        m.rightArm.xScale = big; m.leftArm.xScale = big; m.rightArm.zScale = big; m.leftArm.zScale = big;

        sync(m);
    }

    private static void reset(PlayerModel<?> m) {
        for (ModelPart part : new ModelPart[]{m.body, m.rightArm, m.leftArm, m.jacket, m.rightSleeve, m.leftSleeve}) {
            part.xScale = 1f; part.zScale = 1f;
        }
        m.rightLeg.zRot = 0f; m.leftLeg.zRot = 0f;
    }

    /** setupAnim ya copio las capas exteriores antes de nuestro cambio; hay que repetirlo. */
    private static void sync(PlayerModel<?> m) {
        m.hat.copyFrom(m.head);
        m.jacket.copyFrom(m.body);
        m.leftSleeve.copyFrom(m.leftArm);
        m.rightSleeve.copyFrom(m.rightArm);
        m.leftPants.copyFrom(m.leftLeg);
        m.rightPants.copyFrom(m.rightLeg);
        m.jacket.xScale = m.body.xScale; m.jacket.zScale = m.body.zScale;
        m.leftSleeve.xScale = m.leftArm.xScale; m.leftSleeve.zScale = m.leftArm.zScale;
        m.rightSleeve.xScale = m.rightArm.xScale; m.rightSleeve.zScale = m.rightArm.zScale;
    }
}
