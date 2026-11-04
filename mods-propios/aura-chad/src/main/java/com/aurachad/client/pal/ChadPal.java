package com.aurachad.client.pal;

import com.aurachad.AuraChadMod;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.enums.PlayState;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Capa propia de aurachad en Player Animation Library. Prioridad 1600: por encima de los emotes (1000) y de las
 * animaciones de gameplay "normales" (1500); otro mod que quiera convivir registra su capa con una prioridad MENOR
 * (p. ej. 1550) para ceder mientras dura el modo chad. Ver README.
 */
final class ChadPal {
    private ChadPal() {}

    static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath(AuraChadMod.MODID, "chad");
    /** Id de la animacion = nombre dentro de assets/aurachad/player_animations/chad.json */
    static final ResourceLocation ANIM = ResourceLocation.fromNamespaceAndPath(AuraChadMod.MODID, "chad");
    static final int PRIORITY = 1600;

    static void register() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, PRIORITY,
            player -> new PlayerAnimationController(player, (controller, state, setter) -> PlayState.STOP));
    }

    static boolean play(AbstractClientPlayer p) {
        var layer = PlayerAnimationAccess.getPlayerAnimationLayer(p, LAYER);
        return layer instanceof PlayerAnimationController c && c.triggerAnimation(ANIM);
    }

    static void stop(AbstractClientPlayer p) {
        if (PlayerAnimationAccess.getPlayerAnimationLayer(p, LAYER) instanceof PlayerAnimationController c) c.stopTriggeredAnimation();
    }
}
