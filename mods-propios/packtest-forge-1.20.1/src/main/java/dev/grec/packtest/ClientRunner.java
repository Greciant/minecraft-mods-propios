package dev.grec.packtest;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.ChatFormatting;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Maquina de estados del cliente: crea el mundo, espera a que cargue de verdad y ejecuta el guion. */
final class ClientRunner {
    private enum Phase { MENU, JOINING, SETUP, READY_WAIT, SCRIPT, ENDING, DONE }

    private static final int PLATFORM_Y = 219;
    private static final int READY_RADIUS = Math.max(4, Integer.getInteger("packtest.readyRadius", 4));
    private static final int READY_EXTRA_TICKS = Math.max(600, Integer.getInteger("packtest.readyTicks", 600));
    private static final int FPS_MIN = 5;
    private static final int FPS_STREAK = 100;
    private static final int DEFAULT_GAP = Math.max(0, Integer.getInteger("packtest.gap", 40));
    private static final long MAX_TICKS = 20L * 60 * Math.max(5, Integer.getInteger("packtest.maxMinutes", 90));
    private static final TextColor RED = TextColor.fromLegacyFormat(ChatFormatting.RED);

    private record Step(int n, String line, String op, String[] args) {}
    private record CmdResult(int value, List<String> messages, boolean failed, Throwable error) {}
    private record PlaceResult(boolean ok, boolean retry, String msg, BoundingBox box) {}
    /** Resultado de una orden que se calcula en el hilo del servidor (findblock, clearabove...). */
    private record SrvResult(String verdict, Vec3 found) {}

    /** Centro de la plataforma: "use" relativo y "useat" exigen estar cerca. */
    private static final Vec3 PLATFORM_CENTER = new Vec3(0.5, 220, 0.5);
    private static final double MAX_PLATFORM_DIST = 5.0;
    private static final double MAX_USE_REACH = 6.0;
    private static final int SAMPLE_W = 96, SAMPLE_H = 54;

    private final Path scriptPath;
    private final LogCapture capture;
    private Phase phase = Phase.MENU;
    private long ticks = 0;
    private long phaseTick = 0;
    private int titleSeen = 0;
    private boolean pauseOnLostFocusOrig = true;

    // setup
    private int setupStage = 0;
    private CompletableFuture<CmdResult> pending;
    private long nextTry = 0;
    private long tpTick = -1;
    // ready
    private boolean chunksLogged, ticksLogged, fpsLogged;
    private int fpsStreak = 0;
    // script
    private List<Step> steps = List.of();
    private int stepIdx = 0;
    private Step cur;
    private long stepStart;
    private long gapUntil = 0;
    private int stepAux;
    private int executed = 0;
    private int failures = 0;
    private int shotCounter = 0;
    private final List<String> failedSteps = new ArrayList<>();
    private long endTick;
    private int gap = DEFAULT_GAP;
    private boolean scriptPause = false;
    private long retryAt = 0;
    // placestruct / frame
    private CompletableFuture<PlaceResult> placePending;
    private BoundingBox lastBox;
    private String lastBoxName;
    // shot -> muestra reducida de pixeles (para expect_diff)
    private final Map<String, int[]> shotSamples = new HashMap<>();
    // watch: tipo de entidad -> ticks en los que se vio alguna en el cliente
    private final Map<ResourceLocation, Integer> watched = new LinkedHashMap<>();
    // findblock / findentity -> punto para framefound
    private Vec3 lastFound;
    private CompletableFuture<SrvResult> srvPending;
    // pantallas: el cursor se aparta a la esquina (0,0) al abrir cada pantalla nueva (sin tooltips)
    private boolean parkCursor = true;
    private Screen lastScreen;
    // avisos (logros, recetas): se borran cada tick salvo "toasts on"
    private boolean allowToasts = false;
    private static Field mouseX, mouseY;

    private ClientRunner(Path scriptPath, LogCapture capture) {
        this.scriptPath = scriptPath;
        this.capture = capture;
    }

    static void start(String script) {
        LogCapture capture = LogCapture.install();
        ClientRunner r = new ClientRunner(Path.of(script), capture);
        PackTest.LOG.info("[packtest] activo, guion={} (existe={})", script, Files.isRegularFile(r.scriptPath));
        MinecraftForge.EVENT_BUS.addListener(r::onTick);
    }

    // ------------------------------------------------------------------ util

    private static void log(String fmt, Object... args) {
        PackTest.LOG.info("[packtest] " + fmt, args);
    }

    private void setPhase(Phase p) {
        phase = p;
        phaseTick = ticks;
    }

    /** Ejecuta un comando en el servidor integrado como el jugador, capturando su salida. */
    private CompletableFuture<CmdResult> runServer(Minecraft mc, String cmd) {
        CompletableFuture<CmdResult> f = new CompletableFuture<>();
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) {
            f.complete(new CmdResult(0, List.of(), true, new IllegalStateException("sin servidor integrado o sin jugador")));
            return f;
        }
        UUID id = mc.player.getUUID();
        server.execute(() -> {
            List<String> msgs = new ArrayList<>();
            boolean[] failed = {false};
            CommandSource sink = new CommandSource() {
                @Override public void sendSystemMessage(Component c) {
                    synchronized (msgs) { msgs.add(c.getString()); }
                    if (RED.equals(c.getStyle().getColor())) failed[0] = true;
                    for (Component s : c.getSiblings()) if (RED.equals(s.getStyle().getColor())) failed[0] = true;
                }
                @Override public boolean acceptsSuccess() { return true; }
                @Override public boolean acceptsFailure() { return true; }
                @Override public boolean shouldInformAdmins() { return false; }
            };
            try {
                ServerPlayer sp = server.getPlayerList().getPlayer(id);
                CommandSourceStack src = (sp != null ? sp.createCommandSourceStack() : server.createCommandSourceStack()).withSource(sink);
                int v = server.getCommands().performPrefixedCommand(src, cmd);
                f.complete(new CmdResult(v, msgs, failed[0] || (v == 0 && msgs.isEmpty()), null));
            } catch (Throwable t) {
                f.complete(new CmdResult(0, msgs, true, t));
            }
        });
        return f;
    }

    private static String describe(CmdResult r) {
        StringBuilder sb = new StringBuilder("resultado=").append(r.value());
        if (!r.messages().isEmpty()) sb.append(" msg=\"").append(String.join(" / ", r.messages()).replace('\n', ' ')).append('"');
        if (r.error() != null) sb.append(" error=").append(r.error());
        return sb.toString();
    }

    private static int effectiveRender(Minecraft mc) {
        return Math.max(2, mc.options.getEffectiveRenderDistance());
    }

    /** Devuelve cuantos chunks faltan en un radio (circular) alrededor del jugador. */
    private static int missingChunks(Minecraft mc, int radius) {
        ClientLevel level = mc.level;
        LocalPlayer p = mc.player;
        if (level == null || p == null) return Integer.MAX_VALUE;
        ChunkPos c = p.chunkPosition();
        int missing = 0;
        for (int dx = -radius; dx <= radius; dx++)
            for (int dz = -radius; dz <= radius; dz++)
                if (dx * dx + dz * dz <= radius * radius && !level.hasChunk(c.x + dx, c.z + dz)) missing++;
        return missing;
    }

    // ------------------------------------------------------------------ tick

    private void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || phase == Phase.DONE) return;
        Minecraft mc = Minecraft.getInstance();
        ticks++;
        try {
            if (ticks % 400 == 0) capture.attach();
            if (ticks > MAX_TICKS && phase != Phase.ENDING) {
                log("FAIL tiempo maximo agotado ({} ticks) en fase {}", MAX_TICKS, phase);
                failures++;
                failedSteps.add("timeout global en fase " + phase);
                beginEnding();
            }
            if (phase.ordinal() >= Phase.SETUP.ordinal() && phase != Phase.ENDING && !scriptPause && mc.screen instanceof PauseScreen) {
                log("cierro el menu de pausa (pausaria el servidor integrado)");
                mc.setScreen(null);
            }
            switch (phase) {
                case MENU -> tickMenu(mc);
                case JOINING -> tickJoining(mc);
                case SETUP -> tickSetup(mc);
                case READY_WAIT -> tickReady(mc);
                case SCRIPT -> tickScript(mc);
                case ENDING -> tickEnding(mc);
                default -> {}
            }
        } catch (Throwable t) {
            PackTest.LOG.error("[packtest] excepcion en la fase " + phase, t);
            failures++;
            failedSteps.add("excepcion en fase " + phase + ": " + t);
            if (phase == Phase.SCRIPT && cur != null) {
                finishStep("FAIL excepcion " + t);
            } else if (phase != Phase.ENDING) {
                beginEnding();
            } else {
                phase = Phase.DONE;
                mc.stop();
            }
        }
    }

    private void tickMenu(Minecraft mc) {
        if (mc.getOverlay() == null && mc.screen instanceof AccessibilityOnboardingScreen) {
            log("cierro la pantalla de accesibilidad del primer arranque");
            mc.screen.onClose();
            return;
        }
        if (ticks % 600 == 0) log("en el menu, pantalla actual: {}", mc.screen == null ? "ninguna" : mc.screen.getClass().getName());
        if (!(mc.screen instanceof TitleScreen) || mc.getOverlay() != null) return;
        if (++titleSeen < 40) return; // dejar que la pantalla de titulo se asiente
        pauseOnLostFocusOrig = mc.options.pauseOnLostFocus;
        mc.options.pauseOnLostFocus = false;
        String name = "packtest-" + System.currentTimeMillis();
        String preset = System.getProperty("packtest.preset", "minecraft:normal");
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        rules.getRule(GameRules.RULE_MOBGRIEFING).set(false, null);
        LevelSettings settings = new LevelSettings(name, GameType.CREATIVE, false, Difficulty.EASY, true, rules,
                WorldDataConfiguration.DEFAULT);
        ResourceKey<WorldPreset> key = ResourceKey.create(Registries.WORLD_PRESET, new ResourceLocation(preset));
        log("creando mundo {} (preset {}, creativo, facil, trucos, sin ciclo dia/clima, sin mobGriefing)", name, preset);
        setPhase(Phase.JOINING);
        mc.createWorldOpenFlows().createFreshLevel(name, settings, WorldOptions.defaultWithRandomSeed(),
                ra -> ra.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(key).value().createWorldDimensions());
    }

    private void tickJoining(Minecraft mc) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (mc.player == null || mc.level == null || server == null) {
            if ((ticks - phaseTick) % 1200 == 0) log("esperando a entrar al mundo... ({} s)", (ticks - phaseTick) / 20);
            if (ticks - phaseTick > 20L * 60 * 30) throw new IllegalStateException("el mundo no cargo en 30 minutos");
            return;
        }
        if (server.getTickCount() < 40 || mc.screen instanceof ReceivingLevelScreen || mc.getOverlay() != null) return;
        log("jugador en el mundo, servidor integrado en tick {} (tras {} s)", server.getTickCount(), (ticks - phaseTick) / 20);
        capture.startCountingErrors();
        setupStage = 0;
        setPhase(Phase.SETUP);
    }

    private static final String[] SETUP_CMDS = {
            "gamemode creative",
            "difficulty easy",
            // sin spawns naturales: patrullas/tiendas de illagers caian en la plataforma y tiraban al jugador
            "gamerule doMobSpawning false",
            "gamerule doPatrolSpawning false",
            "gamerule doTraderSpawning false",
            "gamerule doInsomnia false",
            "gamerule disableRaids true",
            "forceload add -32 -32 32 32",
            "tp @s 0.5 220 0.5 0 20",
            "effect give @s minecraft:resistance infinite 4 true",
            "effect give @s minecraft:night_vision infinite 0 true",
            "effect give @s minecraft:slow_falling infinite 0 true",
            "attribute @s minecraft:generic.knockback_resistance base set 1",
            "time set noon",
            "weather clear",
    };

    /** Despues de la plataforma: anillo de barreras (paredes invisibles en x/z=+-17) y aire encima. */
    private static final String[] PLATFORM_CMDS = {
            "fill -17 " + (PLATFORM_Y + 1) + " -17 17 " + (PLATFORM_Y + 4) + " 17 minecraft:barrier hollow",
            "fill -16 " + (PLATFORM_Y + 1) + " -16 16 " + (PLATFORM_Y + 12) + " 16 minecraft:air",
            "kill @e[type=!player,distance=..48]",
    };

    private void tickSetup(Minecraft mc) {
        int nSetup = SETUP_CMDS.length;
        int platformStage = nSetup;
        int lastStage = nSetup + 1 + PLATFORM_CMDS.length; // tp final
        if (pending != null) {
            if (!pending.isDone()) return;
            CmdResult r = pending.join();
            pending = null;
            if (setupStage < nSetup) {
                log("preparacion: /{} -> {} {}", SETUP_CMDS[setupStage], r.failed() ? "FAIL" : "ok", describe(r));
                if (SETUP_CMDS[setupStage].startsWith("tp ")) {
                    tpTick = ticks;
                    LocalPlayer p = mc.player;
                    if (p != null) { p.getAbilities().flying = true; p.onUpdateAbilities(); }
                }
                setupStage++;
                nextTry = ticks + 10;
            } else if (setupStage == platformStage) { // plataforma
                if (r.failed()) {
                    if (ticks - tpTick > 20L * 60 * 5) {
                        log("FAIL no pude poner la plataforma en 5 min: {}", describe(r));
                        failures++;
                        failedSteps.add("plataforma");
                        setupStage = lastStage;
                    } else {
                        if ((ticks - tpTick) % 200 < 40) log("plataforma aun no (chunks del servidor sin cargar?): {}", describe(r));
                        nextTry = ticks + 40;
                    }
                } else {
                    log("plataforma de piedra 33x33 colocada en y={} ({})", PLATFORM_Y, describe(r));
                    setupStage++;
                    nextTry = ticks + 10;
                }
            } else if (setupStage < lastStage) { // barreras, aire, limpieza
                log("preparacion: /{} -> {}", PLATFORM_CMDS[setupStage - platformStage - 1], describe(r));
                setupStage++;
                nextTry = ticks + 10;
            } else { // tp final
                LocalPlayer p = mc.player;
                if (p != null) { p.getAbilities().flying = false; p.onUpdateAbilities(); }
                log("jugador sobre la plataforma; esperando carga (radio {} chunks, {} ticks, FPS>{} durante {} ticks)",
                        READY_RADIUS, READY_EXTRA_TICKS, FPS_MIN, FPS_STREAK);
                setPhase(Phase.READY_WAIT);
            }
            return;
        }
        if (ticks < nextTry) return;
        if (setupStage < nSetup) pending = runServer(mc, SETUP_CMDS[setupStage]);
        else if (setupStage == platformStage)
            pending = runServer(mc, "fill -16 " + PLATFORM_Y + " -16 16 " + PLATFORM_Y + " 16 minecraft:stone");
        else if (setupStage < lastStage) pending = runServer(mc, PLATFORM_CMDS[setupStage - platformStage - 1]);
        else pending = runServer(mc, "tp @s 0.5 220 0.5 0 20");
    }

    private void tickReady(Minecraft mc) {
        if (mc.player == null) throw new IllegalStateException("jugador desaparecio mientras esperaba la carga");
        long waited = ticks - tpTick;
        int radius = Math.min(READY_RADIUS, effectiveRender(mc) - 1);
        int missing = missingChunks(mc, radius);
        if (!chunksLogged && missing == 0) {
            chunksLogged = true;
            log("chunks cargados en el cliente (radio {}) tras {} s", radius, waited / 20);
        } else if (!chunksLogged && waited > 20L * 60 * 10) {
            chunksLogged = true;
            log("AVISO: tras 10 min aun faltan {} chunks en radio {}; sigo igual", missing, radius);
        } else if (!chunksLogged && waited % 600 == 0) {
            log("esperando chunks: faltan {} (radio {}), {} s", missing, radius, waited / 20);
        }
        if (!ticksLogged && waited >= READY_EXTRA_TICKS) {
            ticksLogged = true;
            log("pasaron {} ticks desde el tp", READY_EXTRA_TICKS);
        }
        int fps = mc.getFps();
        fpsStreak = fps > FPS_MIN ? fpsStreak + 1 : 0;
        if (!fpsLogged && fpsStreak >= FPS_STREAK) {
            fpsLogged = true;
            log("FPS estables (>{} durante {} ticks, ahora {})", FPS_MIN, FPS_STREAK, fps);
        } else if (!fpsLogged && waited > 20L * 60 * 10) {
            fpsLogged = true;
            log("AVISO: FPS bajos durante 10 min (ahora {}); sigo igual", fps);
        }
        if (chunksLogged && ticksLogged && fpsLogged) {
            loadScript();
            capture.startRecordingLines();
            log("mundo listo tras {} s; empieza el guion ({} pasos)", waited / 20, steps.size());
            stepIdx = 0;
            cur = null;
            gapUntil = ticks + 20;
            setPhase(Phase.SCRIPT);
        }
    }

    private void loadScript() {
        List<Step> out = new ArrayList<>();
        try {
            List<String> lines = Files.readAllLines(scriptPath, StandardCharsets.UTF_8);
            int n = 0;
            for (String raw : lines) {
                String line = raw.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                n++;
                String[] parts = line.split("\\s+", 2);
                String op = parts[0].toLowerCase(Locale.ROOT);
                String rest = parts.length > 1 ? parts[1].strip() : "";
                out.add(new Step(n, line, op, rest.isEmpty() ? new String[0] : rest.split("\\s+")));
            }
        } catch (IOException e) {
            log("FAIL no se pudo leer el guion {}: {}", scriptPath, e.toString());
            failures++;
            failedSteps.add("leer guion: " + e);
        }
        steps = out;
    }

    // ------------------------------------------------------------------ guion

    private void tickScript(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            log("FAIL el jugador salio del mundo durante el guion");
            failures++;
            failedSteps.add("jugador fuera del mundo");
            beginEnding();
            return;
        }
        if (!watched.isEmpty()) scanWatched(mc);
        if (!allowToasts) mc.getToasts().clear();
        if (mc.screen != lastScreen) {
            lastScreen = mc.screen;
            if (lastScreen != null && parkCursor) parkCursor(mc, 0, 0);
        }
        if (cur == null) {
            if (ticks < gapUntil) return;
            if (stepIdx >= steps.size()) { beginEnding(); return; }
            cur = steps.get(stepIdx++);
            stepStart = ticks;
            stepAux = 0;
            pending = null;
            String res = beginStep(mc, cur);
            if (res != null) finishStep(res);
            return;
        }
        String res = pollStep(mc, cur);
        if (res != null) finishStep(res);
    }

    private void finishStep(String result) {
        executed++;
        boolean ok = result.startsWith("ok");
        if (!ok) {
            failures++;
            failedSteps.add(cur.n() + " " + cur.line() + " -> " + result);
        }
        log("step {} {} -> {}", cur.n(), cur.line(), result);
        cur = null;
        pending = null;
        gapUntil = ticks + gap;
    }

    private static String join(String[] a) {
        return String.join(" ", a);
    }

    private static int intArg(String[] a, int i) {
        return Integer.parseInt(a[i]);
    }

    /** Arranca un paso. Devuelve el resultado si termina ya, o null si sigue en curso. */
    private String beginStep(Minecraft mc, Step s) {
        LocalPlayer p = mc.player;
        String[] a = s.args();
        try {
            switch (s.op()) {
                case "wait": {
                    stepAux = intArg(a, 0);
                    return stepAux <= 0 ? "ok" : null;
                }
                case "cmd":
                case "cmdtry": {
                    if (a.length == 0) return "FAIL falta el comando";
                    String c = join(a);
                    if (c.startsWith("/")) c = c.substring(1);
                    pending = runServer(mc, c);
                    return null;
                }
                case "chatcmd": {
                    String c = join(a);
                    if (c.startsWith("/")) c = c.substring(1);
                    p.connection.sendCommand(c);
                    return "ok enviado";
                }
                case "shot": {
                    String name = (a.length == 0 ? "captura" : join(a)).replaceAll("[^A-Za-z0-9._-]", "_");
                    String file = String.format(Locale.ROOT, "packtest-%02d-%s.png", ++shotCounter, name);
                    Screenshot.grab(mc.gameDirectory, file, mc.getMainRenderTarget(),
                            c -> PackTest.LOG.info("[packtest] captura: {}", c.getString()));
                    try (NativeImage img = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                        shotSamples.put(name, sample(img));
                    } catch (Throwable t) {
                        log("  no pude muestrear la captura {}: {}", name, t.toString());
                    }
                    return "ok screenshots/" + file + " (camara " + mc.options.getCameraType() + ", jugador en " + fmt(p.position()) + ")";
                }
                case "look": {
                    float yaw = Float.parseFloat(a[0]);
                    float pitch = Float.parseFloat(a[1]);
                    p.setYRot(yaw);
                    p.setXRot(pitch);
                    p.yRotO = yaw;
                    p.xRotO = pitch;
                    p.setYHeadRot(yaw);
                    p.yBodyRot = yaw;
                    return "ok";
                }
                case "tp": {
                    pending = runServer(mc, "tp @s " + a[0] + " " + a[1] + " " + a[2]);
                    return null;
                }
                case "tpbiome": {
                    // Fase 0: locate biome; fase 1 (en pollStep): spreadplayers en ese x/z, que deja
                    // al jugador sobre la superficie (no en el aire ni enterrado).
                    if (a.length == 0) return "FAIL uso: tpbiome <bioma>";
                    stepAux = 0;
                    pending = runServer(mc, "locate biome " + a[0]);
                    return null;
                }
                case "inventory": {
                    mc.setScreen(new InventoryScreen(p));
                    return "ok";
                }
                case "gap": {
                    gap = Math.max(0, intArg(a, 0));
                    return "ok pausa entre pasos = " + gap + " ticks";
                }
                case "pause": {
                    scriptPause = true;
                    mc.pauseGame(false);
                    return "ok " + (mc.screen == null ? "sin pantalla" : mc.screen.getClass().getSimpleName());
                }
                case "cmdretry": {
                    if (a.length < 2) return "FAIL uso: cmdretry <ticks> <comando>";
                    stepAux = intArg(a, 0);
                    String c = String.join(" ", java.util.Arrays.copyOfRange(a, 1, a.length));
                    if (c.startsWith("/")) c = c.substring(1);
                    pending = runServer(mc, c);
                    return null;
                }
                case "close": {
                    scriptPause = false;
                    if (mc.screen == null) return "ok (no habia pantalla)";
                    String name = mc.screen.getClass().getSimpleName();
                    mc.screen.onClose();
                    if (mc.screen != null) mc.setScreen(null);
                    return "ok cerrada " + name;
                }
                case "use":
                case "useat": {
                    boolean abs = s.op().equals("useat");
                    Vec3 pp = p.position();
                    BlockPos pos;
                    if (abs) {
                        pos = new BlockPos(intArg(a, 0), intArg(a, 1), intArg(a, 2));
                    } else {
                        double dPlat = pp.distanceTo(PLATFORM_CENTER);
                        if (dPlat > MAX_PLATFORM_DIST)
                            return "FAIL jugador fuera de la plataforma en " + fmt(pp) + " (a " + fmt1(dPlat)
                                    + " bloques de 0.5 220 0.5); no hago clic relativo";
                        pos = p.blockPosition().offset(intArg(a, 0), intArg(a, 1), intArg(a, 2));
                    }
                    double reach = p.getEyePosition().distanceTo(Vec3.atCenterOf(pos));
                    if (reach > MAX_USE_REACH)
                        return "FAIL bloque " + pos.toShortString() + " a " + fmt1(reach) + " bloques del jugador (en " + fmt(pp) + ")";
                    String what = BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(pos).getBlock()).toString();
                    if (mc.level.getBlockState(pos).isAir())
                        return "FAIL en " + pos.toShortString() + " hay " + what + " (jugador en " + fmt(pp) + ")";
                    if (a.length > 3 && !new ResourceLocation(a[3]).toString().equals(what))
                        return "FAIL se esperaba " + a[3] + " en " + pos.toShortString() + " y hay " + what;
                    BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false);
                    InteractionResult r = mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, hit);
                    if (r.shouldSwing()) p.swing(InteractionHand.MAIN_HAND);
                    return (r.consumesAction() ? "ok " : "FAIL ") + r + " en " + what + " " + pos.toShortString()
                            + " (jugador en " + fmt(pp) + ")";
                }
                case "useitem": {
                    InteractionResult r = mc.gameMode.useItem(p, InteractionHand.MAIN_HAND);
                    if (r.shouldSwing()) p.swing(InteractionHand.MAIN_HAND);
                    stepAux = a.length > 0 ? intArg(a, 0) : 0;
                    String item = p.getMainHandItem().getItem().builtInRegistryHolder().key().location().toString();
                    if (r == InteractionResult.FAIL) return "FAIL " + r + " con " + item;
                    if (stepAux > 0) {
                        mc.options.keyUse.setDown(true);
                        log("  useitem {} con {}: mantengo {} ticks", r, item, stepAux);
                        return null;
                    }
                    return "ok " + r + " con " + item;
                }
                case "interact": {
                    Entity target = null;
                    Vec3 hitPos = null;
                    if (mc.hitResult instanceof EntityHitResult eh) { target = eh.getEntity(); hitPos = eh.getLocation(); }
                    if (target != null && a.length > 0 && !new ResourceLocation(a[0]).equals(target.getType().builtInRegistryHolder().key().location())) target = null;
                    if (target == null && a.length > 0) {
                        ResourceLocation id = new ResourceLocation(a[0]);
                        double best = Double.MAX_VALUE;
                        for (Entity e : mc.level.entitiesForRendering()) {
                            if (e == p || !id.equals(e.getType().builtInRegistryHolder().key().location())) continue;
                            double d = e.distanceToSqr(p);
                            if (d < best) { best = d; target = e; }
                        }
                        if (target != null) hitPos = target.getBoundingBox().getCenter();
                    }
                    if (target == null) return "FAIL no hay entidad en la mira" + (a.length > 0 ? " ni cerca de tipo " + a[0] : "");
                    InteractionResult r = mc.gameMode.interactAt(p, target, new EntityHitResult(target, hitPos), InteractionHand.MAIN_HAND);
                    if (!r.consumesAction()) r = mc.gameMode.interact(p, target, InteractionHand.MAIN_HAND);
                    if (r.shouldSwing()) p.swing(InteractionHand.MAIN_HAND);
                    String what = target.getType().builtInRegistryHolder().key().location() + " a " + String.format(Locale.ROOT, "%.1f", Math.sqrt(target.distanceToSqr(p))) + " bloques";
                    return (r.consumesAction() ? "ok " : "FAIL ") + r + " con " + what;
                }
                case "attack": {
                    HitResult hr = mc.hitResult;
                    String what;
                    if (hr instanceof EntityHitResult eh) {
                        mc.gameMode.attack(p, eh.getEntity());
                        what = "entidad " + eh.getEntity().getType().builtInRegistryHolder().key().location();
                    } else if (hr instanceof BlockHitResult bh && hr.getType() == HitResult.Type.BLOCK
                            && !mc.level.getBlockState(bh.getBlockPos()).isAir()) {
                        mc.gameMode.startDestroyBlock(bh.getBlockPos(), bh.getDirection());
                        what = "bloque " + bh.getBlockPos().toShortString();
                    } else {
                        what = "aire";
                    }
                    p.swing(InteractionHand.MAIN_HAND);
                    return "ok golpe a " + what;
                }
                case "hotbar": {
                    int slot = intArg(a, 0);
                    if (slot < 0 || slot > 8) return "FAIL ranura fuera de 0-8";
                    p.getInventory().selected = slot;
                    return "ok " + p.getInventory().getItem(slot).getItem().builtInRegistryHolder().key().location();
                }
                case "press": {
                    String k = a.length == 0 ? "use" : a[0].toLowerCase(Locale.ROOT);
                    if (k.equals("use")) mc.options.keyUse.setDown(true);
                    else if (k.equals("attack")) mc.options.keyAttack.setDown(true);
                    else return "FAIL tecla desconocida (use|attack)";
                    return "ok mantengo " + k;
                }
                case "release": {
                    mc.options.keyUse.setDown(false);
                    mc.options.keyAttack.setDown(false);
                    return "ok";
                }
                case "view": {
                    String v = a.length == 0 ? "first" : a[0].toLowerCase(Locale.ROOT);
                    CameraType t = switch (v) {
                        case "back", "third", "tercera" -> CameraType.THIRD_PERSON_BACK;
                        case "front", "frente" -> CameraType.THIRD_PERSON_FRONT;
                        default -> CameraType.FIRST_PERSON;
                    };
                    mc.options.setCameraType(t);
                    return "ok " + t;
                }
                case "hud": {
                    mc.options.hideGui = a.length > 0 && a[0].equalsIgnoreCase("off");
                    return "ok";
                }
                case "echo": {
                    log("  {}", join(a));
                    return "ok";
                }
                case "waitload": {
                    int r = Math.max(0, intArg(a, 0));
                    int max = effectiveRender(mc) - 1;
                    if (r > max) {
                        log("  waitload {} > distancia de render-1 ({}); uso {}", r, max, max);
                        r = max;
                    }
                    stepAux = r;
                    return null;
                }
                case "expect_log": {
                    if (a.length == 0) return "FAIL falta la regex";
                    Pattern pat;
                    try {
                        pat = Pattern.compile(join(a));
                    } catch (PatternSyntaxException e) {
                        return "FAIL regex invalida: " + e.getDescription();
                    }
                    return capture.anyLineMatches(pat) ? "ok" : "FAIL ninguna linea del log coincide";
                }
                case "expect_nolog": {
                    if (a.length == 0) return "FAIL falta la regex";
                    Pattern pat;
                    try {
                        pat = Pattern.compile(join(a));
                    } catch (PatternSyntaxException e) {
                        return "FAIL regex invalida: " + e.getDescription();
                    }
                    if (capture.anyLineMatches(pat)) return "FAIL una linea del log del guion coincide";
                    Path latest = mc.gameDirectory.toPath().resolve("logs").resolve("latest.log");
                    try {
                        if (!Files.isRegularFile(latest)) return "FAIL no existe logs/latest.log";
                        // Minecraft/mods pueden escribir bytes no UTF-8 en Windows. Decodificar
                        // con reemplazo evita ocultar errores por una excepción de charset.
                        String contents = new String(Files.readAllBytes(latest), StandardCharsets.UTF_8);
                        for (String l : contents.split("\\R", -1)) {
                            if (l.contains("[packtest]")) continue;
                            if (pat.matcher(l).find()) return "FAIL en logs/latest.log: " + (l.length() > 200 ? l.substring(0, 200) + "..." : l);
                        }
                    } catch (IOException | java.io.UncheckedIOException e) {
                        return "FAIL no pude leer logs/latest.log: " + e;
                    }
                    return "ok ninguna linea coincide";
                }
                case "expect_diff": {
                    if (a.length < 2) return "FAIL uso: expect_diff <shotA> <shotB> [porcentaje minimo, 2]";
                    String na = a[0].replaceAll("[^A-Za-z0-9._-]", "_"), nb = a[1].replaceAll("[^A-Za-z0-9._-]", "_");
                    double min = a.length > 2 ? Double.parseDouble(a[2]) : 2.0;
                    int[] sa = shotSamples.get(na), sb = shotSamples.get(nb);
                    if (sa == null || sb == null) return "FAIL falta la muestra de " + (sa == null ? na : nb);
                    double pct = diffPercent(sa, sb);
                    return (pct >= min ? "ok " : "FAIL ") + String.format(Locale.ROOT, "%.1f%% de pixeles distintos (minimo %.1f%%)", pct, min);
                }
                case "watch": {
                    if (a.length == 0) return "FAIL falta el tipo de entidad";
                    for (String t : a) watched.put(new ResourceLocation(t), 0);
                    scanWatched(mc);
                    return "ok vigilo " + join(a);
                }
                case "expect_seen": {
                    if (a.length == 0) return "FAIL falta el tipo de entidad";
                    ResourceLocation id = new ResourceLocation(a[0]);
                    Integer n = watched.get(id);
                    if (n == null) return "FAIL " + id + " no estaba vigilado (falta 'watch')";
                    watched.remove(id);
                    return n > 0 ? "ok " + id + " visto en " + n + " ticks" : "FAIL no se vio ninguna entidad " + id;
                }
                case "expect_effect": {
                    if (a.length == 0) return "FAIL falta el efecto";
                    ResourceLocation id = new ResourceLocation(a[0]);
                    for (MobEffectInstance e : p.getActiveEffects()) {
                        if (id.equals(BuiltInRegistries.MOB_EFFECT.getKey(e.getEffect())))
                            return "ok " + id + " nivel " + (e.getAmplifier() + 1) + ", " + e.getDuration() + " ticks";
                    }
                    return "FAIL el jugador no tiene " + id;
                }
                case "setfield": {
                    if (a.length < 3) return "FAIL uso: setfield <clase> <ruta> <valor>";
                    return setField(a[0], a[1], a[2]);
                }
                case "placestruct": {
                    if (a.length < 4) return "FAIL uso: placestruct <ticks> <estructura> <x> <z>";
                    stepAux = intArg(a, 0);
                    placePending = placeServer(mc, new ResourceLocation(a[1]), intArg(a, 2), intArg(a, 3));
                    return null;
                }
                case "frame": {
                    if (lastBox == null) return "FAIL no hay caja de estructura (usar placestruct antes)";
                    boolean auto = a[0].equalsIgnoreCase("auto");
                    double dist = auto ? 0 : Double.parseDouble(a[0]);
                    double pitch = a.length > 1 ? Double.parseDouble(a[1]) : 25;
                    double yaw = a.length > 2 ? Double.parseDouble(a[2]) : 45;
                    double hfrac = a.length > 3 ? Double.parseDouble(a[3]) : 0.5;
                    BoundingBox b = lastBox;
                    double cx = (b.minX() + b.maxX() + 1) / 2.0, cz = (b.minZ() + b.maxZ() + 1) / 2.0;
                    double cy = b.minY() + (b.maxY() + 1 - b.minY()) * hfrac;
                    int span = Math.max(b.getXSpan(), Math.max(b.getYSpan(), b.getZSpan()));
                    double d = auto ? Math.max(20, span * 1.3) : dist;
                    String c = lookAtCmd(new Vec3(cx, cy, cz), d, pitch, yaw);
                    log("  frame {}: caja {} (tam {}x{}x{}), distancia {}, {}", lastBoxName, b,
                            b.getXSpan(), b.getYSpan(), b.getZSpan(), fmt1(d), c);
                    pending = runServer(mc, c);
                    return null;
                }
                case "framefound": {
                    if (lastFound == null) return "FAIL no hay punto (usar findblock o findentity antes)";
                    double d = Double.parseDouble(a[0]);
                    double pitch = a.length > 1 ? Double.parseDouble(a[1]) : 25;
                    double yaw = a.length > 2 ? Double.parseDouble(a[2]) : 45;
                    String c = lookAtCmd(lastFound, d, pitch, yaw);
                    log("  framefound: punto {}, distancia {}, {}", fmt(lastFound), fmt1(d), c);
                    pending = runServer(mc, c);
                    return null;
                }
                case "tpbox": {
                    if (lastBox == null) return "FAIL no hay caja de estructura (usar placestruct antes)";
                    Vec3 c = boxCenter(lastBox);
                    String cmd = String.format(Locale.ROOT, "tp @s %.2f %.2f %.2f", c.x + Double.parseDouble(a[0]),
                            c.y + Double.parseDouble(a[1]), c.z + Double.parseDouble(a[2]));
                    if (a.length >= 5) cmd += " " + a[3] + " " + a[4];
                    log("  tpbox: centro de {} = {}; {}", lastBoxName, fmt(c), cmd);
                    pending = runServer(mc, cmd);
                    return null;
                }
                case "forceloadbox": {
                    if (lastBox == null) return "FAIL no hay caja de estructura (usar placestruct antes)";
                    String mode = a.length > 0 && a[0].equalsIgnoreCase("remove") ? "remove" : "add";
                    BoundingBox b = lastBox;
                    String cmd = "forceload " + mode + " " + b.minX() + " " + b.minZ() + " " + b.maxX() + " " + b.maxZ();
                    log("  forceloadbox: {}", cmd);
                    pending = runServer(mc, cmd);
                    return null;
                }
                case "findblock": {
                    if (a.length == 0) return "FAIL uso: findblock <regex de id de bloque>";
                    if (lastBox == null) return "FAIL no hay caja de estructura (usar placestruct antes)";
                    Pattern pat = Pattern.compile(join(a));
                    BoundingBox box = lastBox;
                    srvPending = onServer(mc, level -> findBlock(level, box, pat));
                    return null;
                }
                case "findentity": {
                    if (a.length == 0) return "FAIL uso: findentity <tipo>";
                    ResourceLocation id = new ResourceLocation(a[0]);
                    BoundingBox box = lastBox;
                    Vec3 from = box != null ? boxCenter(box) : p.position();
                    srvPending = onServer(mc, level -> findEntity(level, id, from, box));
                    return null;
                }
                case "clearabove": {
                    if (lastBox == null) return "FAIL no hay caja de estructura (usar placestruct antes)";
                    int margin = a.length > 0 ? intArg(a, 0) : 3;
                    int height = a.length > 1 ? intArg(a, 1) : 30;
                    BoundingBox box = lastBox;
                    srvPending = onServer(mc, level -> clearAbove(level, box, margin, height));
                    return null;
                }
                case "expect_block": {
                    if (a.length < 4) return "FAIL uso: expect_block <x> <y> <z> <regex de id>";
                    BlockPos pos = new BlockPos(intArg(a, 0), intArg(a, 1), intArg(a, 2));
                    Pattern pat = Pattern.compile(String.join(" ", java.util.Arrays.copyOfRange(a, 3, a.length)));
                    srvPending = onServer(mc, level -> {
                        if (!level.hasChunkAt(pos)) return new SrvResult("FAIL chunk de " + pos.toShortString() + " sin cargar en el servidor", null);
                        String id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString();
                        return new SrvResult((pat.matcher(id).matches() ? "ok " : "FAIL ") + "en " + pos.toShortString() + " hay " + id, null);
                    });
                    return null;
                }
                case "expect_same": {
                    if (a.length < 2) return "FAIL uso: expect_same <shotA> <shotB> [porcentaje maximo, 30]";
                    String na = a[0].replaceAll("[^A-Za-z0-9._-]", "_"), nb = a[1].replaceAll("[^A-Za-z0-9._-]", "_");
                    double max = a.length > 2 ? Double.parseDouble(a[2]) : 30.0;
                    int[] sa = shotSamples.get(na), sb = shotSamples.get(nb);
                    if (sa == null || sb == null) return "FAIL falta la muestra de " + (sa == null ? na : nb);
                    double pct = diffPercent(sa, sb);
                    return (pct <= max ? "ok " : "FAIL ") + String.format(Locale.ROOT, "%.1f%% de pixeles distintos (maximo %.1f%%)", pct, max);
                }
                case "fly": {
                    boolean on = a.length == 0 || a[0].equalsIgnoreCase("on");
                    if (on && !p.getAbilities().mayfly) return "FAIL el jugador no puede volar (no esta en creativo)";
                    p.getAbilities().flying = on;
                    p.onUpdateAbilities();
                    return "ok vuelo " + (on ? "activado" : "desactivado");
                }
                case "toasts": {
                    allowToasts = a.length > 0 && a[0].equalsIgnoreCase("on");
                    if (!allowToasts) mc.getToasts().clear();
                    return "ok avisos " + (allowToasts ? "permitidos" : "borrados cada tick");
                }
                case "cursor": {
                    if (a.length == 0) return "FAIL uso: cursor park|free|<x> <y>";
                    if (a[0].equalsIgnoreCase("park")) { parkCursor = true; parkCursor(mc, 0, 0); return "ok cursor a 0,0 en cada pantalla"; }
                    if (a[0].equalsIgnoreCase("free")) { parkCursor = false; return "ok cursor sin tocar"; }
                    double x = Double.parseDouble(a[0]), y = Double.parseDouble(a[1]);
                    parkCursor(mc, x, y);
                    return "ok cursor en " + fmt1(x) + "," + fmt1(y);
                }
                case "particles": {
                    String v = a.length == 0 ? "all" : a[0].toLowerCase(Locale.ROOT);
                    ParticleStatus st = switch (v) {
                        case "minimal", "min" -> ParticleStatus.MINIMAL;
                        case "decreased" -> ParticleStatus.DECREASED;
                        default -> ParticleStatus.ALL;
                    };
                    ParticleStatus old = mc.options.particles().get();
                    mc.options.particles().set(st);
                    return "ok particulas " + old + " -> " + st;
                }
                default:
                    return "FAIL comando desconocido '" + s.op() + "'";
            }
        } catch (ArrayIndexOutOfBoundsException | NumberFormatException e) {
            return "FAIL argumentos invalidos (" + e.getMessage() + ")";
        }
    }

    /** Sigue un paso en curso. Devuelve el resultado al terminar, o null. */
    private String pollStep(Minecraft mc, Step s) {
        long el = ticks - stepStart;
        switch (s.op()) {
            case "wait":
                return el >= stepAux ? "ok" : null;
            case "cmd":
            case "cmdtry":
            case "frame":
            case "framefound":
            case "tpbox":
            case "forceloadbox":
            case "tp": {
                if (!pending.isDone()) {
                    if (el > 20L * 60 * 5) return "FAIL el servidor no respondio en 5 min";
                    if (el > 0 && el % 1200 == 0) log("  {} sigue en curso ({} s)", s.line(), el / 20);
                    return null;
                }
                CmdResult r = pending.join();
                for (String m : r.messages()) log("  > {}", m.replace('\n', ' '));
                if (r.error() != null) PackTest.LOG.error("[packtest]   excepcion en el comando", r.error());
                String verdict = !r.failed() ? "ok " : s.op().equals("cmdtry") ? "ok (fallo ignorado) " : "FAIL ";
                return verdict + describe(r) + (el > 40 ? " (" + el / 20 + " s)" : "");
            }
            case "tpbiome": {
                if (!pending.isDone()) {
                    return el > 20L * 60 * 5 ? "FAIL el servidor no respondio en 5 min" : null;
                }
                CmdResult r = pending.join();
                for (String m : r.messages()) log("  > {}", m.replace('\n', ' '));
                if (stepAux == 1) {
                    return (r.failed() ? "FAIL " : "ok ") + describe(r);
                }
                if (r.failed()) return "FAIL " + describe(r);
                java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("\\[(-?\\d+), [^,]+, (-?\\d+)\\]").matcher(String.join(" ", r.messages()));
                if (!m.find()) return "FAIL no entiendo la salida de locate: " + describe(r);
                stepAux = 1;
                pending = runServer(mc, "spreadplayers " + m.group(1) + " " + m.group(2) + " 0 8 false @s");
                return null;
            }
            case "cmdretry": {
                if (pending == null) {
                    if (ticks < retryAt) return null;
                    String c = String.join(" ", java.util.Arrays.copyOfRange(s.args(), 1, s.args().length));
                    if (c.startsWith("/")) c = c.substring(1);
                    pending = runServer(mc, c);
                    return null;
                }
                if (!pending.isDone()) return el > stepAux + 20L * 60 * 5 ? "FAIL el servidor no respondio" : null;
                CmdResult r = pending.join();
                pending = null;
                if (!r.failed()) {
                    for (String m : r.messages()) log("  > {}", m.replace('\n', ' '));
                    return "ok " + describe(r) + " (" + el / 20 + " s)";
                }
                if (el >= stepAux) return "FAIL tras " + el / 20 + " s: " + describe(r);
                log("  cmdretry: aun falla ({}), reintento en 5 s", describe(r));
                retryAt = ticks + 100;
                return null;
            }
            case "placestruct": {
                if (placePending == null) {
                    if (ticks < retryAt) return null;
                    placePending = placeServer(mc, new ResourceLocation(s.args()[1]), intArg(s.args(), 2), intArg(s.args(), 3));
                    return null;
                }
                if (!placePending.isDone()) return el > stepAux + 20L * 60 * 5 ? "FAIL el servidor no respondio" : null;
                PlaceResult r = placePending.join();
                placePending = null;
                if (r.ok()) {
                    lastBox = r.box();
                    lastBoxName = s.args()[1];
                    return "ok " + r.msg() + " (" + el / 20 + " s)";
                }
                if (!r.retry() || el >= stepAux) return "FAIL tras " + el / 20 + " s: " + r.msg();
                log("  placestruct: aun no ({}), reintento en 5 s", r.msg());
                retryAt = ticks + 100;
                return null;
            }
            case "findblock":
            case "findentity":
            case "clearabove":
            case "expect_block": {
                if (!srvPending.isDone()) return el > 20L * 60 * 5 ? "FAIL el servidor no respondio en 5 min" : null;
                SrvResult r = srvPending.join();
                srvPending = null;
                if (s.op().equals("findblock") || s.op().equals("findentity")) lastFound = r.found();
                return r.verdict();
            }
            case "useitem":
                if (el >= stepAux) {
                    mc.options.keyUse.setDown(false);
                    return "ok mantenido " + stepAux + " ticks";
                }
                return null;
            case "waitload": {
                int missing = missingChunks(mc, stepAux);
                if (missing == 0) return "ok radio " + stepAux + " cargado en " + el / 20.0 + " s";
                if (el > 20L * 60 * 10) return "FAIL faltan " + missing + " chunks tras 10 min";
                if (el > 0 && el % 600 == 0) log("  waitload {}: faltan {} chunks ({} s)", stepAux, missing, el / 20);
                return null;
            }
            default:
                return "ok";
        }
    }

    private static String fmt(Vec3 v) {
        return String.format(Locale.ROOT, "%.1f %.1f %.1f", v.x, v.y, v.z);
    }

    private static String fmt1(double d) {
        return String.format(Locale.ROOT, "%.1f", d);
    }

    private void scanWatched(Minecraft mc) {
        if (mc.level == null) return;
        Map<ResourceLocation, Boolean> now = new HashMap<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
            if (watched.containsKey(id)) now.put(id, true);
        }
        for (ResourceLocation id : now.keySet()) {
            int n = watched.get(id);
            if (n == 0) log("  watch: aparece {}", id);
            watched.put(id, n + 1);
        }
    }

    /** Muestra reducida (SAMPLE_W x SAMPLE_H) de la imagen, en ABGR. */
    private static int[] sample(NativeImage img) {
        int[] out = new int[SAMPLE_W * SAMPLE_H];
        int w = img.getWidth(), h = img.getHeight();
        for (int y = 0; y < SAMPLE_H; y++)
            for (int x = 0; x < SAMPLE_W; x++)
                out[y * SAMPLE_W + x] = img.getPixelRGBA(Math.min(w - 1, (x * w + w / 2) / SAMPLE_W), Math.min(h - 1, (y * h + h / 2) / SAMPLE_H));
        return out;
    }

    private static double diffPercent(int[] a, int[] b) {
        int n = Math.min(a.length, b.length), diff = 0;
        for (int i = 0; i < n; i++) {
            int x = a[i], y = b[i];
            int d = Math.max(Math.abs((x & 0xFF) - (y & 0xFF)),
                    Math.max(Math.abs(((x >> 8) & 0xFF) - ((y >> 8) & 0xFF)), Math.abs(((x >> 16) & 0xFF) - ((y >> 16) & 0xFF))));
            if (d > 24) diff++;
        }
        return n == 0 ? 0 : 100.0 * diff / n;
    }

    /**
     * Cambia un campo por reflexion. La ruta empieza en un campo o metodo estatico sin argumentos de la clase,
     * p.ej. "config.enabled" o "get().animationSpeedTicks"; el ultimo tramo es el campo a cambiar.
     */
    private static String setField(String className, String path, String value) {
        try {
            Class<?> cls;
            try {
                cls = Class.forName(className, true, ClientRunner.class.getClassLoader());
            } catch (ClassNotFoundException e) {
                cls = Class.forName(className, true, Thread.currentThread().getContextClassLoader());
            }
            String[] parts = path.split("\\.");
            Object obj = null;
            Class<?> c = cls;
            for (int i = 0; i < parts.length - 1; i++) {
                String part = parts[i];
                if (part.endsWith("()")) {
                    Method m = findMethod(c, part.substring(0, part.length() - 2));
                    m.setAccessible(true);
                    obj = m.invoke(Modifier.isStatic(m.getModifiers()) ? null : obj);
                } else {
                    Field f = findField(c, part);
                    f.setAccessible(true);
                    obj = f.get(Modifier.isStatic(f.getModifiers()) ? null : obj);
                }
                if (obj == null) return "FAIL " + part + " es null";
                c = obj.getClass();
            }
            Field f = findField(c, parts[parts.length - 1]);
            f.setAccessible(true);
            Object target = Modifier.isStatic(f.getModifiers()) ? null : obj;
            Object old = f.get(target);
            Class<?> t = f.getType();
            Object v;
            if (t == int.class || t == Integer.class) v = Integer.parseInt(value);
            else if (t == boolean.class || t == Boolean.class) v = Boolean.parseBoolean(value);
            else if (t == float.class || t == Float.class) v = Float.parseFloat(value);
            else if (t == double.class || t == Double.class) v = Double.parseDouble(value);
            else if (t == long.class || t == Long.class) v = Long.parseLong(value);
            else if (t == String.class) v = value;
            else return "FAIL tipo no soportado " + t.getName();
            f.set(target, v);
            return "ok " + className + "." + path + ": " + old + " -> " + v;
        } catch (Throwable e) {
            return "FAIL " + e;
        }
    }

    private static Field findField(Class<?> c, String name) throws NoSuchFieldException {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            try { return k.getDeclaredField(name); } catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(c.getName() + "." + name);
    }

    private static Method findMethod(Class<?> c, String name) throws NoSuchMethodException {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            try { return k.getDeclaredMethod(name); } catch (NoSuchMethodException ignored) {}
        }
        throw new NoSuchMethodException(c.getName() + "." + name + "()");
    }

    private static Vec3 boxCenter(BoundingBox b) {
        return new Vec3((b.minX() + b.maxX() + 1) / 2.0, (b.minY() + b.maxY() + 1) / 2.0, (b.minZ() + b.maxZ() + 1) / 2.0);
    }

    /** tp que mira a un punto desde una distancia, con pitch (positivo = mirando abajo) y yaw como "look". */
    private static String lookAtCmd(Vec3 t, double d, double pitch, double yaw) {
        double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
        double lx = -Math.sin(yr) * Math.cos(pr), ly = -Math.sin(pr), lz = Math.cos(yr) * Math.cos(pr);
        return String.format(Locale.ROOT, "tp @s %.2f %.2f %.2f facing %.2f %.2f %.2f",
                t.x - lx * d, t.y - ly * d, t.z - lz * d, t.x, t.y, t.z);
    }

    /** Lleva el cursor (y la posicion que guarda MouseHandler) a x,y de la ventana: sin tooltips por pasar por encima. */
    private static void parkCursor(Minecraft mc, double x, double y) {
        try {
            GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), x, y);
            if (mouseX == null) {
                mouseX = ObfuscationReflectionHelper.findField(MouseHandler.class, "f_91507_");
                mouseY = ObfuscationReflectionHelper.findField(MouseHandler.class, "f_91508_");
            }
            mouseX.setDouble(mc.mouseHandler, x);
            mouseY.setDouble(mc.mouseHandler, y);
        } catch (Throwable t) {
            log("  no pude mover el cursor: {}", t.toString());
        }
    }

    /** Ejecuta algo en el hilo del servidor integrado sobre el nivel del jugador. */
    private CompletableFuture<SrvResult> onServer(Minecraft mc, Function<ServerLevel, SrvResult> fn) {
        CompletableFuture<SrvResult> f = new CompletableFuture<>();
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) {
            f.complete(new SrvResult("FAIL sin servidor integrado o sin jugador", null));
            return f;
        }
        UUID uuid = mc.player.getUUID();
        server.execute(() -> {
            try {
                ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
                f.complete(fn.apply(sp != null ? sp.serverLevel() : server.overworld()));
            } catch (Throwable t) {
                PackTest.LOG.error("[packtest] excepcion en el servidor", t);
                f.complete(new SrvResult("FAIL excepcion " + t, null));
            }
        });
        return f;
    }

    /** Busca en la caja bloques cuyo id coincida; devuelve el mas cercano al centro. Sin bloque: FAIL y centro de la caja. */
    private static SrvResult findBlock(ServerLevel level, BoundingBox b, Pattern pat) {
        Set<Block> targets = new HashSet<>();
        for (Block bl : BuiltInRegistries.BLOCK) if (pat.matcher(BuiltInRegistries.BLOCK.getKey(bl).toString()).matches()) targets.add(bl);
        Vec3 center = boxCenter(b);
        if (targets.isEmpty()) return new SrvResult("FAIL ningun bloque registrado coincide con " + pat + "; uso el centro de la caja " + fmt(center), center);
        int count = 0, skipped = 0;
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        Map<String, Integer> perId = new LinkedHashMap<>();
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        for (int cx = SectionPos.blockToSectionCoord(b.minX()); cx <= SectionPos.blockToSectionCoord(b.maxX()); cx++) {
            for (int cz = SectionPos.blockToSectionCoord(b.minZ()); cz <= SectionPos.blockToSectionCoord(b.maxZ()); cz++) {
                if (!level.hasChunk(cx, cz)) { skipped++; continue; }
                LevelChunk ch = level.getChunk(cx, cz);
                LevelChunkSection[] secs = ch.getSections();
                for (int si = 0; si < secs.length; si++) {
                    LevelChunkSection sec = secs[si];
                    if (sec.hasOnlyAir() || !sec.maybeHas(st -> targets.contains(st.getBlock()))) continue;
                    int sy = SectionPos.sectionToBlockCoord(ch.getSectionYFromSectionIndex(si));
                    for (int y = Math.max(sy, b.minY()); y <= Math.min(sy + 15, b.maxY()); y++)
                        for (int x = Math.max(cx << 4, b.minX()); x <= Math.min((cx << 4) + 15, b.maxX()); x++)
                            for (int z = Math.max(cz << 4, b.minZ()); z <= Math.min((cz << 4) + 15, b.maxZ()); z++) {
                                BlockState st = sec.getBlockState(x & 15, y & 15, z & 15);
                                if (!targets.contains(st.getBlock())) continue;
                                count++;
                                perId.merge(BuiltInRegistries.BLOCK.getKey(st.getBlock()).toString(), 1, Integer::sum);
                                mp.set(x, y, z);
                                double d = Vec3.atCenterOf(mp).distanceToSqr(center);
                                if (d < bestD) { bestD = d; best = mp.immutable(); }
                            }
                }
            }
        }
        String extra = skipped > 0 ? " (" + skipped + " chunks sin cargar no revisados)" : "";
        if (best == null) return new SrvResult("FAIL ningun " + pat + " en la caja" + extra + "; uso el centro de la caja " + fmt(center), center);
        return new SrvResult("ok " + count + " bloques " + perId + "; el mas cercano al centro en " + best.toShortString() + extra, Vec3.atCenterOf(best));
    }

    /** Busca entidades de un tipo (cargadas en el servidor); devuelve la mas cercana a from. Sin entidad: FAIL y from. */
    private static SrvResult findEntity(ServerLevel level, ResourceLocation id, Vec3 from, BoundingBox box) {
        Entity best = null;
        double bestD = Double.MAX_VALUE;
        int count = 0;
        for (Entity e : level.getAllEntities()) {
            if (e == null || !id.equals(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()))) continue;
            count++;
            double d = e.position().distanceToSqr(from);
            if (d < bestD) { bestD = d; best = e; }
        }
        if (best == null) return new SrvResult("FAIL no hay ninguna entidad " + id + " cargada; uso " + (box != null ? "el centro de la caja " : "la posicion del jugador ") + fmt(from), from);
        Vec3 c = best.getBoundingBox().getCenter();
        return new SrvResult("ok " + count + " entidades " + id + "; la mas cercana en " + fmt(c)
                + (box != null ? (box.isInside(best.blockPosition()) ? " (dentro de la caja)" : " (FUERA de la caja)") : ""), c);
    }

    /** Pone aire encima de la caja: x/z de la caja +- margin, desde maxY+1 hasta maxY+height. */
    private static SrvResult clearAbove(ServerLevel level, BoundingBox b, int margin, int height) {
        int changed = 0, skipped = 0;
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        int y0 = b.maxY() + 1, y1 = Math.min(level.getMaxBuildHeight() - 1, b.maxY() + height);
        for (int x = b.minX() - margin; x <= b.maxX() + margin; x++)
            for (int z = b.minZ() - margin; z <= b.maxZ() + margin; z++) {
                if (!level.hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))) { skipped++; continue; }
                for (int y = y0; y <= y1; y++) {
                    mp.set(x, y, z);
                    if (level.getBlockState(mp).isAir()) continue;
                    level.setBlock(mp, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    changed++;
                }
            }
        return new SrvResult(String.format(Locale.ROOT, "ok %d bloques quitados en %d %d %d -> %d %d %d%s", changed,
                b.minX() - margin, y0, b.minZ() - margin, b.maxX() + margin, y1, b.maxZ() + margin,
                skipped > 0 ? " (" + skipped + " columnas sin cargar)" : ""), null);
    }

    /** Como /place structure, pero devuelve la caja de la estructura para poder encuadrarla. */
    private CompletableFuture<PlaceResult> placeServer(Minecraft mc, ResourceLocation id, int x, int z) {
        CompletableFuture<PlaceResult> f = new CompletableFuture<>();
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) {
            f.complete(new PlaceResult(false, false, "sin servidor integrado o sin jugador", null));
            return f;
        }
        UUID uuid = mc.player.getUUID();
        server.execute(() -> {
            try {
                ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
                ServerLevel level = sp != null ? sp.serverLevel() : server.overworld();
                Registry<Structure> reg = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
                Structure st = reg.get(id);
                if (st == null) { f.complete(new PlaceResult(false, false, "estructura desconocida " + id, null)); return; }
                ChunkGenerator gen = level.getChunkSource().getGenerator();
                ChunkPos origin = new ChunkPos(new BlockPos(x, 0, z));
                StructureStart start = st.generate(level.registryAccess(), gen, gen.getBiomeSource(),
                        level.getChunkSource().randomState(), level.getStructureManager(), level.getSeed(),
                        origin, 0, level, b -> true);
                if (!start.isValid()) { f.complete(new PlaceResult(false, true, "la estructura no se pudo generar", null)); return; }
                BoundingBox bb = start.getBoundingBox();
                ChunkPos c0 = new ChunkPos(SectionPos.blockToSectionCoord(bb.minX()), SectionPos.blockToSectionCoord(bb.minZ()));
                ChunkPos c1 = new ChunkPos(SectionPos.blockToSectionCoord(bb.maxX()), SectionPos.blockToSectionCoord(bb.maxZ()));
                int missing = (int) ChunkPos.rangeClosed(c0, c1).filter(cp -> !level.isLoaded(cp.getWorldPosition())).count();
                if (missing > 0) { f.complete(new PlaceResult(false, true, missing + " chunks sin cargar en el servidor", null)); return; }
                ChunkPos.rangeClosed(c0, c1).forEach(cp -> start.placeInChunk(level, level.structureManager(), gen, level.getRandom(),
                        new BoundingBox(cp.getMinBlockX(), level.getMinBuildHeight(), cp.getMinBlockZ(),
                                cp.getMaxBlockX(), level.getMaxBuildHeight(), cp.getMaxBlockZ()), cp));
                BoundingBox fin = start.getBoundingBox();
                f.complete(new PlaceResult(true, false, String.format(Locale.ROOT,
                        "%s colocada; caja %d %d %d -> %d %d %d (centro %.1f %.1f %.1f)", id,
                        fin.minX(), fin.minY(), fin.minZ(), fin.maxX(), fin.maxY(), fin.maxZ(),
                        (fin.minX() + fin.maxX() + 1) / 2.0, (fin.minY() + fin.maxY() + 1) / 2.0, (fin.minZ() + fin.maxZ() + 1) / 2.0), fin));
            } catch (Throwable t) {
                PackTest.LOG.error("[packtest] placestruct " + id, t);
                f.complete(new PlaceResult(false, false, "excepcion " + t, null));
            }
        });
        return f;
    }

    // ------------------------------------------------------------------ final

    private void beginEnding() {
        Minecraft mc = Minecraft.getInstance();
        mc.options.keyUse.setDown(false);
        mc.options.keyAttack.setDown(false);
        log("fin pasos={} fallos={}", executed, failures);
        for (String f : failedSteps) log("  fallo: {}", f);
        log("errores tras entrar: {}", capture.errorCount());
        for (String e : capture.firstErrors()) log("  error: {}", e);
        endTick = ticks;
        setPhase(Phase.ENDING);
    }

    private void tickEnding(Minecraft mc) {
        if (ticks - endTick < 100) return;
        mc.options.pauseOnLostFocus = pauseOnLostFocusOrig;
        log("cerrando el juego");
        phase = Phase.DONE;
        mc.stop();
    }
}
