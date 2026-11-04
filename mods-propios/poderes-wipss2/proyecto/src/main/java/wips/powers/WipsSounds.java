package wips.powers;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class WipsSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, WipsPowers.MOD_ID);

    /** Pisadas del Scaler gigante (4 variantes cortadas del mp3 que paso el usuario). */
    public static final DeferredHolder<SoundEvent, SoundEvent> GIANT_STEP = SOUNDS.register("scaler.giant_step",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(WipsPowers.MOD_ID, "scaler.giant_step")));

    /** Carga del Mass Leap (mp3 que paso el usuario). Se corta al saltar. */
    public static final DeferredHolder<SoundEvent, SoundEvent> MASS_LEAP_CHARGE = SOUNDS.register("scaler.mass_leap_charge",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(WipsPowers.MOD_ID, "scaler.mass_leap_charge")));

    private WipsSounds() {
    }
}
