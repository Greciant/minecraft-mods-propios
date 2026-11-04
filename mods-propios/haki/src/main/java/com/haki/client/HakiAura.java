package com.haki.client;

import com.haki.HakiVisuals;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Vector3f;

/**
 * Capas de ambiente del Haki (estilos 2 y 3): sigilo runico, anillos orbitales, columnas de luz, rocas flotantes,
 * espirales de energia, grietas, rayos gigantes del cielo, humo y brasas. Igual que los rayos, todo sale de
 * funciones del tiempo y de semillas (sin estado), asi no hay que sincronizar nada ni crear objetos por cuadro.
 * Los caminos (espirales, grietas, rayos del cielo) se agregan a HakiBolts y se dibujan con ellos; el resto son
 * billboards en "pools" o cintas propias.
 */
final class HakiAura {
    private HakiAura() {}

    /** Lista de billboards con un cuadro de hoja de sprites cada uno. */
    static final class Pool {
        final float[] d;
        final int max;
        int n;
        Pool(int max) { this.max = max; this.d = new float[max * 8]; }
        void clear() { n = 0; }
        void add(float x, float y, float z, float size, float alpha, int rgb, float roll, int frame) {
            if (n >= max || alpha <= 0.004f) return;
            int o = n++ * 8;
            d[o] = x; d[o + 1] = y; d[o + 2] = z; d[o + 3] = size; d[o + 4] = alpha;
            d[o + 5] = Float.intBitsToFloat(rgb); d[o + 6] = roll; d[o + 7] = frame;
        }
    }

    static final Pool SMOKE = new Pool(96), EMBERS = new Pool(320), DEBRIS = new Pool(256), HEAD_SIGIL = new Pool(8);
    // Modelos 3D: modelo, x, y, z, escala, giro Y, inclinacion X, opacidad, brillo
    static final float[] MESH = new float[9 * 160];
    static int meshes;
    static void mesh(int model, float x, float y, float z, float scale, float rotY, float rotX, float alpha, float tint) {
        if (meshes >= 160 || alpha <= 0.004f || scale <= 0.001f) return;
        int o = meshes++ * 9;
        MESH[o] = model; MESH[o + 1] = x; MESH[o + 2] = y; MESH[o + 3] = z; MESH[o + 4] = scale;
        MESH[o + 5] = rotY; MESH[o + 6] = rotX; MESH[o + 7] = alpha; MESH[o + 8] = tint;
    }

    // Sigilo del suelo: x, y, z, tamano, angulo, opacidad
    static final float[] GROUND_SIGIL = new float[6 * 8];
    static int groundSigils;
    // Bandas: x, y(suelo), z, radio, altura del centro, alto, inclinacion (rad), giro (rad), precesion (rad), opacidad
    static final float[] BANDS = new float[10 * 32];
    static int bands;
    // Columnas: x, y(suelo), z, alto, ancho, opacidad
    static final float[] BEAMS = new float[6 * 64];
    static int beams;

    private static final RandomSource R = RandomSource.create();

    static void begin() {
        SMOKE.clear(); EMBERS.clear(); DEBRIS.clear(); HEAD_SIGIL.clear();
        groundSigils = 0; bands = 0; beams = 0; meshes = 0;
    }

    static boolean hasGroundSigil() { return groundSigils > 0; }
    static boolean hasBands() { return bands > 0; }
    static boolean hasBeams() { return beams > 0; }

    /**
     * Agrega las capas de un lanzador. x, z: pies; gy: suelo; chestY, headY: alturas; todo relativo a la camara.
     */
    static void add(int id, float time, float x, float gy, float z, float chestY, float headY, float fade,
                    boolean ownFirstPerson, int style, boolean active) {
        boolean hybrid = style == HakiVisuals.STYLE_HYBRID;
        boolean procedural = style == HakiVisuals.STYLE_PROCEDURAL;
        if (hybrid) sigil(time, x, gy, z, headY, fade, ownFirstPerson);
        if (!hybrid && !procedural) return;

        float grow = HakiFxRender.easeOutCubic(Math.min(1f, time / 20f));
        if (HakiFxRender.skip("paths")) { smoke(id, time, x, gy, z, fade, ownFirstPerson); embers(id, time, x, gy, z, fade);
            orbitDebris(id, time, x, gy, z, fade, active); bands(time, x, gy, z, fade, active); beams(id, time, x, gy, z, fade); return; }
        if (HakiFxRender.skip("pools")) { if (!ownFirstPerson) spirals(id, time, x, gy, z, fade, grow); cracks(id, time, x, gy, z, fade);
            groundArcs(id, time, x, gy, z, fade); sky(id, time, x, gy, z, fade); return; }
        if (!ownFirstPerson) spirals(id, time, x, gy, z, fade, grow);
        if (procedural) { cracks(id, time, x, gy, z, fade); groundArcs(id, time, x, gy, z, fade); }
        sky(id, time, x, gy, z, fade);
        smoke(id, time, x, gy, z, fade, ownFirstPerson);
        embers(id, time, x, gy, z, fade);
        orbitDebris(id, time, x, gy, z, fade, active);
        if (HakiVisuals.MESH_ENABLED) { groundShards(id, time, x, gy, z, fade); crown(time, x, headY, z, fade, ownFirstPerson); }
        bands(time, x, gy, z, fade * (ownFirstPerson ? 0.6f : 1f), active);
        beams(id, time, x, gy, z, fade);
    }

    // ------------------------------------------------------------------ sigilo (estilo 3)
    private static void sigil(float time, float x, float gy, float z, float headY, float fade, boolean ownFirstPerson) {
        int o = groundSigils++ * 6;
        float g = HakiFxRender.easeOutCubic(Math.min(1f, time / 12f));
        GROUND_SIGIL[o] = x; GROUND_SIGIL[o + 1] = gy + 0.07f; GROUND_SIGIL[o + 2] = z;
        GROUND_SIGIL[o + 3] = HakiVisuals.SIGIL_GROUND_HALF * g;
        GROUND_SIGIL[o + 4] = (float) Math.toRadians(time * HakiVisuals.SIGIL_SPIN_DEG);
        GROUND_SIGIL[o + 5] = fade * HakiVisuals.SIGIL_GROUND_ALPHA * HakiFxRender.smooth(time / 5f);
        if (!ownFirstPerson) {
            float a = fade * HakiFxRender.smooth(time / 6f);
            HEAD_SIGIL.add(x, headY + HakiVisuals.SIGIL_HEAD_ABOVE + 0.08f * Mth.sin(time * 0.15f), z,
                HakiVisuals.SIGIL_HEAD_SIZE * (0.55f + 0.45f * g), a, 0xFFFFFF, -time * 0.04f, 0);
        }
    }

    // ------------------------------------------------------------------ espirales
    private static void spirals(int id, float time, float x, float gy, float z, float fade, float grow) {
        int cnt = HakiVisuals.SPIRAL_COUNT, n = 17;
        for (int s = 0; s < cnt; s++) {
            float alpha = fade * 0.9f * (0.7f + 0.3f * Mth.sin(time * 0.4f + s * 1.3f));
            int idx = HakiBolts.newPath(n, alpha, false);
            if (idx < 0) return;
            float a0 = s * Mth.TWO_PI / cnt + time * 0.20f;
            float hs = HakiVisuals.SPIRAL_HEIGHT * grow;
            float[] p = HakiBolts.PTS[idx], w = HakiBolts.W[idx];
            for (int k = 0; k < n; k++) {
                float h = k / (float) (n - 1);
                float ang = a0 + h * 2.8f;
                float rad = HakiVisuals.SPIRAL_RADIUS * (0.85f + 0.3f * Mth.sin(h * 6f + time * 0.35f + s * 1.7f));
                p[k * 3] = x + Mth.cos(ang) * rad;
                p[k * 3 + 1] = gy + 0.05f + h * hs;
                p[k * 3 + 2] = z + Mth.sin(ang) * rad;
                w[k] = HakiVisuals.SPIRAL_WIDTH * (float) Math.pow(Math.sin(Math.PI * h), 0.55) + 0.012f;
            }
            HakiBolts.U0[idx] = s * 0.17f;
        }
    }

    // ------------------------------------------------------------------ grietas procedurales (estilo 2)
    private static void cracks(int id, float time, float x, float gy, float z, float fade) {
        float grow = HakiFxRender.easeOutCubic(Math.min(1f, time / HakiVisuals.CRACK_GROW_TICKS));
        int cnt = HakiVisuals.CRACK_COUNT;
        for (int i = 0; i < cnt && HakiBolts.paths < HakiBolts.MAX_PATHS - 8; i++) {
            HakiBolts.RNG.setSeed(id * 7919L + i * 104729L);
            HakiBolts.FLK.setSeed(id * 31L + i * 17L + (long) Math.floor(time * 0.5f));
            float a = (i + HakiBolts.RNG.nextFloat() * 0.6f) * Mth.TWO_PI / cnt;
            float len = (HakiVisuals.CRACK_MIN_LENGTH
                + HakiBolts.RNG.nextFloat() * (HakiVisuals.CRACK_MAX_LENGTH - HakiVisuals.CRACK_MIN_LENGTH)) * grow;
            if (len < 0.2f) continue;
            float alpha = fade * (0.7f + 0.3f * Mth.sin(time * 0.25f + i * 1.9f));
            int first = HakiBolts.paths;
            HakiBolts.emit(x, gy + 0.045f, z, Mth.cos(a), 0f, Mth.sin(a), len, HakiVisuals.CRACK_LEVELS, len * 0.10f,
                HakiVisuals.CRACK_WIDTH, 0.02f, alpha, 2, 0.8f);
            for (int k = first; k < HakiBolts.paths; k++) {
                HakiBolts.FLAT[k] = true;
                float[] p = HakiBolts.PTS[k];
                for (int q = 0; q < HakiBolts.N[k]; q++) p[q * 3 + 1] = gy + 0.045f;
            }
        }
    }

    // ------------------------------------------------------------------ arcos que corren por el suelo (estilo 2)
    private static void groundArcs(int id, float time, float x, float gy, float z, float fade) {
        for (int i = 0; i < HakiVisuals.ARC_SLOTS && HakiBolts.paths < HakiBolts.MAX_PATHS - 8; i++) {
            int period = HakiVisuals.ARC_PERIOD_MIN + (i * 5) % 7;
            R.setSeed(id * 373587883L + i * 611953L);
            float u = time + R.nextFloat() * period;
            int cycle = Mth.floor(u / period);
            float lt = u - cycle * period;
            long seed = id * 898128001L + i * 15485867L + cycle * 32452867L;
            HakiBolts.RNG.setSeed(seed);
            HakiBolts.FLK.setSeed(seed ^ (long) Math.floor(time * 2f));
            int life = HakiVisuals.ARC_LIFE_MIN + HakiBolts.RNG.nextInt(HakiVisuals.ARC_LIFE_MAX - HakiVisuals.ARC_LIFE_MIN + 1);
            if (lt >= life) continue;
            float p = lt / life;
            float ang = HakiBolts.RNG.nextFloat() * Mth.TWO_PI;
            float full = HakiVisuals.ARC_MIN_LENGTH + HakiBolts.RNG.nextFloat() * (HakiVisuals.ARC_MAX_LENGTH - HakiVisuals.ARC_MIN_LENGTH);
            float s = Math.min(1f, p / 0.3f);
            float len = full * HakiFxRender.easeOutCubic(s);
            if (len < 0.3f) continue;
            float out = p < 0.4f ? 1f : (float) Math.pow(1f - (p - 0.4f) / 0.6f, 1.3);
            float alpha = fade * out * (1f - 0.35f * HakiBolts.FLK.nextFloat());
            int first = HakiBolts.paths;
            int n = HakiBolts.emit(x + Mth.cos(ang) * 0.5f, gy + 0.06f, z + Mth.sin(ang) * 0.5f, Mth.cos(ang), 0f, Mth.sin(ang),
                len, HakiVisuals.ARC_LEVELS, len * 0.07f, HakiVisuals.ARC_WIDTH, 0.02f, alpha, 2, 0.8f);
            int main = HakiBolts.paths - 1 - HakiBolts.lastForks;
            for (int k = first; k < HakiBolts.paths; k++) {
                HakiBolts.FLAT[k] = true;
                float[] pp = HakiBolts.PTS[k];
                for (int q = 0; q < HakiBolts.N[k]; q++) pp[q * 3 + 1] = gy + 0.06f;
            }
            if (p < 0.5f) { // chispa en la cabeza del arco
                float[] mp = HakiBolts.PTS[main];
                HakiBolts.addSpark(mp[(n - 1) * 3], gy + 0.25f, mp[(n - 1) * 3 + 2], 0.7f, alpha);
            }
        }
    }

    // ------------------------------------------------------------------ rayos gigantes del cielo
    private static void sky(int id, float time, float x, float gy, float z, float fade) {
        for (int slot = 0; slot < HakiVisuals.SKY_SLOTS; slot++) {
            int period = HakiVisuals.SKY_PERIOD + slot * 9;
            R.setSeed(id * 15485863L + slot * 32452843L);
            float u = time + R.nextFloat() * period;
            int cycle = Mth.floor(u / period);
            float lt = u - cycle * period;
            if (lt >= HakiVisuals.SKY_LIFE) continue;
            long seed = id * 49979687L + slot * 86028121L + cycle * 67867967L;
            HakiBolts.RNG.setSeed(seed);
            HakiBolts.FLK.setSeed(seed ^ (long) Math.floor(time * 2f));
            float p = lt / HakiVisuals.SKY_LIFE;
            float ang = HakiBolts.RNG.nextFloat() * Mth.TWO_PI;
            float rr = HakiVisuals.SKY_MIN_R + HakiBolts.RNG.nextFloat() * (HakiVisuals.SKY_MAX_R - HakiVisuals.SKY_MIN_R);
            float px = x + Mth.cos(ang) * rr, pz = z + Mth.sin(ang) * rr;
            float tx = (HakiBolts.RNG.nextFloat() - 0.5f) * 0.2f, tz = (HakiBolts.RNG.nextFloat() - 0.5f) * 0.2f;
            float dl = (float) Math.sqrt(tx * tx + 1 + tz * tz);
            float dx = tx / dl, dy = -1f / dl, dz = tz / dl;
            float len = HakiVisuals.SKY_HEIGHT / -dy;
            float alpha = fade * (p < 0.3f ? 1f : (1f - p) / 0.7f) * (1f - 0.4f * HakiBolts.FLK.nextFloat());
            HakiBolts.emit(px - dx * len, gy - dy * len, pz - dz * len, dx, dy, dz, len, 5, len * 0.045f,
                HakiVisuals.SKY_WIDTH, 0.14f, alpha, 3, 0.9f);
            // Destello del impacto (y de la pantalla, en el primer cuadro)
            HakiBolts.addSpark(px, gy + 0.3f, pz, 5.5f * (1f - 0.5f * p), alpha * 0.9f);
            if (lt < 1.2f) HakiScreenFx.skyStrike((float) Math.sqrt(px * px + gy * gy + pz * pz));
        }
    }

    // ------------------------------------------------------------------ humo
    private static void smoke(int id, float time, float x, float gy, float z, float fade, boolean ownFirstPerson) {
        for (int i = 0; i < HakiVisuals.SMOKE_COUNT; i++) {
            int period = 36 + (i % 5) * 4;
            R.setSeed(id * 982451653L + i * 472882049L);
            float u = time + R.nextFloat() * period;
            int cycle = Mth.floor(u / period);
            float p = (u - cycle * period) / period;
            R.setSeed(id * 982451653L + i * 472882049L + cycle * 179424673L);
            float ang = R.nextFloat() * Mth.TWO_PI, rr = 0.4f + R.nextFloat() * 2.8f;
            float rise = 1.6f + R.nextFloat() * 1.2f, sz = 0.6f + R.nextFloat() * 0.8f;
            float a = fade * HakiVisuals.SMOKE_ALPHA * (ownFirstPerson ? 0.6f : 1f)
                * HakiFxRender.smooth(p / 0.15f) * (1f - HakiFxRender.smooth((p - 0.55f) / 0.45f));
            SMOKE.add(x + Mth.cos(ang) * rr + 0.3f * Mth.sin(time * 0.1f + i), gy + 0.3f + p * rise,
                z + Mth.sin(ang) * rr, 0.9f + (sz + 1.2f) * p, a, 0xFFFFFF, 0f, Math.min(15, (int) (p * 16f)));
        }
    }

    // ------------------------------------------------------------------ brasas
    private static void embers(int id, float time, float x, float gy, float z, float fade) {
        for (int i = 0; i < HakiVisuals.EMBER_COUNT; i++) {
            int period = 24 + (i % 7) * 3;
            R.setSeed(id * 2038074743L + i * 1190494759L);
            float u = time + R.nextFloat() * period;
            int cycle = Mth.floor(u / period);
            float p = (u - cycle * period) / period;
            R.setSeed(id * 2038074743L + i * 1190494759L + cycle * 297428147L);
            float ang = R.nextFloat() * Mth.TWO_PI, rr = 0.3f + R.nextFloat() * 3.2f;
            float rise = 3f + R.nextFloat() * 4f, sz = 0.07f + 0.09f * R.nextFloat();
            float sway = 0.25f * Mth.sin(time * 0.2f + i * 1.7f) + 0.12f * Mth.sin(time * 0.53f + i * 4.1f);
            float sway2 = 0.22f * Mth.sin(time * 0.17f + i * 2.9f) + 0.1f * Mth.sin(time * 0.61f + i * 0.7f) + p * 0.6f * Mth.sin(i * 1.3f);
            float a = fade * HakiFxRender.smooth(p / 0.1f) * (float) Math.pow(1f - p, 0.8);
            int g = (int) (200 - 170 * p), b = (int) (140 - 130 * p);
            EMBERS.add(x + Mth.cos(ang) * rr + sway, gy + 0.1f + p * rise, z + Mth.sin(ang) * rr + sway2,
                sz * (1f - 0.5f * p) * 2.2f, a, 0xFF0000 | g << 8 | b, 0f, 0);
        }
    }

    // ------------------------------------------------------------------ rocas flotantes
    private static void orbitDebris(int id, float time, float x, float gy, float z, float fade, boolean active) {
        float grow = HakiFxRender.easeOutCubic(Math.min(1f, Math.max(0f, time - 2f) / 30f));
        for (int i = 0; i < HakiVisuals.DEBRIS_ORBIT_COUNT; i++) {
            R.setSeed(id * 1299709L + i * 15485863L);
            float rr = HakiVisuals.DEBRIS_MIN_R + R.nextFloat() * (HakiVisuals.DEBRIS_MAX_R - HakiVisuals.DEBRIS_MIN_R);
            float a0 = R.nextFloat() * Mth.TWO_PI, speed = (0.006f + R.nextFloat() * 0.014f) * (R.nextBoolean() ? 1 : -1);
            float h = 0.8f + R.nextFloat() * (HakiVisuals.DEBRIS_MAX_H - 0.8f);
            float sz = HakiVisuals.DEBRIS_MIN_SIZE + R.nextFloat() * (HakiVisuals.DEBRIS_MAX_SIZE - HakiVisuals.DEBRIS_MIN_SIZE);
            float spin = (R.nextFloat() - 0.5f) * 0.08f;
            float ang = a0 + time * speed;
            // Al soltar el Haki (fade baja) las rocas pierden altura y caen hacia el suelo
            float drop = active ? 0f : (1f - fade) * HakiVisuals.RELEASE_DROP;
            float y = gy + 0.3f + h * grow * (1f - drop) + (0.3f + 0.15f * Mth.sin(i * 2.1f)) * Mth.sin(time * 0.08f + i * 1.3f) * (1f - drop);
            if (HakiVisuals.MESH_ENABLED) {
                mesh(HakiModels.ROCK0 + (i % HakiModels.ROCKS), x + Mth.cos(ang) * rr * grow, y, z + Mth.sin(ang) * rr * grow,
                    sz * HakiVisuals.ROCK_SCALE, time * spin * 2f + i, time * spin * 1.3f + i * 0.7f,
                    Math.min(1f, fade * 1.5f) * grow, HakiVisuals.MESH_TINT);
            } else {
                DEBRIS.add(x + Mth.cos(ang) * rr * grow, y, z + Mth.sin(ang) * rr * grow, sz, Math.min(1f, fade * 1.5f) * grow, 0xFFFFFF,
                    time * spin + i, i & 3);
            }
        }
    }

    // ------------------------------------------------------------------ pinchos de obsidiana que brotan del suelo
    private static void groundShards(int id, float time, float x, float gy, float z, float fade) {
        for (int i = 0; i < HakiVisuals.GSHARD_COUNT; i++) {
            R.setSeed(id * 7368787L + i * 12582917L);
            float ang = (i + R.nextFloat() * 0.8f) * Mth.TWO_PI / HakiVisuals.GSHARD_COUNT;
            float rr = HakiVisuals.GSHARD_MIN_R + R.nextFloat() * (HakiVisuals.GSHARD_MAX_R - HakiVisuals.GSHARD_MIN_R);
            float h = HakiVisuals.GSHARD_MIN_H + R.nextFloat() * (HakiVisuals.GSHARD_MAX_H - HakiVisuals.GSHARD_MIN_H);
            float lean = HakiVisuals.GSHARD_LEAN * (0.4f + 0.6f * R.nextFloat());
            // Brotan en onda, de dentro hacia fuera, al ritmo de la onda de choque
            float delay = rr / HakiVisuals.DOME_RADIUS * HakiVisuals.DOME_GROW_TICKS * 0.7f + R.nextFloat() * 2f;
            float u = Math.max(0f, time - delay) / HakiVisuals.GSHARD_EMERGE_TICKS;
            float e = u >= 1f ? 1f : 1f - (float) Math.pow(1f - u, 3) + 0.08f * Mth.sin(u * 3.1416f); // pequeno rebote
            float rumble = 1f + 0.025f * Mth.sin(time * 1.9f + i * 2.3f);
            float s = h * e * fade * rumble;
            mesh(HakiModels.SHARD0 + (i % HakiModels.SHARDS), x + Mth.cos(ang) * rr, gy - 0.05f, z + Mth.sin(ang) * rr,
                s, -ang + 1.5708f, lean, fade, HakiVisuals.MESH_TINT);
        }
    }

    // ------------------------------------------------------------------ corona sobre la cabeza
    private static void crown(float time, float x, float headY, float z, float fade, boolean ownFirstPerson) {
        if (ownFirstPerson) return;
        float a = fade * HakiFxRender.smooth(time / 8f);
        float g = HakiFxRender.easeOutCubic(Math.min(1f, time / 14f));
        mesh(HakiModels.CROWN, x, headY + HakiVisuals.CROWN_ABOVE + 0.07f * Mth.sin(time * 0.15f), z,
            HakiVisuals.CROWN_SCALE * (0.4f + 0.6f * g), time * HakiVisuals.CROWN_SPIN, 0f, a, HakiVisuals.MESH_TINT);
    }

    // ------------------------------------------------------------------ anillos orbitales
    private static void bands(float time, float x, float gy, float z, float fade, boolean active) {
        float g = HakiFxRender.easeOutCubic(Math.min(1f, Math.max(0f, time - 4f) / HakiVisuals.BAND_GROW_TICKS));
        if (g <= 0.01f) return;
        for (float[] b : HakiVisuals.BANDS) {
            if (bands >= 32) return;
            int o = bands++ * 10;
            BANDS[o] = x; BANDS[o + 1] = gy; BANDS[o + 2] = z;
            BANDS[o + 3] = b[0] * g; BANDS[o + 4] = b[1];
            BANDS[o + 5] = b[2] * (active ? 1f : 0.35f + 0.65f * fade); // se afinan al soltar
            BANDS[o + 6] = (float) Math.toRadians(b[3]);
            BANDS[o + 7] = time * b[4];
            BANDS[o + 8] = time * 0.004f * (b[4] > 0 ? 1 : -1);
            BANDS[o + 9] = fade * HakiVisuals.BAND_ALPHA * HakiFxRender.smooth(g * 2f);
        }
    }

    /** Dibuja las instancias de modelos 3D (el PoseStack ya esta en coordenadas relativas a la camara). */
    static void drawMeshes(VertexConsumer vc, PoseStack pose) {
        for (int i = 0; i < meshes; i++) {
            int o = i * 9;
            int model = (int) MESH[o];
            pose.pushPose();
            pose.translate(MESH[o + 1], MESH[o + 2], MESH[o + 3]);
            pose.mulPose(com.mojang.math.Axis.YP.rotation(MESH[o + 5]));
            pose.mulPose(com.mojang.math.Axis.XP.rotation(MESH[o + 6]));
            pose.scale(MESH[o + 4], MESH[o + 4], MESH[o + 4]);
            pose.translate(-0.5f, HakiModels.centered(model) ? -0.5f : 0f, -0.5f);
            HakiModels.draw(vc, pose.last(), model, MESH[o + 7], MESH[o + 8]);
            pose.popPose();
        }
    }

    static void drawBands(VertexConsumer vc, PoseStack.Pose pose) {
        int segs = HakiVisuals.BAND_SEGMENTS;
        for (int bi = 0; bi < bands; bi++) {
            int o = bi * 10;
            float cx = BANDS[o], cy = BANDS[o + 1] + BANDS[o + 4], cz = BANDS[o + 2];
            float rad = BANDS[o + 3], bh = BANDS[o + 5] * 0.5f, tilt = BANDS[o + 6], rot = BANDS[o + 7];
            float prec = BANDS[o + 8], alpha = BANDS[o + 9];
            // normal del plano del anillo y dos ejes dentro de el
            float nx = Mth.sin(tilt) * Mth.cos(prec), ny = Mth.cos(tilt), nz = Mth.sin(tilt) * Mth.sin(prec);
            float rx, ry, rz;
            if (Math.abs(ny) < 0.9f) { rx = 0; ry = 1; rz = 0; } else { rx = 1; ry = 0; rz = 0; }
            float e1x = ry * nz - rz * ny, e1y = rz * nx - rx * nz, e1z = rx * ny - ry * nx;
            float l1 = (float) Math.sqrt(e1x * e1x + e1y * e1y + e1z * e1z);
            e1x /= l1; e1y /= l1; e1z /= l1;
            float e2x = ny * e1z - nz * e1y, e2y = nz * e1x - nx * e1z, e2z = nx * e1y - ny * e1x;
            for (int j = 0; j < segs; j++) {
                float t0 = rot + Mth.TWO_PI * j / segs, t1 = rot + Mth.TWO_PI * (j + 1) / segs;
                float c0 = Mth.cos(t0) * rad, s0 = Mth.sin(t0) * rad, c1 = Mth.cos(t1) * rad, s1 = Mth.sin(t1) * rad;
                float ax = cx + e1x * c0 + e2x * s0, ay = cy + e1y * c0 + e2y * s0, az = cz + e1z * c0 + e2z * s0;
                float bx = cx + e1x * c1 + e2x * s1, by = cy + e1y * c1 + e2y * s1, bz = cz + e1z * c1 + e2z * s1;
                HakiFxRender.vertex(vc, pose, ax + nx * bh, ay + ny * bh, az + nz * bh, 0f, 0f, alpha);
                HakiFxRender.vertex(vc, pose, ax - nx * bh, ay - ny * bh, az - nz * bh, 0f, 1f, alpha);
                HakiFxRender.vertex(vc, pose, bx - nx * bh, by - ny * bh, bz - nz * bh, 0.25f, 1f, alpha);
                HakiFxRender.vertex(vc, pose, bx + nx * bh, by + ny * bh, bz + nz * bh, 0.25f, 0f, alpha);
            }
        }
    }

    // ------------------------------------------------------------------ columnas de luz
    private static void beams(int id, float time, float x, float gy, float z, float fade) {
        float g = HakiFxRender.easeOutCubic(Math.min(1f, Math.max(0f, time - 6f) / 20f));
        if (g <= 0.01f) return;
        for (int i = 0; i < HakiVisuals.BEAM_COUNT && beams < 64; i++) {
            R.setSeed(id * 6700417L + i * 2147483629L);
            float ang = (i + R.nextFloat() * 0.7f) * Mth.TWO_PI / HakiVisuals.BEAM_COUNT;
            float rr = HakiVisuals.BEAM_MIN_R + R.nextFloat() * (HakiVisuals.BEAM_MAX_R - HakiVisuals.BEAM_MIN_R);
            int o = beams++ * 6;
            BEAMS[o] = x + Mth.cos(ang) * rr; BEAMS[o + 1] = gy; BEAMS[o + 2] = z + Mth.sin(ang) * rr;
            BEAMS[o + 3] = HakiVisuals.BEAM_HEIGHT * g;
            BEAMS[o + 4] = HakiVisuals.BEAM_WIDTH * (0.7f + 0.6f * R.nextFloat());
            BEAMS[o + 5] = fade * HakiVisuals.BEAM_ALPHA * (0.75f + 0.25f * Mth.sin(time * 0.3f + i * 2f));
        }
    }

    /** Columnas siempre de frente a la camara (que esta en el origen), giradas solo alrededor del eje vertical. */
    static void drawBeams(VertexConsumer vc, PoseStack.Pose pose) {
        for (int i = 0; i < beams; i++) {
            int o = i * 6;
            float x = BEAMS[o], y = BEAMS[o + 1], z = BEAMS[o + 2], h = BEAMS[o + 3], hw = BEAMS[o + 4] * 0.5f, a = BEAMS[o + 5];
            float l = (float) Math.sqrt(x * x + z * z);
            if (l < 0.5f) continue;
            float hx = z / l * hw, hz = -x / l * hw;
            HakiFxRender.vertex(vc, pose, x - hx, y + h, z - hz, 0f, 0f, a);
            HakiFxRender.vertex(vc, pose, x - hx, y, z - hz, 0f, 1f, a);
            HakiFxRender.vertex(vc, pose, x + hx, y, z + hz, 1f, 1f, a);
            HakiFxRender.vertex(vc, pose, x + hx, y + h, z + hz, 1f, 0f, a);
        }
    }

    // ------------------------------------------------------------------ dibujo de pools
    /** Billboards con roll y cuadro de una hoja de cols x rows. */
    static void drawPool(VertexConsumer vc, PoseStack.Pose pose, Pool pool, Vector3f left, Vector3f up, int cols, int rows) {
        float du = 1f / cols, dv = 1f / rows;
        for (int i = 0; i < pool.n; i++) {
            int o = i * 8;
            float[] d = pool.d;
            float half = d[o + 3] * 0.5f, roll = d[o + 6];
            int rgb = Float.floatToRawIntBits(d[o + 5]);
            float r = (rgb >> 16 & 255) / 255f, g = (rgb >> 8 & 255) / 255f, b = (rgb & 255) / 255f;
            float c = Mth.cos(roll) * half, s = Mth.sin(roll) * half;
            float lx = left.x() * c + up.x() * s, ly = left.y() * c + up.y() * s, lz = left.z() * c + up.z() * s;
            float ux = -left.x() * s + up.x() * c, uy = -left.y() * s + up.y() * c, uz = -left.z() * s + up.z() * c;
            int frame = (int) d[o + 7];
            float u0 = (frame % cols) * du, v0 = (frame / cols) * dv;
            float x = d[o], y = d[o + 1], z = d[o + 2], a = d[o + 4];
            HakiFxRender.vertexC(vc, pose, x + lx + ux, y + ly + uy, z + lz + uz, u0, v0, r, g, b, a);
            HakiFxRender.vertexC(vc, pose, x + lx - ux, y + ly - uy, z + lz - uz, u0, v0 + dv, r, g, b, a);
            HakiFxRender.vertexC(vc, pose, x - lx - ux, y - ly - uy, z - lz - uz, u0 + du, v0 + dv, r, g, b, a);
            HakiFxRender.vertexC(vc, pose, x - lx + ux, y - ly + uy, z - lz + uz, u0 + du, v0, r, g, b, a);
        }
    }
}
