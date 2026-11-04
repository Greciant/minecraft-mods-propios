package com.aurachad.client;

import com.mojang.blaze3d.vertex.VertexConsumer;

/** Reenvia cada cuadrilatero con el orden de vertices invertido: con culling dibuja solo las caras traseras (casco invertido). */
final class FlippedConsumer implements VertexConsumer {
    private final VertexConsumer out;
    private final float[][] v = new float[4][12];
    private int n;

    FlippedConsumer(VertexConsumer out) { this.out = out; }

    @Override public VertexConsumer addVertex(float x, float y, float z) {
        if (n == 4) flush();
        float[] a = v[n++];
        a[0] = x; a[1] = y; a[2] = z;
        return this;
    }
    @Override public VertexConsumer setColor(int r, int g, int b, int a) { float[] c = v[n - 1]; c[3] = r; c[4] = g; c[5] = b; c[6] = a; return this; }
    @Override public VertexConsumer setUv(float u, float w) { float[] c = v[n - 1]; c[7] = u; c[8] = w; return this; }
    @Override public VertexConsumer setUv1(int u, int w) { return this; }
    @Override public VertexConsumer setUv2(int u, int w) { return this; }
    @Override public VertexConsumer setNormal(float x, float y, float z) { float[] c = v[n - 1]; c[9] = x; c[10] = y; c[11] = z; return this; }

    void flush() {
        for (int i = n - 1; i >= 0; i--) {
            float[] a = v[i];
            out.addVertex(a[0], a[1], a[2]).setColor((int) a[3], (int) a[4], (int) a[5], (int) a[6]).setUv(a[7], a[8])
                .setUv1(0, 10).setUv2(0xF0, 0xF0).setNormal(a[9], a[10], a[11]);
        }
        n = 0;
    }
}
