package com.haki.client;

import com.haki.HakiEffects;
import com.haki.HakiMod;
import com.haki.HakiServer;
import com.haki.HakiVisuals;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Efectos de pantalla del jugador local: tinte rojo, vineta y destello. */
@EventBusSubscriber(modid = HakiMod.MODID, value = Dist.CLIENT)
public final class HakiScreenFx {
    private HakiScreenFx() {}

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(HakiMod.MODID, "haki_screen");
    private static final ResourceLocation VIGNETTE_TEX =
        ResourceLocation.fromNamespaceAndPath(HakiMod.MODID, "textures/fx/vignette.png");

    private static float intensity, prevIntensity;
    private static final ResourceLocation STREAKS_TEX =
        ResourceLocation.fromNamespaceAndPath(HakiMod.MODID, "textures/fx/streaks.png");

    private static int flash, prevFlash;
    private static float skyFlash, prevSkyFlash; // destello corto y rojizo de los rayos del cielo (0..1)
    private static int intro, prevIntro; // ticks que quedan de la entrada cinematografica
    private static boolean wasInside;

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused()) return;
        prevIntensity = intensity;
        prevFlash = flash;
        prevSkyFlash = skyFlash;
        skyFlash = Math.max(0f, skyFlash - 1f / HakiVisuals.SKY_FLASH_TICKS);
        prevIntro = intro;
        boolean inside = mc.player != null && mc.level != null && isInside(mc);
        if (flash > 0) flash--;
        if (intro > 0 && --intro == 0) flash = HakiVisuals.FLASH_TICKS; // el destello cierra la entrada
        if (inside && !wasInside) {
            if (HakiVisuals.INTRO_ENABLED && HakiVisuals.INTRO_STRENGTH > 0f) intro = HakiVisuals.INTRO_TICKS;
            else flash = HakiVisuals.FLASH_TICKS;
        }
        wasInside = inside;
        intensity = inside
            ? Math.min(1f, intensity + 1f / HakiVisuals.SCREEN_FADE_IN_TICKS)
            : Math.max(0f, intensity - 1f / HakiVisuals.SCREEN_FADE_OUT_TICKS);
    }

    /** El jugador local es el lanzador, esta en el radio de un lanzador activo o sigue paralizado. */
    private static boolean isInside(Minecraft mc) {
        if (mc.player.hasEffect(HakiEffects.HAKI_KING)) return true;
        double r2 = HakiServer.RADIUS * HakiServer.RADIUS;
        for (var e : HakiClientFx.CASTERS.entrySet()) {
            if (!e.getValue().active) continue;
            if (e.getKey() == mc.player.getId()) return true;
            Entity caster = mc.level.getEntity(e.getKey());
            if (caster != null && caster.distanceToSqr(mc.player) <= r2) return true;
        }
        return false;
    }

    public static float intensity(float pt) {
        return prevIntensity + (intensity - prevIntensity) * pt;
    }

    /** Capa de interfaz registrada encima de CAMERA_OVERLAYS (ver HakiClient). */
    public static void render(GuiGraphics g, DeltaTracker delta) {
        float pt = delta.getGameTimeDeltaPartialTick(false);
        float k = intensity(pt);
        float fl = Math.max(0f, prevFlash + (flash - prevFlash) * pt) / HakiVisuals.FLASH_TICKS;
        float in = introCurve(pt);
        if (k <= 0.001f && fl <= 0.001f && in <= 0.001f) return;
        // suavizado (smoothstep) para que la entrada y salida no sean lineales
        float s = k * k * (3f - 2f * k);
        int w = g.guiWidth(), h = g.guiHeight();

        int tintA = (int) (HakiVisuals.TINT_ALPHA * s * 255f);
        if (tintA > 0) g.fill(0, 0, w, h, tintA << 24 | HakiVisuals.TINT_RGB);

        if (s > 0.001f) {
            RenderSystem.enableBlend();
            g.setColor(1f, 1f, 1f, HakiVisuals.VIGNETTE_ALPHA * s);
            g.blit(VIGNETTE_TEX, 0, 0, 0, 0f, 0f, w, h, w, h);
            g.setColor(1f, 1f, 1f, 1f);
            RenderSystem.disableBlend();
        }

        if (in > 0.001f) {
            // Entrada: casi negro con vetas rojas radiales que se abren desde el centro
            int darkA = (int) (HakiVisuals.INTRO_DARK_ALPHA * in * 255f);
            if (darkA > 0) g.fill(0, 0, w, h, darkA << 24);
            float p = introProgress(pt);
            int size = (int) (Math.max(w, h) * (HakiVisuals.INTRO_STREAK_SCALE + 0.5f * p));
            RenderSystem.enableBlend();
            g.setColor(1f, 1f, 1f, HakiVisuals.INTRO_STREAK_ALPHA * in);
            g.blit(STREAKS_TEX, (w - size) / 2, (h - size) / 2, size, size, 0f, 0f, 256, 256, 256, 256);
            g.setColor(1f, 1f, 1f, 1f);
            RenderSystem.disableBlend();
        }

        float sk = prevSkyFlash + (skyFlash - prevSkyFlash) * pt;
        int skyA = (int) (HakiVisuals.SKY_FLASH_ALPHA * sk * sk * 255f);
        if (skyA > 0) g.fill(0, 0, w, h, skyA << 24 | 0xFFC8C8);
        int flashA = (int) (HakiVisuals.FLASH_ALPHA * fl * 255f);
        if (flashA > 0) g.fill(0, 0, w, h, flashA << 24 | 0xFFFFFF);
    }

    /** 0 al empezar la entrada .. 1 al terminar. */
    private static float introProgress(float pt) {
        if (intro <= 0 && prevIntro <= 0) return 1f;
        float left = Math.max(0f, prevIntro + (intro - prevIntro) * pt);
        return 1f - left / HakiVisuals.INTRO_TICKS;
    }

    /** Fuerza de la entrada: sube rapido, se mantiene y baja al final (0 si no hay entrada). */
    private static float introCurve(float pt) {
        if (intro <= 0 && prevIntro <= 0) return 0f;
        float p = introProgress(pt);
        return HakiVisuals.INTRO_STRENGTH * Math.min(1f, p / 0.25f) * Math.min(1f, (1f - p) / 0.3f);
    }

    /** Pequeno zoom de camara durante la entrada (solo el FOV del mundo, no el de la mano). */
    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        if (!event.usedConfiguredFov() || (intro <= 0 && prevIntro <= 0)) return;
        float p = introProgress((float) event.getPartialTick());
        float zoom = HakiVisuals.INTRO_FOV_ZOOM * HakiVisuals.INTRO_STRENGTH * Mth.sin(p * Mth.PI);
        event.setFOV(event.getFOV() * (1f - zoom));
    }

    /** Temblor suave de camara, escalado por la misma intensidad que el tinte. */
    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        float k = intensity((float) event.getPartialTick());
        if (k <= 0.001f) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float t = (mc.level.getGameTime() + (float) event.getPartialTick()) * HakiVisuals.SHAKE_SPEED;
        float a = HakiVisuals.SHAKE_DEGREES * k;
        // suma de senos a distintas frecuencias: tiembla sin saltos bruscos
        event.setYaw(event.getYaw() + a * (float) (Math.sin(t * 1.3) * 0.6 + Math.sin(t * 3.1) * 0.4));
        event.setPitch(event.getPitch() + a * (float) (Math.sin(t * 1.7 + 1.0) * 0.6 + Math.sin(t * 2.9) * 0.4));
        event.setRoll(event.getRoll() + a * 0.5f * (float) Math.sin(t * 2.3 + 2.0));
    }

    /** Un rayo del cielo cayo a dist bloques de la camara: destello mas fuerte cuanto mas cerca. */
    public static void skyStrike(float dist) {
        float k = 1f - Math.min(1f, dist / HakiVisuals.SKY_FLASH_RANGE);
        if (k > skyFlash) skyFlash = k;
    }

    public static void reset() {
        skyFlash = prevSkyFlash = 0f;
        intensity = prevIntensity = 0f;
        flash = prevFlash = 0;
        intro = prevIntro = 0;
        wasInside = false;
    }
}
