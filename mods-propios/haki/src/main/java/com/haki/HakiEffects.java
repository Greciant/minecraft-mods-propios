package com.haki;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class HakiEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
        DeferredRegister.create(Registries.MOB_EFFECT, HakiMod.MODID);

    public static final DeferredHolder<MobEffect, HakiKingEffect> HAKI_KING =
        EFFECTS.register("haki_king", HakiKingEffect::new);
}
