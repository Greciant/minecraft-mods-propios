package com.aurachad.devtest;

import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.enums.PlayState;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

/** Capa PAL de prueba (otro "mod" con prioridad configurable) para comprobar quien gana frente a aurachad:chad. */
final class PalTestLayer {
    static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("aurachad", "testlow");

    static void register(int priority) {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(ID, priority,
            p -> new PlayerAnimationController(p, (c, s, a) -> PlayState.STOP));
    }

    static void play(AbstractClientPlayer p) {
        if (PlayerAnimationAccess.getPlayerAnimationLayer(p, ID) instanceof PlayerAnimationController c) c.triggerAnimation(ID);
    }
}
