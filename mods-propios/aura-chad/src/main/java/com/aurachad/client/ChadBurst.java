package com.aurachad.client;

import com.aurachad.ChadParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

/**
 * Estallido de aura de un golpe de pose, proporcional a su fuerza: explosion radial desde el cuerpo (60 x fuerza), anillo de onda que se
 * abre en el suelo (36 x fuerza) y chorro de chispas hacia arriba (20 x fuerza). Usa level.addParticle, asi que respeta la opcion de particulas.
 */
final class ChadBurst {
    private ChadBurst() {}

    static final int RADIAL = 60, RING = 36, JET = 20;

    /** Devuelve las particulas pedidas al nivel. */
    static int fire(Minecraft mc, Entity e, float strength) {
        var level = mc.level;
        RandomSource r = level.random;
        var type = ChadParticles.BURST.get();
        double cx = e.getX(), cz = e.getZ(), fy = e.getY();
        int radial = Math.round(RADIAL * strength), ring = Math.round(RING * strength), jet = Math.round(JET * strength);

        for (int i = 0; i < radial; i++) {            // explosion esferica desde el torso
            double zc = r.nextDouble() * 2 - 1, phi = r.nextDouble() * Math.PI * 2, rr = Math.sqrt(1 - zc * zc);
            double dx = rr * Math.cos(phi), dy = zc, dz = rr * Math.sin(phi);
            double sp = (0.35 + r.nextDouble() * 0.40) * (0.8 + 0.3 * strength);
            level.addParticle(type, cx + dx * 0.3, fy + 1.0 + dy * 0.5, cz + dz * 0.3, dx * sp, dy * sp * 0.8, dz * sp);
        }
        for (int i = 0; i < ring; i++) {              // anillo de onda: a ras de suelo, hacia afuera
            double a = (i + r.nextDouble()) / ring * Math.PI * 2;
            double sp = (0.42 + r.nextDouble() * 0.12) * (0.85 + 0.2 * strength);
            level.addParticle(type, cx + Math.cos(a) * 0.35, fy + 0.08, cz + Math.sin(a) * 0.35, Math.cos(a) * sp, 0.01, Math.sin(a) * sp);
        }
        for (int i = 0; i < jet; i++) {               // chorro hacia arriba
            double a = r.nextDouble() * Math.PI * 2, rad = r.nextDouble() * 0.35;
            level.addParticle(type, cx + Math.cos(a) * rad, fy + 0.3 + r.nextDouble() * 0.8, cz + Math.sin(a) * rad,
                (r.nextDouble() - 0.5) * 0.08, (0.45 + r.nextDouble() * 0.40) * (0.85 + 0.2 * strength), (r.nextDouble() - 0.5) * 0.08);
        }
        return radial + ring + jet;
    }
}
