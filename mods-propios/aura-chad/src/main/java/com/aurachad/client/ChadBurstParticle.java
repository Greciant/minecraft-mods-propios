package com.aurachad.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** Chispa del estallido de aura: azul brillante con algunos nucleos casi blancos, 8 a 16 ticks, rapida y con friccion alta; siempre a pleno brillo. */
public class ChadBurstParticle extends TextureSheetParticle {
    private final float size;

    ChadBurstParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, vx, vy, vz);
        pickSprite(sprites);
        this.xd = vx; this.yd = vy; this.zd = vz;
        this.lifetime = 8 + random.nextInt(9);
        this.size = 0.10f + random.nextFloat() * 0.12f;
        this.quadSize = size;
        this.gravity = 0.02f;
        this.friction = 0.80f;
        this.hasPhysics = false;
        if (random.nextFloat() < 0.2f) this.setColor(0.80f, 0.93f, 1f);   // nucleo blanco azulado frio (R < G < B)
        else { float s = 0.8f + random.nextFloat() * 0.2f; this.setColor(0.25f * s, 0.65f * s + 0.1f, 1f); }
    }

    @Override
    public void tick() {
        super.tick();
        float life = (float) age / lifetime;
        this.alpha = 1f - life * life * life;
        this.quadSize = size * (1f - 0.55f * life);
    }

    @Override
    public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }

    @Override
    protected int getLightColor(float partialTick) { return 0xF000F0; }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public Provider(SpriteSet sprites) { this.sprites = sprites; }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double vx, double vy, double vz) {
            return new ChadBurstParticle(level, x, y, z, vx, vy, vz, sprites);
        }
    }
}
