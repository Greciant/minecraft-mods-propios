package com.haki.client.pal;

import com.haki.HakiMod;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.animation.layered.modifier.AbstractFadeModifier;
import com.zigythebird.playeranimcore.easing.EasingType;
import com.zigythebird.playeranimcore.enums.PlayState;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Capa propia del Haki en Player Animation Library. Prioridad 1550: por encima de los emotes (1000) y debajo de la
 * capa aurachad:chad (1600), que cuando esta activa pisa todos los ejes que anima. Unica clase del mod que importa PAL.
 */
final class HakiPal {
    private HakiPal() {}

    static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath(HakiMod.MODID, "cast");
    /** Id de la animacion = nombre dentro de assets/haki/player_animations/cast.json */
    static final ResourceLocation ANIM = ResourceLocation.fromNamespaceAndPath(HakiMod.MODID, "cast");
    /** Animacion vacia (no mueve ningun hueso) a la que se funde la pose al soltar; dura FADE_OUT_TICKS (cast_end.json). */
    static final ResourceLocation ANIM_END = ResourceLocation.fromNamespaceAndPath(HakiMod.MODID, "cast_end");
    static final int PRIORITY = 1550;
    static final int FADE_IN_TICKS = 6, FADE_OUT_TICKS = 8;

    static void register() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, PRIORITY,
            player -> new PlayerAnimationController(player, (controller, state, setter) -> PlayState.STOP));
    }

    static boolean play(AbstractClientPlayer p) {
        return PlayerAnimationAccess.getPlayerAnimationLayer(p, LAYER) instanceof PlayerAnimationController c
            && c.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(FADE_IN_TICKS, EasingType.EASE_IN_OUT_SINE), ANIM, true);
    }

    static void stop(AbstractClientPlayer p) {
        if (!(PlayerAnimationAccess.getPlayerAnimationLayer(p, LAYER) instanceof PlayerAnimationController c)) return;
        // PAL 1.1.6 no sabe "fundir a nada": con null no hacia nada y la animacion en bucle seguia (brazos abiertos).
        // standardFadeIn funde desde la pose actual (la captura PAL) hacia cast_end, que no mueve ningun hueso;
        // cuando cast_end termina, el manejador devuelve STOP y la capa se apaga sola.
        boolean faded = false;
        try { faded = c.isActive() && c.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(FADE_OUT_TICKS, EasingType.EASE_IN_OUT_SINE), ANIM_END); }
        catch (RuntimeException ignored) { }
        if (!faded) c.stopTriggeredAnimation();
    }

    /** Corte inmediato, sin fundido (ultimo recurso si algo falla). */
    static void forceStop(AbstractClientPlayer p) {
        if (PlayerAnimationAccess.getPlayerAnimationLayer(p, LAYER) instanceof PlayerAnimationController c) c.stopTriggeredAnimation();
    }

    static boolean active(AbstractClientPlayer p) {
        return PlayerAnimationAccess.getPlayerAnimationLayer(p, LAYER) instanceof PlayerAnimationController c && c.isActive();
    }
}
