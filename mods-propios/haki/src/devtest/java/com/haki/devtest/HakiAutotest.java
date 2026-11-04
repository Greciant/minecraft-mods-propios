package com.haki.devtest;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.haki.HakiClient;
import com.haki.HakiEffects;
import com.haki.HakiMod;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Wolf;
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

/**
 * Prueba automatica (solo con -Dhaki.autotest=true, tarea runAutotest): crea un mundo plano,
 * invoca mobs, simula mantener G, toma capturas y anota mediciones en run/haki_autotest.txt.
 */
@EventBusSubscriber(modid = HakiMod.MODID, value = Dist.CLIENT)
public final class HakiAutotest {
    private static final boolean ENABLED = Boolean.getBoolean("haki.autotest");
    /** HAKI_SEQ=1: solo una secuencia de capturas (una cada 2 ticks, F5 de frente) para armar un GIF. */
    private static final boolean SEQ = "1".equals(System.getenv("HAKI_SEQ"));
    /** HAKI_FPS=1: mide el tiempo de cuadro sin Haki y con Haki (F5 de frente, sin limite de FPS ni vsync). */
    private static final boolean FPS = "1".equals(System.getenv("HAKI_FPS"));
    private static boolean recording;
    private static long lastFrame;
    private static final List<Double> FRAMES = new ArrayList<>();
    private static final List<String> OUT = new ArrayList<>();
    private static boolean created;
    private static int t = -1;
    private static UUID airHusk, groundHusk, pig, wolf;

    @SubscribeEvent
    public static void onSound(PlaySoundEvent event) {
        if (ENABLED && event.getName().contains("haki_king")) log("SONIDO reproducido: " + event.getName());
    }

    @SubscribeEvent
    public static void onFrame(net.neoforged.neoforge.client.event.RenderFrameEvent.Post event) {
        long now = System.nanoTime();
        if (recording && lastFrame != 0) FRAMES.add((now - lastFrame) / 1e6);
        lastFrame = now;
    }

    private static void report(String label) {
        if (FRAMES.isEmpty()) { log("FPS " + label + ": sin datos"); return; }
        double sum = 0;
        for (double d : FRAMES) sum += d;
        List<Double> sorted = new ArrayList<>(FRAMES);
        java.util.Collections.sort(sorted);
        double avg = sum / FRAMES.size(), p95 = sorted.get((int) (sorted.size() * 0.95));
        log(String.format("FPS %s: cuadros=%d tiempo medio=%.3f ms (%.1f fps) p95=%.3f ms", label, FRAMES.size(), avg, 1000.0 / avg, p95));
        FRAMES.clear();
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
        // La ventana no tiene el foco: evitar el menu de pausa (soltaria la G y congelaria el juego)
        mc.options.pauseOnLostFocus = false;
        if (mc.screen instanceof PauseScreen) mc.setScreen(null);

        if (FPS) {
            if (t == 1) {
                long win = mc.getWindow().getWindow();
                GLFW.glfwSetWindowAttrib(win, GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE);
                GLFW.glfwRestoreWindow(win);
                GLFW.glfwSetWindowSize(win, 1280, 720);
                mc.options.framerateLimit().set(260);
                mc.options.enableVsync().set(false);
            }
            if (t == 40) server.execute(() -> setup(server));
            if (t == 52) mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            if (t == 70) { FRAMES.clear(); recording = true; }
            if (t == 170) { recording = false; report("SIN Haki (antes)"); HakiClient.HAKI_KEY.setDown(true); }
            if (t == 195) { FRAMES.clear(); recording = true; }
            if (t == 290) { recording = false; report("CON Haki (estilo " + com.haki.HakiVisuals.STYLE + ")"); HakiClient.HAKI_KEY.setDown(false); }
            if (t == 335) { FRAMES.clear(); recording = true; }
            if (t == 435) { recording = false; report("SIN Haki (despues)"); }
            if (t == 440) { write(mc); mc.stop(); }
            return;
        }
        if (SEQ) {
            if (t == 1) {
                long win = mc.getWindow().getWindow();
                GLFW.glfwSetWindowAttrib(win, GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE);
                GLFW.glfwRestoreWindow(win);
                GLFW.glfwSetWindowSize(win, 1280, 720);
            }
            if (t == 40) server.execute(() -> setup(server));
            if (t == 52) mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            if (t == 60) HakiClient.HAKI_KEY.setDown(true);
            if (t >= 58 && t <= 140 && t % 2 == 0) shot(mc, String.format("seq_%03d", t));
            if (t == 150) { HakiClient.HAKI_KEY.setDown(false); write(mc); mc.stop(); }
            return;
        }
        switch (t) {
            case 1 -> {
                // Si la ventana esta minimizada el framebuffer mide 1x1 y las capturas salen vacias
                long win = mc.getWindow().getWindow();
                GLFW.glfwSetWindowAttrib(win, GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE);
                GLFW.glfwRestoreWindow(win);
                GLFW.glfwSetWindowSize(win, 1280, 720);
            }
            case 40 -> server.execute(() -> setup(server));
            case 58 -> server.execute(() -> airHusk = spawn(server, EntityType.HUSK, 0, 7, 5));
            case 60 -> { HakiClient.HAKI_KEY.setDown(true); log("G pulsada"); }
            case 63 -> shot(mc, "1_inicio_destello");
            case 66 -> shot(mc, "1a_entrada_oscura");
            case 70 -> shot(mc, "1b_onda_de_choque");
            case 75 -> shot(mc, "2_primera_persona");
            case 85 -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            case 92 -> shot(mc, "3_tercera_persona_atras");
            case 96 -> shot(mc, "3b_tercera_persona_atras");
            case 100 -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            case 107 -> shot(mc, "4_tercera_persona_frente");
            case 102, 108 -> poseLog(mc);
            case 111 -> shot(mc, "4b_tercera_persona_frente");
            case 112 -> { if ("1".equals(System.getenv("HAKI_CHAD"))) chad(mc.player.getId(), true); }
            case 115 -> shot(mc, "4c_tercera_persona_frente");
            case 118 -> mc.options.setCameraType(CameraType.FIRST_PERSON);
            case 124 -> shot(mc, "2b_primera_persona");
            case 128 -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            case 140 -> { HakiClient.HAKI_KEY.setDown(false); log("G soltada (mantenida 80 ticks)"); }
            case 146 -> poseLog(mc);
            case 150 -> { HakiClient.HAKI_KEY.setDown(true); log("G pulsada otra vez (deberia estar en enfriamiento)"); }
            case 153 -> shot(mc, "6_enfriamiento");
            case 155 -> HakiClient.HAKI_KEY.setDown(false);
            case 158 -> { poseLog(mc); shot(mc, "5_tras_soltar"); }
            case 310 -> { HakiClient.HAKI_KEY.setDown(true); log("G pulsada: segundo uso tras el enfriamiento"); }
            case 322 -> poseLog(mc);
            case 330 -> { HakiClient.HAKI_KEY.setDown(false); log("G soltada (segundo uso)"); }
            case 348 -> { poseLog(mc); shot(mc, "7_segundo_uso_tras_soltar"); }
            case 360 -> { brazos(); write(mc); mc.stop(); }
            default -> { }
        }
        if (t >= 58 && t <= 350 && t % 10 == 0) {
            int now = t;
            server.execute(() -> measure(server, now));
        }
    }

    private static final java.util.Map<Integer, double[]> POSES = new java.util.HashMap<>();

    /** Veredicto: tras soltar la pose abierta (zRot ~1.05) debe haberse recogido y la capa de PAL apagado. */
    private static void brazos() {
        String fallo = null;
        for (int tt : new int[] {158, 348}) {
            double[] v = POSES.get(tt);
            if (v == null) { fallo = "sin dato de pose en t=" + tt; break; }
            if (v[0] != 0) { fallo = "t=" + tt + " la capa de PAL sigue activa"; break; }
            if (v[1] >= 0.35 || v[2] >= 0.35) { fallo = String.format("t=%d brazos abiertos (D=%.2f I=%.2f)", tt, v[1], v[2]); break; }
        }
        double[] u = POSES.get(322);
        if (fallo == null && com.haki.client.pal.HakiPalBridge.available() && (u == null || u[0] == 0))
            fallo = "t=322 la capa de PAL no esta activa durante el segundo uso";
        log(fallo == null ? "PRUEBA BRAZOS: OK" : "PRUEBA BRAZOS: FALLO (" + fallo + ")");
    }

    /** Rotaciones reales del modelo del jugador (para comprobar si la pose llega al render, con o sin EMF). */
    private static void poseLog(Minecraft mc) {
        try {
            var r = (net.minecraft.client.renderer.entity.player.PlayerRenderer) mc.getEntityRenderDispatcher().getRenderer(mc.player);
            var m = r.getModel();
            boolean capa = com.haki.client.pal.HakiPalBridge.layerActive(mc.player);
            POSES.put(t, new double[] {capa ? 1 : 0, Math.abs(m.rightArm.zRot), Math.abs(m.leftArm.zRot)});
            log(String.format("POSE t=%d capaPal=%s palAnima=%s chadActivo=%s brazoD zRot=%.2f xRot=%.2f | brazoI zRot=%.2f | cabeza xRot=%.2f | cuerpo xRot=%.2f | piernaD zRot=%.2f",
                t, capa, com.haki.client.pal.HakiPalBridge.drives(mc.player), com.haki.client.HakiCompat.chadActive(mc.player),
                m.rightArm.zRot, m.rightArm.xRot, m.leftArm.zRot, m.head.xRot, m.body.xRot, m.rightLeg.zRot));
        } catch (Throwable e) {
            log("POSE no disponible: " + e);
        }
    }

    private static void chad(int id, boolean on) {
        try {
            Class<?> pl = Class.forName("com.aurachad.ChadStatePayload");
            Object payload = pl.getConstructor(int.class, boolean.class).newInstance(id, on);
            Class.forName("com.aurachad.client.ChadClient").getMethod("onState", pl).invoke(null, payload);
            log("Chad " + (on ? "activo" : "apagado") + " para entidad " + id);
        } catch (ReflectiveOperationException e) {
            log("No se pudo activar chad: " + e);
        }
    }

    private static void createWorld(Minecraft mc) {
        String name = "haki_autotest_" + System.currentTimeMillis();
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        LevelSettings settings = new LevelSettings(name, GameType.SURVIVAL, false, Difficulty.NORMAL, true,
            rules, WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(12345L, false, false),
            reg -> reg.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                .value().createWorldDimensions(), mc.screen);
        log("Creando mundo plano " + name);
    }

    private static void setup(MinecraftServer server) {
        ServerPlayer p = server.getPlayerList().getPlayers().get(0);
        cmd(server, p, "time set " + System.getenv().getOrDefault("HAKI_TIME", "1000"));
        cmd(server, p, "tp @s ~ ~ ~ 0 10");
        cmd(server, p, "effect give @s resistance 999 4 true");
        cmd(server, p, "effect give @s regeneration 999 4 true");
        groundHusk = spawn(server, EntityType.HUSK, 2, 0, 6);
        pig = spawn(server, EntityType.PIG, -3, 0, 5);
        UUID wolfId = spawn(server, EntityType.WOLF, -2, 0, 3);
        Entity we = p.serverLevel().getEntity(wolfId);
        if (we instanceof Wolf wo) wo.tame(p);
        wolf = wolfId;
        log("Preparado: husk en el suelo, cerdo, lobo domesticado (mascota del lanzador). Hambre=" + p.getFoodData().getFoodLevel());
    }

    private static UUID spawn(MinecraftServer server, EntityType<? extends Mob> type, double dx, double dy, double dz) {
        ServerPlayer p = server.getPlayerList().getPlayers().get(0);
        ServerLevel level = p.serverLevel();
        Mob m = type.create(level);
        if (m == null) return null;
        m.moveTo(p.getX() + dx, p.getY() + dy, p.getZ() + dz, 180f, 0f);
        m.finalizeSpawn(level, level.getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.COMMAND, null);
        m.setPersistenceRequired();
        level.addFreshEntity(m);
        return m.getUUID();
    }

    private static void measure(MinecraftServer server, int now) {
        ServerPlayer p = server.getPlayerList().getPlayers().get(0);
        ServerLevel level = p.serverLevel();
        StringBuilder sb = new StringBuilder("t=" + now + " hambre=" + p.getFoodData().getFoodLevel()
            + " sat=" + String.format("%.1f", p.getFoodData().getSaturationLevel()));
        describe(sb, "huskAire", level.getEntity(airHusk), p);
        describe(sb, "huskSuelo", level.getEntity(groundHusk), p);
        describe(sb, "cerdo", level.getEntity(pig), p);
        describe(sb, "lobo", level.getEntity(wolf), p);
        log(sb.toString());
    }

    private static void describe(StringBuilder sb, String name, Entity e, ServerPlayer p) {
        if (!(e instanceof LivingEntity le)) { sb.append(" | ").append(name).append("=?"); return; }
        sb.append(" | ").append(name).append(String.format(" y=%.2f vida=%.1f", le.getY() - p.getY(), le.getHealth()));
        if (le instanceof Mob m) sb.append(m.isNoAi() ? " NoAI" : " IA");
        if (le.hasEffect(HakiEffects.HAKI_KING)) sb.append(" haki=").append(le.getEffect(HakiEffects.HAKI_KING).getDuration());
    }

    private static void cmd(MinecraftServer server, ServerPlayer p, String c) {
        server.getCommands().performPrefixedCommand(p.createCommandSourceStack().withPermission(4).withSuppressedOutput(), c);
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, "haki_" + name + ".png", mc.getMainRenderTarget(), c -> { });
        log("Captura " + name + " (" + mc.getMainRenderTarget().width + "x" + mc.getMainRenderTarget().height + ")");
    }

    private static synchronized void log(String s) {
        String line = "[haki-autotest] " + s;
        OUT.add(line);
        System.out.println(line);
    }

    private static synchronized void write(Minecraft mc) {
        try {
            Files.write(new File(mc.gameDirectory, "haki_autotest.txt").toPath(), OUT, StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
