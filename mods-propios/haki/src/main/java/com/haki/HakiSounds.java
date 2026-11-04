package com.haki;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class HakiSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
        DeferredRegister.create(Registries.SOUND_EVENT, HakiMod.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> HAKI_KING = SOUNDS.register("haki_king",
        () -> SoundEvent.createVariableRangeEvent(
            ResourceLocation.fromNamespaceAndPath(HakiMod.MODID, "haki_king")));
}
