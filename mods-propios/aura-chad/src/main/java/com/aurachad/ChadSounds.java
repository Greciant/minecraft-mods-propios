package com.aurachad;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ChadSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, AuraChadMod.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> AURA_CHAD = SOUNDS.register("aura_chad",
        () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(AuraChadMod.MODID, "aura_chad")));

    /** Version mono para la reproduccion posicional (los estereo no se atenuan con la distancia). */
    public static final DeferredHolder<SoundEvent, SoundEvent> AURA_CHAD_OTHERS = SOUNDS.register("aura_chad_others",
        () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(AuraChadMod.MODID, "aura_chad_others")));
}
