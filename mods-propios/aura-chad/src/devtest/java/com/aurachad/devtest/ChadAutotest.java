package com.aurachad.devtest;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import com.aurachad.AuraChadMod;
import com.aurachad.client.ChadClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import org.lwjgl.glfw.GLFW;

/** Prueba automatica (solo con -Daurachad.autotest=true, tarea runAutotest): activa el modo chad y toma capturas. */
@EventBusSubscriber(modid = AuraChadMod.MODID, value = Dist.CLIENT)
public final class ChadAutotest {
    private static final boolean FRAMES = new java.io.File("aurachad_frames").exists();
    private static int frame;
    private static final boolean ENABLED = Boolean.getBoolean("aurachad.autotest");
    private static final List<String> OUT = new ArrayList<>();
    private static boolean created;
    private static int t = -1;
    private static int start = -1;
    /** Segundos de modo chad en los que se captura. */
    private static final float[] SHOTS = new float[com.aurachad.client.ChadHits.all().size()];
    static { for (var h : com.aurachad.client.ChadHits.all()) SHOTS[h.index()] = h.t() + 0.12f; }   // con el estallido ya en el aire
    private static int next;
    /** Instantes en que se mide la direccion real del antebrazo/pierna doblada y se compara con el objetivo de chad_bend.json. */
    private static final float[] MEAS;
    static {
        var hs = com.aurachad.client.ChadHits.all();
        MEAS = new float[hs.size() * 2];
        for (var h : hs) {   // justo despues del corte y justo antes del siguiente (meseta)
            float next = h.index() + 1 < hs.size() ? hs.get(h.index() + 1).t() : com.aurachad.client.ChadPoses.TOTAL_SECONDS;
            MEAS[h.index() * 2] = h.t() + 0.15f;
            MEAS[h.index() * 2 + 1] = Math.max(h.t() + 0.2f, next - 0.1f);
        }
    }
    private static int nextMeas;
    private static long soundMs = -1;
    /** Prueba con Haki (-Paurachad.haki): "same" = la misma entidad usa chad y haki; "split" = el jugador usa haki y un RemotePlayer usa chad. */
    private static final String HAKI = System.getProperty("aurachad.haki", "");
    private static int subject = -1;
    /** -Daurachad.palTest=<prioridad>: capa PAL de prueba que anima brazos/cabeza/piernas con esa prioridad. */
    private static final String PAL_TEST = System.getProperty("aurachad.palTest", "");
    @EventBusSubscriber(modid = AuraChadMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Setup {
        @SubscribeEvent
        public static void setup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent e) {
            if (!PAL_TEST.isEmpty()) e.enqueueWork(() -> PalTestLayer.register(Integer.parseInt(PAL_TEST)));
        }
    }
    private static final java.util.Map<Integer, String> POSES = new java.util.HashMap<>();
    /** Por jugador, justo despues de dibujarlo (el modelo del renderer se comparte entre jugadores): tipo de modelo EMF y medida de codos. */
    private static final java.util.Map<Integer, String> DESC = new java.util.HashMap<>();
    private static final java.util.Map<Integer, float[][]> MEASURED = new java.util.HashMap<>();
    private static final String[] BONES = {"right_arm", "left_arm", "right_leg", "left_leg"};
    /** Una fila por fotograma dibujado: segundos mostrados, brazo D (xyz), brazo I (xyz), cabeza (xy), cuerpo x, escala del cuerpo, escala del brazo, giro (grados). */
    private static final List<float[]> SAMPLES = new ArrayList<>();
    private static final List<float[]> BURSTS = new ArrayList<>();   // {indice, t, segundos mostrados, particulas}
    private static final java.util.Map<Integer, Integer> PENDING_LIVE = new java.util.HashMap<>();   // tick -> indice del golpe

    @SubscribeEvent
    public static void onRendered(net.neoforged.neoforge.client.event.RenderLivingEvent.Post<?, ?> event) {
        if (!ENABLED || !(event.getEntity() instanceof AbstractClientPlayer p) || !(event.getRenderer().getModel() instanceof PlayerModel<?> m)) return;
        DESC.put(p.getId(), com.aurachad.client.emf.ChadEmfBridge.describe(m));
        float[][] ms = new float[4][];
        for (int i = 0; i < 4; i++) ms[i] = com.aurachad.client.bend.ChadBendBridge.measure(m, BONES[i]);
        MEASURED.put(p.getId(), ms);
        float shown = ChadClient.ageSeconds(p.getId());
        if (shown >= 0f && p.getId() == subject) SAMPLES.add(new float[]{shown, m.rightArm.xRot, m.rightArm.yRot, m.rightArm.zRot, m.leftArm.xRot, m.leftArm.yRot, m.leftArm.zRot,
            m.head.xRot, m.head.yRot, m.body.xRot, m.body.xScale, m.rightArm.xScale, com.aurachad.client.ChadPoses.yawAt(shown)});
        POSES.put(p.getId(), String.format("R(%.2f %.2f %.2f) L(%.2f %.2f %.2f) head(%.2f) body(%.2f) legZ(%.2f %.2f) scale(body %.2f arm %.2f)",
            m.rightArm.xRot, m.rightArm.yRot, m.rightArm.zRot, m.leftArm.xRot, m.leftArm.yRot, m.leftArm.zRot, m.head.xRot, m.body.xRot,
            m.rightLeg.zRot, m.leftLeg.zRot, m.body.xScale, m.rightArm.xScale));
    }

    private static void hakiCast(int id, boolean on) {
        try {
            Class<?> pl = Class.forName("com.haki.HakiStatePayload");
            Object payload = pl.getConstructor(int.class, boolean.class).newInstance(id, on);
            Class.forName("com.haki.client.HakiClientFx").getMethod("onState", pl).invoke(null, payload);
            log("Haki " + (on ? "activo" : "apagado") + " para entidad " + id);
        } catch (ReflectiveOperationException e) {
            log("No se pudo activar Haki: " + e);
        }
    }

    @SubscribeEvent
    public static void onSound(PlaySoundEvent event) {
        if (ENABLED && event.getName().contains("aura_chad")) {
            log("SONIDO reproducido: " + event.getName() + " (ms=" + System.currentTimeMillis() + ")");
            if (soundMs < 0) soundMs = System.currentTimeMillis();
        }
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (!ENABLED) return;
        Minecraft mc = Minecraft.getInstance();
        if (!created && mc.screen instanceof AccessibilityOnboardingScreen) {
            mc.options.onboardAccessibility = false;
            mc.setScreen(new TitleScreen());
            return;
        }
        if (!created && mc.screen instanceof TitleScreen) {
            created = true;
            createWorld(mc);
            return;
        }
        if (mc.player == null || mc.getSingleplayerServer() == null) return;
        t++;
        MinecraftServer server = mc.getSingleplayerServer();
        mc.options.pauseOnLostFocus = false;
        if (mc.screen instanceof PauseScreen) mc.setScreen(null);
        if (t == 1) {
            long win = mc.getWindow().getWindow();
            GLFW.glfwSetWindowAttrib(win, GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE);
            GLFW.glfwRestoreWindow(win);
            GLFW.glfwSetWindowSize(win, 720, 1280);
        }
        if (t == 2) {
            com.aurachad.client.ChadHits.listener = (id, hit, shownSecs, n) -> {
            if (id != subject) return;
            BURSTS.add(new float[]{hit.index(), hit.t(), shownSecs, n});
            PENDING_LIVE.put(t + 2, hit.index());
            log(String.format("BURST %d t=%.2f pose=%s fuerza=%.1f -> mostrado a %.2f s (%+.0f ms), particulas pedidas=%d (esperadas %d)", hit.index(), hit.t(), hit.pose(),
                hit.strength(), shownSecs, (shownSecs - hit.t()) * 1000f, n,
                Math.round(60 * hit.strength()) + Math.round(36 * hit.strength()) + Math.round(20 * hit.strength())));
        };
        }
        if (t == 40) server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            cmd(server, p, "time set 18000");
            cmd(server, p, "tp @s ~ ~ ~ 0 0");
            cmd(server, p, "effect give @s resistance 999 4 true");
        });
        if (t == 60 && "split".equals(HAKI)) {
            var remote = new net.minecraft.client.player.RemotePlayer(mc.level, new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes(new byte[]{7}), "Remoto"));
            remote.setPos(mc.player.getX() + 1.6, mc.player.getY(), mc.player.getZ());
            remote.setYRot(0f); remote.setYHeadRot(0f);
            mc.level.addEntity(remote);
            subject = remote.getId();
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
            hakiCast(mc.player.getId(), true);
            ChadClient.onState(new com.aurachad.ChadStatePayload(subject, true));
            log("Chad en RemotePlayer " + subject + ", Haki en jugador " + mc.player.getId());
        } else if (t == 60) { ChadClient.KEY.setDown(true); log("Tecla pulsada"); }
        if (t == 62) ChadClient.KEY.setDown(false);
        if (t == 56) emfStatus(mc, "ANTES del modo chad");
        if (subject < 0) subject = mc.player.getId();
        if (t >= 62 && start < 0 && ChadClient.ageSeconds(subject) >= 0f) {
            if ("same".equals(HAKI)) hakiCast(mc.player.getId(), true);
            if (!PAL_TEST.isEmpty()) { PalTestLayer.play(mc.player); log("Capa de prueba PAL prioridad " + PAL_TEST + " reproduciendose"); }
            start = t;
            var pl = mc.player;
            mc.level.playLocalSound(pl.getX(), pl.getY(), pl.getZ(), com.aurachad.ChadSounds.AURA_CHAD_OTHERS.get(),
                net.minecraft.sounds.SoundSource.PLAYERS, 0.01f, 1f, false);
            log("Modo chad activo (camara=" + mc.options.getCameraType() + ")");
        }
        if (PENDING_LIVE.containsKey(t)) log("   golpe " + PENDING_LIVE.remove(t) + ": particulas vivas en el motor 2 ticks despues = " + mc.particleEngine.countParticles());
        if (start >= 0) {
            float secs = ChadClient.ageSeconds(subject);
            if (FRAMES && secs >= 0f) Screenshot.grab(mc.gameDirectory, String.format("fr_%04d.png", frame++), mc.getMainRenderTarget(), c -> { });
            if (nextMeas < MEAS.length && secs >= MEAS[nextMeas]) {
                if (nextMeas == 0) emfStatus(mc, "DURANTE el modo chad");
                measureBend(mc, MEAS[nextMeas++], secs);
            }
            if (next < SHOTS.length && secs >= SHOTS[next]) {
                String name = String.format("%02d_%04.1fs", next, SHOTS[next]).replace('.', '_');
                shot(mc, name);
                var r = (net.minecraft.client.renderer.entity.player.PlayerRenderer) mc.getEntityRenderDispatcher().getRenderer(mc.player);
                PlayerModel<AbstractClientPlayer> m = r.getModel();
                log(String.format("t=%.2fs rightArm.xRot=%.2f zRot=%.2f leftArm.zRot=%.2f body.xScale=%.2f", secs,
                    m.rightArm.xRot, m.rightArm.zRot, m.leftArm.zRot, m.body.xScale));
                log(String.format("   pose R(%.2f %.2f %.2f) L(%.2f %.2f %.2f) head(%.2f %.2f) body(%.2f) legs(%.2f %.2f) scale(body %.2f arm %.2f)",
                    m.rightArm.xRot, m.rightArm.yRot, m.rightArm.zRot, m.leftArm.xRot, m.leftArm.yRot, m.leftArm.zRot,
                    m.head.xRot, m.head.yRot, m.body.xRot, m.rightLeg.xRot, m.leftLeg.xRot, m.body.xScale, m.rightArm.xScale));
                if (!HAKI.isEmpty()) {
                    log("   [haki=" + HAKI + "] jugador " + mc.player.getId() + ": " + POSES.get(mc.player.getId()));
                    if (subject != mc.player.getId()) log("   [haki=" + HAKI + "] remoto " + subject + ": " + POSES.get(subject));
                }
                next++;
            }
            if (secs < 0f && next > 0) {
                log("Duracion modo chad (animacion) = " + (System.currentTimeMillis() - soundMs) / 1000.0 + " s desde el sonido; audio = 21.9 s");
                log("Modo chad terminado; camara=" + mc.options.getCameraType());
                if (afterTicks == 0) mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT); // para que se dibuje y se pueda comprobar el modelo
                if (++afterTicks < 30) return;
                emfStatus(mc, "DESPUES del modo chad");
                evaluateCuts();
                write(mc);
                mc.stop();
            }
        }
        if (t > 1500) { log("TIMEOUT"); write(mc); mc.stop(); }
    }

    private static int afterTicks;

    private static void emfStatus(Minecraft mc, String when) {
        log("EMF " + when + ": jugador " + mc.player.getId() + " -> " + DESC.get(mc.player.getId()));
        if (subject != mc.player.getId()) log("EMF " + when + ": chad en remoto " + subject + " -> " + DESC.get(subject));
    }

    private static double maxAngleDeg(float[] a) { double m = 0; for (float v : a) m = Math.max(m, Math.abs(Math.toDegrees(v))); return m; }

    /** Pose esperada del modelo (en el orden de SAMPLES, sin el tiempo) para el golpe i. */
    private static float[] expected(int i, float secs, boolean mixin) {
        var m = com.aurachad.client.ChadHits.all().get(i).model();
        var ra = m.getAsJsonArray("right_arm"); var la = m.getAsJsonArray("left_arm"); var hd = m.getAsJsonArray("head");
        float chest = m.get("chest").getAsFloat(), pump = chest - 1f, flex = mixin ? 0.5f + 0.5f * (float) Math.sin(secs * 9f) : 0f;
        float big = 1f + pump * 0.9f, c = chest + pump * 0.04f * flex;
        return new float[]{ra.get(0).getAsFloat(), ra.get(1).getAsFloat(), ra.get(2).getAsFloat() + pump * 0.15f * flex,
            la.get(0).getAsFloat(), la.get(1).getAsFloat(), la.get(2).getAsFloat() - pump * 0.15f * flex,
            hd.get(0).getAsFloat(), hd.get(1).getAsFloat(), m.get("body").getAsFloat(), c, big, m.get("yaw").getAsFloat()};
    }

    /** Diferencia maxima (grados) entre el fotograma y la pose esperada; las escalas (indices 9,10) se devuelven aparte. */
    private static double[] diff(float[] sample, float[] exp) {
        double ang = 0;
        for (int k = 0; k < 9; k++) ang = Math.max(ang, Math.abs(Math.toDegrees(sample[k + 1] - exp[k])));
        double scale = Math.max(Math.abs(sample[10] - exp[9]), Math.abs(sample[11] - exp[10]));
        double yaw = Math.abs(sample[12] - exp[11]);
        return new double[]{ang, scale, yaw};
    }

    /** Mas cercano a "target" dentro de +-win segundos, o null. */
    private static float[] nearest(float target, float win) {
        float[] best = null;
        for (float[] s : SAMPLES) if (Math.abs(s[0] - target) <= win && (best == null || Math.abs(s[0] - target) < Math.abs(best[0] - target))) best = s;
        return best;
    }

    /** En cada golpe: t-0,03 sigue la pose anterior y t+0,03 ya esta la nueva (menos de 1 grado), y cuando salta de verdad. */
    private static void evaluateCuts() {
        boolean mixin = !com.aurachad.client.pal.ChadPalBridge.drives(Minecraft.getInstance().player);
        var hits = com.aurachad.client.ChadHits.all();
        log("CORTES (" + (mixin ? "Mixin de respaldo" : "PAL") + ", " + SAMPLES.size() + " fotogramas): golpe | t | antes(t-0,03) vs pose anterior | despues(t+0,03) vs pose nueva | salto observado");
        int bad = 0;
        for (var h : hits) {
            int i = h.index();
            if (i == 0) {
                float[] a = nearest(0.03f, 0.04f);
                if (a != null) { double[] d = diff(a, expected(0, a[0], mixin)); log(String.format("CORTE 0 t=0.00 %s: despues(+0.03)=%.2f grados escala=%.3f", h.pose(), d[0], d[1])); if (d[0] >= 1.0 || d[1] >= 0.01) bad++; }
                continue;
            }
            float[] before = nearest(h.t() - 0.03f, 0.025f), after = nearest(h.t() + 0.03f, 0.025f);
            if (before == null || after == null) { log(String.format("CORTE %d t=%.2f %s: SIN FOTOGRAMA cerca de t+-0.03 (antes=%s despues=%s)", i, h.t(), h.pose(), before != null, after != null)); bad++; continue; }
            double[] db = diff(before, expected(i - 1, before[0], mixin)), da = diff(after, expected(i, after[0], mixin));
            // Salto observado: primer fotograma, desde 0,3 s antes, mas cerca de la pose nueva que de la anterior
            float jump = -1f;
            for (float[] s : SAMPLES) {
                if (s[0] < h.t() - 0.3f) continue;
                if (diff(s, expected(i, s[0], mixin))[0] < diff(s, expected(i - 1, s[0], mixin))[0]) { jump = s[0]; break; }
            }
            boolean ok = db[0] < 1.0 && da[0] < 1.0 && db[1] < 0.01 && da[1] < 0.01 && db[2] < 1.0 && da[2] < 1.0;
            if (!ok) bad++;
            log(String.format("CORTE %d t=%.2f %s [%s]: antes(%.3f s)=%.2f grados escala=%.3f giro=%.1f | despues(%.3f s)=%.2f grados escala=%.3f giro=%.1f | salto en %.3f s (%+.0f ms)",
                i, h.t(), h.pose(), ok ? "OK" : "FALLA", before[0], db[0], db[1], db[2], after[0], da[0], da[1], da[2], jump, (jump - h.t()) * 1000f));
        }
        int okBursts = 0;
        for (var h : hits) {
            int n = 0; float shown = -1f;
            for (float[] b : BURSTS) if ((int) b[0] == h.index()) { n++; shown = b[2]; }
            boolean ok = n == 1 && shown >= h.t() && shown - h.t() <= 0.1f;
            if (ok) okBursts++;
            log(String.format("ESTALLIDO %d t=%.2f %s: lanzados=%d, a %.2f s (%+.0f ms) [%s]", h.index(), h.t(), h.pose(), n, shown, (shown - h.t()) * 1000f, ok ? "OK" : "FALLA"));
        }
        log(String.format("RESUMEN: cortes con falla=%d de %d; estallidos en su tiempo=%d de %d (total lanzados=%d)", bad, hits.size(), okBursts, hits.size(), BURSTS.size()));
    }

    private static void measureBend(Minecraft mc, float key, float secs) {
        float[][] snap = MEASURED.get(subject);
        for (int i = 0; i < 4; i++) {
            String bone = BONES[i];
            float[] v = snap == null ? null : snap[i];
            if (v == null) { log(String.format("BEND t=%.1f %s sin bend (%s)", secs, bone, DESC.get(subject))); continue; }
            boolean right = bone.startsWith("right");
            float out = right ? v[0] : -v[0];
            String tgtS = "";
            float[] tg = com.aurachad.client.bend.ChadBendTrack.targetAt(bone, secs);
            if (tg != null) {
                double n = Math.sqrt(tg[0] * tg[0] + tg[1] * tg[1] + tg[2] * tg[2]);
                double dot = (out * tg[0] + v[1] * tg[1] + v[2] * tg[2]) / n;
                tgtS = String.format(" objetivo(afuera %.2f arriba %.2f frente %.2f) error=%.1f grados", tg[0] / n, tg[1] / n, tg[2] / n,
                    Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, dot)))));
            }
            log(String.format("BEND t=%.1f %s antebrazo(afuera %.2f arriba %.2f frente %.2f) codo=%.0f grados%s", secs, bone, out, v[1], v[2], v[6], tgtS));
        }
    }

    private static void createWorld(Minecraft mc) {
        String name = "aurachad_autotest_" + System.currentTimeMillis();
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        LevelSettings settings = new LevelSettings(name, GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
            rules, WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(12345L, false, false),
            reg -> reg.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                .value().createWorldDimensions(), mc.screen);
        log("Creando mundo plano " + name);
    }

    private static void cmd(MinecraftServer server, ServerPlayer p, String c) {
        server.getCommands().performPrefixedCommand(p.createCommandSourceStack().withPermission(4).withSuppressedOutput(), c);
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, "aurachad_" + name + ".png", mc.getMainRenderTarget(), c -> { });
        log("Captura " + name + " (" + mc.getMainRenderTarget().width + "x" + mc.getMainRenderTarget().height + ")");
    }

    private static synchronized void log(String s) {
        String line = "[aurachad-autotest] " + s;
        OUT.add(line);
        System.out.println(line);
    }

    private static synchronized void write(Minecraft mc) {
        try {
            Files.write(new File(mc.gameDirectory, "aurachad_autotest.txt").toPath(), OUT, StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
