package com.aurachad;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ChadParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, AuraChadMod.MODID);
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SPARK = PARTICLES.register("spark", () -> new SimpleParticleType(false));
    /** Estallido de aura en cada golpe de pose: vida corta, rapida y con friccion alta. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BURST = PARTICLES.register("burst", () -> new SimpleParticleType(false));
}
