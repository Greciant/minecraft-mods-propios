package com.aurachad.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** Chispa azul brillante que sube y se apaga; siempre a pleno brillo. */
public class ChadSparkParticle extends TextureSheetParticle {
    ChadSparkParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, vx, vy, vz);
        pickSprite(sprites);
        this.xd = vx; this.yd = vy; this.zd = vz;
        this.lifetime = 24 + random.nextInt(24);
        this.quadSize = 0.05f + random.nextFloat() * 0.07f;
        this.gravity = -0.01f;
        this.friction = 0.96f;
        this.hasPhysics = false;
        float s = 0.75f + random.nextFloat() * 0.25f;
        this.setColor(0.25f * s, 0.65f * s + 0.1f, 1f);
    }

    @Override
    public void tick() {
        super.tick();
        float life = (float) age / lifetime;
        this.alpha = 1f - life * life;
        this.xd += (random.nextFloat() - 0.5f) * 0.004;
        this.zd += (random.nextFloat() - 0.5f) * 0.004;
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
            return new ChadSparkParticle(level, x, y, z, vx, vy, vz, sprites);
        }
    }
}
