package com.haki.client;

import com.haki.HakiVisuals;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Vector3f;

/**
 * Relampagos del Haki. Sin estado entre cuadros: cada "casilla" de rayo repite un ciclo (periodo propio y
 * fase propia) y la forma de cada rayo sale de una semilla (casilla, ciclo), asi los rayos aparecen
 * escalonados, salen disparados, se desvanecen y reaparecen en otra direccion.
 * Cada cuadro se calcula la geometria una sola vez (begin/add...) y luego se dibuja en pasadas:
 * halo rojo ancho, nucleo negro que se afina hacia la punta, laminas de sprites y chispas blancas.
 * Los "caminos" tambien los usan las espirales, las grietas y los rayos del cielo (HakiAura).
 */
final class HakiBolts {
    private HakiBolts() {}

    static final int MAX_POINTS = 33;
    static final int MAX_PATHS = 640;
    static final int MAX_SPARKS = 256;
    static final int MAX_SPRITES = 96;

    static final float[][] PTS = new float[MAX_PATHS][MAX_POINTS * 3];
    static final float[][] W = new float[MAX_PATHS][MAX_POINTS];
    static final int[] N = new int[MAX_PATHS];
    static final float[] ALPHA = new float[MAX_PATHS];
    static final float[] U0 = new float[MAX_PATHS];
    static final boolean[] FLAT = new boolean[MAX_PATHS];  // cinta horizontal (grietas) en vez de mirar a la camara
    static final float[] SPARKS = new float[MAX_SPARKS * 5]; // x, y, z, tamano, opacidad
    static final float[] SPR = new float[MAX_SPRITES * 11];  // origen, direccion, largo, opacidad, cuadro, espejo, ancho
    static int paths, sparks, sprites;

    static final RandomSource RNG = RandomSource.create();  // forma base del rayo (fija durante todo su ciclo)
    static final RandomSource FLK = RandomSource.create();  // parpadeo (cambia cada medio tick)
    private static final float[] O1 = new float[MAX_POINTS], O2 = new float[MAX_POINTS];

    static void begin() {
        paths = 0;
        sparks = 0;
        sprites = 0;
    }

    /** Reserva un camino vacio (el que llama rellena PTS/W) o -1 si no cabe. */
    static int newPath(int n, float alpha, boolean flat) {
        if (paths >= MAX_PATHS) return -1;
        int i = paths++;
        N[i] = n;
        ALPHA[i] = alpha;
        U0[i] = 0f;
        FLAT[i] = flat;
        return i;
    }

    static void addSpark(float x, float y, float z, float size, float alpha) {
        if (sparks >= MAX_SPARKS) return;
        int o = sparks++ * 5;
        SPARKS[o] = x; SPARKS[o + 1] = y; SPARKS[o + 2] = z; SPARKS[o + 3] = size; SPARKS[o + 4] = alpha;
    }

    /**
     * Agrega los rayos radiales de un lanzador. (ox, oy, oz) es el origen relativo a la camara; (avx, avy, avz) es
     * una direccion unitaria hacia la que no se dibujan rayos (con coseno mayor que avoidCos) para que no tapen la vista.
     * sprite = true: en vez de un camino genera una lamina del flipbook (estilos 1 y 3).
     */
    static void add(int casterId, float time, float ox, float oy, float oz, float fade,
                    int slots, float startOffset, float widthScale,
                    float avx, float avy, float avz, float avoidCos, float maxUp, boolean sprite) {
        int flickStep = (int) Math.floor(time * 2f);
        boolean burst = time < HakiVisuals.BURST_TICKS;
        for (int i = 0; i < slots && paths < MAX_PATHS - 8 && sprites < MAX_SPRITES; i++) {
            int period = HakiVisuals.BOLT_PERIOD_MIN + (i * 7) % 5;
            RNG.setSeed(casterId * 668265263L + i * 374761393L);
            float u = time + RNG.nextFloat() * period;
            int cycle = Mth.floor(u / period);
            float lt = u - cycle * period;
            long seed = casterId * 341873128712L + i * 9781L + cycle * 132897987541L;
            if (burst) { // estallido: todos los rayos disparan juntos en los primeros ticks
                seed = casterId * 341873128712L + i * 9781L + 424242L;
                lt = time;
            }
            RNG.setSeed(seed);
            int life = HakiVisuals.BOLT_LIFE_MIN + RNG.nextInt(HakiVisuals.BOLT_LIFE_MAX - HakiVisuals.BOLT_LIFE_MIN + 1);
            if (burst) life += HakiVisuals.BURST_TICKS / 2;
            if (lt >= life) continue;
            float p = lt / life;

            // Direccion uniforme en la esfera
            float z = RNG.nextFloat() * 2f - 1f;
            float phi = RNG.nextFloat() * (float) (Math.PI * 2);
            float r = (float) Math.sqrt(1f - z * z);
            float dx = r * (float) Math.cos(phi), dy = z, dz = r * (float) Math.sin(phi);
            float fullLen = HakiVisuals.BOLT_MIN_LENGTH
                + RNG.nextFloat() * (HakiVisuals.BOLT_MAX_LENGTH - HakiVisuals.BOLT_MIN_LENGTH);
            if (burst) fullLen *= HakiVisuals.BURST_LEN_MUL;
            float wRnd = 0.7f + 0.6f * RNG.nextFloat();
            if (dy > maxUp || dx * avx + dy * avy + dz * avz > avoidCos) continue;

            // Sale disparado: 1 - (1 - x)^3
            float s = Math.min(1f, p / HakiVisuals.BOLT_SHOOT_FRAC);
            float len = fullLen * (1f - (1f - s) * (1f - s) * (1f - s));
            if (len < 0.1f) continue;
            float hold = HakiVisuals.BOLT_HOLD_FRAC;
            float out = p < hold ? 1f : (float) Math.pow(1f - (p - hold) / (1f - hold), 1.4);
            FLK.setSeed(seed ^ (flickStep * 0x9E3779B97F4A7C15L));
            float alpha = fade * out * (1f - HakiVisuals.BOLT_FLICKER * FLK.nextFloat());
            if (alpha <= 0.01f) continue;

            float sx = ox + dx * startOffset, sy = oy + dy * startOffset, sz = oz + dz * startOffset;
            if (sprite) {
                int o = sprites++ * 11;
                float quadLen = len * 1.1f;                    // la imagen empieza un poco antes del origen
                float back = quadLen * 0.06f / 4f * 4f * 0.25f;
                SPR[o] = sx - dx * back; SPR[o + 1] = sy - dy * back; SPR[o + 2] = sz - dz * back;
                SPR[o + 3] = dx; SPR[o + 4] = dy; SPR[o + 5] = dz;
                SPR[o + 6] = quadLen;
                SPR[o + 7] = alpha;
                SPR[o + 8] = ((int) (time * HakiVisuals.SPRITE_FRAMES_PER_TICK) + i * 3) & 15;
                SPR[o + 9] = ((cycle + i) & 1) == 0 ? 0f : 1f;
                SPR[o + 10] = widthScale;
                continue;
            }

            float base = HakiVisuals.BOLT_WIDTH * widthScale * wRnd;
            int n = emit(sx, sy, sz, dx, dy, dz, len, HakiVisuals.BOLT_LEVELS, len * HakiVisuals.BOLT_JITTER,
                base, HakiVisuals.BOLT_TIP_WIDTH * widthScale, alpha,
                HakiVisuals.BOLT_FORKS_MAX, HakiVisuals.BOLT_FORK_CHANCE);
            int main = paths - 1 - lastForks;
            if (p < HakiVisuals.BOLT_SHOOT_FRAC * 1.8f) { // cabeza brillante mientras sale disparado
                float[] tp = PTS[main];
                addSpark(tp[(n - 1) * 3], tp[(n - 1) * 3 + 1], tp[(n - 1) * 3 + 2],
                    HakiVisuals.TIP_SPARK_SIZE * widthScale, Math.min(1f, alpha * 1.2f));
            }

            // Chispas blancas que parpadean sobre el rayo (cada medio tick en otro punto)
            if (p > 0.08f && p < 0.8f && sparks < MAX_SPARKS - 2 && FLK.nextFloat() < HakiVisuals.SPARK_CHANCE) {
                for (int q = 0; q < 2; q++) {
                    int k = n - 1 - FLK.nextInt(Math.max(1, n / 2)); // mitad exterior del rayo
                    float[] mp = PTS[main];
                    addSpark(mp[k * 3], mp[k * 3 + 1], mp[k * 3 + 2], (HakiVisuals.SPARK_SIZE_MIN
                        + FLK.nextFloat() * (HakiVisuals.SPARK_SIZE_MAX - HakiVisuals.SPARK_SIZE_MIN)) * widthScale,
                        Math.min(1f, alpha * 1.4f));
                }
            }
        }
    }

    static int lastForks;

    /**
     * Camino principal (desplazamiento del punto medio) mas hasta forksMax ramas. Despues de llamar, el camino
     * principal esta en paths - 1 - lastForks y las ramas a continuacion. Devuelve el numero de puntos del principal.
     */
    static int emit(float sx, float sy, float sz, float dx, float dy, float dz, float len, int levels, float amp,
                    float w0, float w1, float alpha, int forksMax, float forkChance) {
        int main = paths;
        int n = build(main, sx, sy, sz, dx, dy, dz, len, levels, amp, w0, w1, alpha);
        lastForks = 0;
        for (int f = 0; f < forksMax && paths < MAX_PATHS; f++) {
            if (RNG.nextFloat() > forkChance) continue;
            int k0 = 2 + RNG.nextInt(Math.max(1, n - 4));
            float[] mp = PTS[main];
            float tx = mp[(k0 + 1) * 3] - mp[(k0 - 1) * 3], ty = mp[(k0 + 1) * 3 + 1] - mp[(k0 - 1) * 3 + 1],
                tz = mp[(k0 + 1) * 3 + 2] - mp[(k0 - 1) * 3 + 2];
            float tl = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
            if (tl < 1e-4f) continue;
            tx /= tl; ty /= tl; tz /= tl;
            float rx = RNG.nextFloat() - 0.5f, ry = RNG.nextFloat() - 0.5f, rz = RNG.nextFloat() - 0.5f;
            float rl = (float) Math.sqrt(rx * rx + ry * ry + rz * rz) + 1e-4f;
            float fx = tx + HakiVisuals.BOLT_FORK_SPREAD * rx / rl * 2f, fy = ty + HakiVisuals.BOLT_FORK_SPREAD * ry / rl * 2f,
                fz = tz + HakiVisuals.BOLT_FORK_SPREAD * rz / rl * 2f;
            float fl = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
            fx /= fl; fy /= fl; fz /= fl;
            float forkLen = len * (HakiVisuals.BOLT_FORK_LEN_MIN
                + RNG.nextFloat() * (HakiVisuals.BOLT_FORK_LEN_MAX - HakiVisuals.BOLT_FORK_LEN_MIN))
                * (1f - 0.5f * k0 / n);
            build(paths, mp[k0 * 3], mp[k0 * 3 + 1], mp[k0 * 3 + 2], fx, fy, fz, forkLen,
                HakiVisuals.BOLT_FORK_LEVELS, forkLen * HakiVisuals.BOLT_JITTER * 1.4f,
                W[main][k0] * HakiVisuals.BOLT_FORK_WIDTH, 0f, alpha * 0.9f);
            lastForks++;
        }
        return n;
    }

    /**
     * Genera un camino por desplazamiento del punto medio: empieza en (sx, sy, sz) y avanza len en la direccion
     * (dx, dy, dz) con desvios laterales que se reducen en cada nivel. Devuelve el numero de puntos.
     */
    private static int build(int idx, float sx, float sy, float sz, float dx, float dy, float dz, float len,
                             int levels, float amp, float w0, float w1, float alpha) {
        int n = (1 << levels);
        // Dos ejes perpendiculares a la direccion
        float ax, ay, az;
        if (Math.abs(dy) < 0.9f) { ax = -dz; ay = 0; az = dx; } else { ax = 1; ay = 0; az = 0; }
        float al = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        ax /= al; ay /= al; az /= al;
        float bx = dy * az - dz * ay, by = dz * ax - dx * az, bz = dx * ay - dy * ax;

        O1[0] = 0; O2[0] = 0;
        O1[n] = (RNG.nextFloat() - 0.5f) * 2f * amp;
        O2[n] = (RNG.nextFloat() - 0.5f) * 2f * amp;
        float a = amp;
        for (int step = n; step > 1; step >>= 1) {
            for (int i = 0; i < n; i += step) {
                int m = i + step / 2;
                O1[m] = (O1[i] + O1[i + step]) * 0.5f + (RNG.nextFloat() - 0.5f) * 2f * a;
                O2[m] = (O2[i] + O2[i + step]) * 0.5f + (RNG.nextFloat() - 0.5f) * 2f * a;
            }
            a *= HakiVisuals.BOLT_ROUGHNESS;
        }
        float[] pts = PTS[idx], w = W[idx];
        for (int k = 0; k <= n; k++) {
            float t = len * k / n;
            if (k > 0 && k < n) t += (RNG.nextFloat() - 0.5f) * len / n * 0.4f;
            // El parpadeo mueve un poco los puntos intermedios entre una forma y la siguiente
            float wob = k == 0 ? 0f : HakiVisuals.BOLT_SHAPE_FLICKER * amp;
            float j1 = O1[k] + (FLK.nextFloat() - 0.5f) * 2f * wob, j2 = O2[k] + (FLK.nextFloat() - 0.5f) * 2f * wob;
            pts[k * 3]     = sx + dx * t + ax * j1 + bx * j2;
            pts[k * 3 + 1] = sy + dy * t + ay * j1 + by * j2;
            pts[k * 3 + 2] = sz + dz * t + az * j1 + bz * j2;
            // Nucleo grueso en la base que se afina hacia la punta
            float f = 1f - (float) k / n;
            w[k] = w1 + (w0 - w1) * (float) Math.pow(f, 1.3);
        }
        N[idx] = n + 1;
        ALPHA[idx] = alpha;
        U0[idx] = RNG.nextFloat();
        FLAT[idx] = false;
        paths++;
        return n + 1;
    }

    /** Pasada de cintas: widthMul/widthAdd ensanchan (halo) y alphaMul lo atenua. */
    static void drawRibbons(VertexConsumer vc, PoseStack.Pose pose, float widthMul, float widthAdd, float alphaMul) {
        for (int i = 0; i < paths; i++) ribbon(vc, pose, PTS[i], W[i], N[i], FLAT[i], widthMul, widthAdd, ALPHA[i] * alphaMul, U0[i]);
    }

    static void drawSparks(VertexConsumer vc, PoseStack.Pose pose, Vector3f left, Vector3f up) {
        for (int i = 0; i < sparks; i++) {
            int o = i * 5;
            HakiFxRender.billboard(vc, pose, SPARKS[o], SPARKS[o + 1], SPARKS[o + 2], SPARKS[o + 3] * 0.5f,
                left, up, 0xFFFFFF, SPARKS[o + 4]);
        }
    }

    /** Laminas del flipbook de rayos: de frente a la camara (que esta en el origen), con 4x4 cuadros. */
    static void drawSprites(VertexConsumer vc, PoseStack.Pose pose) {
        for (int i = 0; i < sprites; i++) {
            int o = i * 11;
            float x = SPR[o], y = SPR[o + 1], z = SPR[o + 2], dx = SPR[o + 3], dy = SPR[o + 4], dz = SPR[o + 5];
            float len = SPR[o + 6], alpha = SPR[o + 7];
            int frame = (int) SPR[o + 8];
            boolean flip = SPR[o + 9] > 0.5f;
            float hw = len * HakiVisuals.SPRITE_BOLT_WIDTH * SPR[o + 10] * 0.5f;
            float mx = x + dx * len * 0.5f, my = y + dy * len * 0.5f, mz = z + dz * len * 0.5f;
            float px = dy * mz - dz * my, py = dz * mx - dx * mz, pz = dx * my - dy * mx;
            float pl = (float) Math.sqrt(px * px + py * py + pz * pz);
            if (pl < 1e-4f) continue;
            px = px / pl * hw; py = py / pl * hw; pz = pz / pl * hw;
            float ex = x + dx * len, ey = y + dy * len, ez = z + dz * len;
            float u0 = (frame & 3) * 0.25f, v0 = (frame >> 2) * 0.25f, u1 = u0 + 0.25f, v1 = v0 + 0.25f;
            float va = flip ? v1 : v0, vb = flip ? v0 : v1;
            HakiFxRender.vertex(vc, pose, x + px, y + py, z + pz, u0, va, alpha);
            HakiFxRender.vertex(vc, pose, x - px, y - py, z - pz, u0, vb, alpha);
            HakiFxRender.vertex(vc, pose, ex - px, ey - py, ez - pz, u1, vb, alpha);
            HakiFxRender.vertex(vc, pose, ex + px, ey + py, ez + pz, u1, va, alpha);
        }
    }

    static boolean hasBolts() { return paths > 0; }
    static boolean hasSprites() { return sprites > 0; }

    /**
     * Cinta con ancho distinto en cada punto, orientada hacia la camara (que esta en el origen: las coordenadas
     * ya son relativas a ella) o plana si flat. La perpendicular de cada punto promedia sus dos tramos para que
     * no queden huecos.
     */
    private static void ribbon(VertexConsumer vc, PoseStack.Pose pose, float[] p, float[] w, int n, boolean flat,
                               float wMul, float wAdd, float alpha, float u0) {
        float px0 = 0, py0 = 0, pz0 = 0;
        for (int k = 0; k < n; k++) {
            int a = Math.max(0, k - 1), b = Math.min(n - 1, k + 1);
            float tx = p[b * 3] - p[a * 3], ty = p[b * 3 + 1] - p[a * 3 + 1], tz = p[b * 3 + 2] - p[a * 3 + 2];
            float x = p[k * 3], y = p[k * 3 + 1], z = p[k * 3 + 2];
            float px, py, pz;
            if (flat) { px = -tz; py = 0; pz = tx; }
            else { px = ty * z - tz * y; py = tz * x - tx * z; pz = tx * y - ty * x; }
            float pl = (float) Math.sqrt(px * px + py * py + pz * pz);
            if (pl < 1e-6f) { px = px0; py = py0; pz = pz0; } else { px /= pl; py /= pl; pz /= pl; }
            float half = (w[k] * wMul + wAdd) * 0.5f;
            if (k > 0) {
                float u1 = (u0 + (float) (k - 1) / (n - 1)) * 0.5f, u2 = (u0 + (float) k / (n - 1)) * 0.5f;
                float qx = p[(k - 1) * 3], qy = p[(k - 1) * 3 + 1], qz = p[(k - 1) * 3 + 2];
                float hq = (w[k - 1] * wMul + wAdd) * 0.5f;
                HakiFxRender.vertex(vc, pose, qx + px0 * hq, qy + py0 * hq, qz + pz0 * hq, u1, 0f, alpha);
                HakiFxRender.vertex(vc, pose, qx - px0 * hq, qy - py0 * hq, qz - pz0 * hq, u1, 1f, alpha);
                HakiFxRender.vertex(vc, pose, x - px * half, y - py * half, z - pz * half, u2, 1f, alpha);
                HakiFxRender.vertex(vc, pose, x + px * half, y + py * half, z + pz * half, u2, 0f, alpha);
            }
            px0 = px; py0 = py; pz0 = pz;
        }
    }
}
