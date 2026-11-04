package wips.powers.devtest;

import com.awakenedmod.awakened.core.PowerService;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
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
import org.lwjgl.glfw.GLFW;
import wips.powers.WipsPowers;

/**
 * Prueba automatica (runFx, -Dwips.autotest=true): mundo plano, da el poder, lanza cada tecnica
 * y guarda capturas en run-fx/screenshots/wips_*.png. Log en run-fx/wips_autotest.txt.
 * WIPS_ONLY=0..5 limita a una tecnica (5 = Island Shaker de Gamma).
 */
@EventBusSubscriber(modid = WipsPowers.MOD_ID, value = Dist.CLIENT)
public final class FxAutotest {
    private static final boolean ENABLED = Boolean.getBoolean("wips.autotest");
    private static final String ONLY = System.getenv("WIPS_ONLY");
    /** WIPS_CAM=side: camara fija de costado (soporte invisible) en vez de tercera persona. */
    private static final boolean SIDE = "side".equals(System.getenv("WIPS_CAM"));
    private static java.util.UUID camId;

    /** nombre, poder, slot, ticks hasta soltar (0 = instantanea), duracion, mobs con IA, capturas, extra */
    private record Cast(String name, String power, int slot, int hold, int span, boolean ai, int[] shots, String extra) {
        Cast(String name, String power, int slot, int hold) {
            this(name, power, slot, hold, 190, false, SHOTS, "");
        }
    }
    /** WIPS_SET=gs: Graviton + Scaler. */
    private static final String SET = System.getenv().getOrDefault("WIPS_SET", "seiryuu");
    private static final int[] LONG_SHOTS = {5, 20, 35, 50, 65, 80, 100, 130, 160, 190, 210, 215, 222, 232, 245};
    private static final int[] ES_SHOTS = {3, 8, 14, 22, 32, 44, 60};
    private static final Cast[] ES = buildEs();

    private static Cast[] buildEs() {
        String[][] p = "ar".equals(SET)
            ? new String[][]{{"s", "summoning"}, {"k", "kinetic_charger"}, {"v", "velocity"}, {"r", "ruin"}, {"f", "phytokinetic"}, {"g", "gamma"}}
            : new String[][]{{"p", "pyrokinetic"}, {"h", "hydrokinetic"}, {"t", "storm_caller"}};
        java.util.List<Cast> out = new java.util.ArrayList<>();
        for (String[] pw : p) {
            for (int i = 0; i < 5; i++) {
                out.add(new Cast(pw[0] + i + "_" + pw[1], pw[1], i, 40, 150, false, ES_SHOTS, ""));
            }
        }
        return out.toArray(new Cast[0]);
    }

    private static final Cast[] GS = {
        new Cast("g0_heavy_hand", "graviton", 0, 0, 140, false, new int[]{2, 5, 8, 12, 18, 26, 40}, ""),
        new Cast("g1_gravity_well", "graviton", 1, 0, 160, false, new int[]{2, 6, 10, 16, 24, 36, 50, 70}, ""),
        new Cast("g2_weightless", "graviton", 2, 0, 140, false, new int[]{2, 6, 10, 16, 24, 36}, ""),
        new Cast("g3_pressure", "graviton", 3, 130, 230, true, new int[]{4, 15, 30, 50, 70, 90, 110, 128, 140, 160}, "speedlog"),
        new Cast("g4_chibaku", "graviton", 4, 0, 330, true, LONG_SHOTS, ""),
        new Cast("s0_scaler", "scaler", 0, 0, 330, false, new int[]{1, 4, 8, 21, 24, 41, 44, 60, 80, 100, 120, 140, 160, 180, 205, 225, 245}, "scaler"),
    };

    /** WIPS_SET=fb: dispara directo en el cliente cada efecto de AbilityFx (sin depender de que la habilidad funcione en el mundo de prueba). */
    private static final Cast[] FB = buildFb();

    private static Cast[] buildFb() {
        java.util.List<Cast> out = new java.util.ArrayList<>();
        String[] powers = {"pyrokinetic", "hydrokinetic", "storm_caller", "kinetic_charger", "velocity", "ruin", "phytokinetic", "gamma", "summoning", "graviton", "scaler", "seiryuu"};
        for (String pw : powers) {
            for (int i = 0; i < 5; i++) out.add(new Cast("fb_cast_" + pw + "_" + i, "cast_" + pw, i, 0, 70, false, new int[]{4, 12, 24}, "fb"));
        }
        String[][] act = {{"pyrokinetic", "1"}, {"pyrokinetic", "2"}, {"pyrokinetic", "3"}, {"hydrokinetic", "2"}, {"storm_caller", "1"}, {"kinetic_charger", "0"}, {"ruin", "2"}};
        for (String[] a : act) out.add(new Cast("fb_active_" + a[0] + "_" + a[1], "active_" + a[0], Integer.parseInt(a[1]), 0, 70, false, new int[]{12, 30}, "fb"));
        for (String k : new String[]{"pyro_burst", "storm_strike", "scaler_shift", "scaler_step", "chibaku_slam", "chibaku_implode"}) {
            out.add(new Cast("fb_ev_" + k, k, 1, 0, 70, false, new int[]{4, 12, 24}, "fb"));
        }
        return out.toArray(new Cast[0]);
    }

    private static final Cast[] CASTS = {
        new Cast("0_thunder_bagua", "seiryuu", 0, 0),
        new Cast("1_flame_cloud", "seiryuu", 1, 0),
        new Cast("2_draconic_thunder", "seiryuu", 2, 45),
        new Cast("3_bolo_barrage", "seiryuu", 3, 30),
        new Cast("4_blast_breath", "seiryuu", 4, 30),
        new Cast("5_island_shaker", "gamma", 1, 0),
    };
    private static final int[] SHOTS = {2, 6, 10, 15, 22, 30, 40, 52, 66, 82, 100};
    private static final int SPAN = 190;
    private static final int START = 60;

    private static final List<String> OUT = new ArrayList<>();
    private static final List<Cast> PLAN = new ArrayList<>();
    private static boolean created;
    private static int t = -1;

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
            for (Cast c : "fb".equals(SET) ? FB : "gs".equals(SET) ? GS : ("es".equals(SET) || "ar".equals(SET)) ? ES : CASTS) {
                if (ONLY == null || java.util.Arrays.stream(ONLY.split(",")).anyMatch(o -> c.name().startsWith(o + "_"))) PLAN.add(c);
            }
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
            GLFW.glfwSetWindowSize(win, 1280, 720);
            mc.options.hideGui = true;
        }
        if (t == 30) server.execute(() -> setup(server));
        if (t == 30 && "1".equals(System.getenv("WIPS_VETO"))) {
            // simula un mod del pack que cancela cualquier BreakEvent
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.BlockEvent.BreakEvent e) -> e.setCanceled(true));
            log("Veto de BreakEvent activo");
        }
        if (t == 40 && !SIDE) mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        if (SIDE && camId != null && mc.level != null && !(mc.getCameraEntity() instanceof net.minecraft.world.entity.decoration.ArmorStand)) {
            for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering()) {
                if (e.getUUID().equals(camId)) { mc.setCameraEntity(e); mc.options.setCameraType(CameraType.FIRST_PERSON); }
            }
        }

        int rel = t - START;
        if (rel < 0) return;
        int idx = 0;
        int dt = rel;
        while (idx < PLAN.size() && dt >= PLAN.get(idx).span()) {
            dt -= PLAN.get(idx).span();
            idx++;
        }
        if (idx >= PLAN.size()) {
            if (dt == 10) { write(mc); mc.stop(); }
            return;
        }
        Cast c = PLAN.get(idx);
        final int fdt = dt;
        if (c.name().startsWith("r") && (dt == 49 || dt == 56 || dt == 64 || dt == 80)) server.execute(() -> {
            ServerPlayer pl = player(server);
            int stone = 0;
            for (net.minecraft.core.BlockPos bp : net.minecraft.core.BlockPos.betweenClosed(-3, -60, 4, 3, -56, 6)) if (!pl.serverLevel().getBlockState(bp).isAir()) stone++;
            log(c.name() + " t=" + fdt + " pos=" + String.format("%.1f,%.1f,%.1f", pl.getX(), pl.getY(), pl.getZ()) + " muro=" + stone);
        });
        if ("speedlog".equals(c.extra()) && dt > 50 && dt % 20 == 0) server.execute(() -> speedLog(server, c, fdt));
        if ("scaler".equals(c.extra())) {
            if (dt == 70 || dt == 90 || dt == 100 || dt == 108 || dt == 116 || dt == 124 || dt == 132) server.execute(() -> {
                PowerService.activate(player(server), 0);
                log(c.name() + ": crecer t=" + fdt + " escala=" + player(server).getScale()
                    + " vel=" + player(server).getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED));
            });
            if (dt > 110 && dt < 200) server.execute(() -> {
                ServerPlayer p = player(server);
                p.teleportTo(p.serverLevel(), p.getX(), p.getY(), p.getZ() + 0.32, p.getYRot(), p.getXRot());
            });
            if (dt == 196) server.execute(() -> PowerService.activate(player(server), 1));
            if (dt == 206) server.execute(() -> PowerService.release(player(server), 1));
            if (dt >= 206 && dt <= 216 && dt % 2 == 0) server.execute(() -> log(c.name() + ": salto vy=" + String.format("%.2f", player(server).getDeltaMovement().y) + " y=" + String.format("%.1f", player(server).getY())));
            if (dt == 200) server.execute(() -> log(c.name() + ": gigante escala=" + player(server).getScale()
                + " vel=" + player(server).getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)));
            if (dt == 210 || dt == 230 || dt == 250) server.execute(() -> {
                com.awakenedmod.awakened.core.power.PowerRegistry.get("scaler").ability(0).onSecondaryAction(player(server), 3);
                log(c.name() + ": encoger t=" + fdt);
            });
        }
        if ("fb".equals(c.extra())) {
            if (dt == 0) server.execute(() -> {
                ServerPlayer pl = player(server);
                cmd(server, pl, "kill @e[type=!player,type=!armor_stand]");
                cmd(server, pl, "tp @s 0 -60 0 0 20");
            });
            boolean active = c.power().startsWith("active_");
            if (dt == 10 || (active && dt > 10 && dt <= 46 && dt % 3 == 0)) {
                net.minecraft.client.player.LocalPlayer lp = mc.player;
                net.minecraft.world.phys.Vec3 eye = lp.getEyePosition();
                net.minecraft.world.phys.Vec3 lk = lp.getLookAngle();
                String k = c.power();
                double px = eye.x, py = eye.y, pz = eye.z;
                float pw = 1.0F;
                int ex = c.slot();
                if (k.equals("pyro_burst") || k.equals("storm_strike") || k.equals("chibaku_slam") || k.equals("scaler_step") || k.equals("scaler_shift")) {
                    px = 0; py = -60; pz = 4; pw = k.equals("chibaku_slam") ? 6F : k.equals("scaler_step") ? 3F : 1.5F;
                    lk = new net.minecraft.world.phys.Vec3(0, 1, 0);
                }
                if (k.equals("chibaku_implode")) { px = 0; py = -55; pz = 4; }
                wips.powers.fx.WipsFx.receive(new wips.powers.fx.WipsFx.Payload(k, lp.getId(), px, py, pz, (float) lk.x, (float) lk.y, (float) lk.z, pw, 12345L + dt, ex));
                if (dt == 10) log(c.name() + ": disparado");
            }
            for (int s : c.shots()) if (dt == 10 + s) shot(mc, c.name() + "_" + String.format("%03d", s));
            return;
        }
        if (dt == 0) server.execute(() -> prepare(server, c));
        if (dt == 50) server.execute(() -> {
            ServerPlayer p = player(server);
            PowerService.activate(p, c.slot());
            if ("1".equals(System.getenv("WIPS_FAKECAST"))) {
                // la habilidad puede fallar en el mundo de prueba (p. ej. Summoning sin almas): forzamos solo su VFX
                wips.powers.fx.WipsFx.around(p, "cast_" + c.power(), p.getEyePosition(), p.getLookAngle(), 1.0F, p.getRandom().nextLong(), c.slot());
            }
            log(c.name() + ": activada (poder=" + PowerService.data(p).powerId() + ")");
        });
        if (c.hold() > 0 && dt == 50 + c.hold()) server.execute(() -> {
            PowerService.release(player(server), c.slot());
            log(c.name() + ": soltada");
        });
        for (int s : c.shots()) {
            if (dt == 50 + s) shot(mc, c.name() + "_" + String.format("%03d", s));
        }
        if ("seiryuu".equals(SET) && !SIDE && dt == 50 + 30) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        }
        if ("seiryuu".equals(SET) && !SIDE && dt == 50 + 31) {
            shot(mc, c.name() + "_frente");
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
    }

    private static void createWorld(Minecraft mc) {
        String name = "wips_fx_" + System.currentTimeMillis();
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        LevelSettings settings = new LevelSettings(name, GameType.CREATIVE, false, Difficulty.NORMAL, true,
            rules, WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(12345L, false, false),
            reg -> reg.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                .value().createWorldDimensions(), mc.screen);
        log("Creando mundo plano " + name + " plan=" + PLAN.size());
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().get(0);
    }

    private static void setup(MinecraftServer server) {
        ServerPlayer p = player(server);
        cmd(server, p, "time set 6000");
        cmd(server, p, "effect give @s resistance 99999 4 true");
        cmd(server, p, "effect give @s regeneration 99999 4 true");
        cmd(server, p, "gamemode survival");
        if (SIDE) {
            net.minecraft.world.entity.decoration.ArmorStand cam = EntityType.ARMOR_STAND.create(p.serverLevel());
            if (cam != null) {
                String[] cp = System.getenv().getOrDefault("WIPS_CAMPOS", "11,-59.2,6,90,8").split(",");
                cam.moveTo(Double.parseDouble(cp[0]), Double.parseDouble(cp[1]), Double.parseDouble(cp[2]), Float.parseFloat(cp[3]), Float.parseFloat(cp[4]));
                cam.setInvisible(true);
                cam.setNoGravity(true);
                cam.setYHeadRot(Float.parseFloat(cp[3]));
                p.serverLevel().addFreshEntity(cam);
                camId = cam.getUUID();
            }
        }
        log("Preparado en " + p.blockPosition() + (SIDE ? " camara lateral" : ""));
    }

    private static void prepare(MinecraftServer server, Cast c) {
        ServerPlayer p = player(server);
        if (!c.power().equals(PowerService.data(p).powerId())) {
            cmd(server, p, "awakened power give @s " + c.power());
        }
        cmd(server, p, "awakened mastery set @s 30");
        PowerService.data(p).setInfiniteStamina(true);
        cmd(server, p, "kill @e[type=!player,type=!armor_stand]");
        cmd(server, p, "fill -20 -61 -20 20 -61 30 minecraft:grass_block");
        cmd(server, p, "fill -20 -60 -20 20 -50 30 minecraft:air");
        if (c.name().startsWith("r3_")) cmd(server, p, "fill -3 -60 4 3 -56 6 minecraft:" + System.getenv().getOrDefault("WIPS_WALL", "stone"));
        cmd(server, p, "tp @s 0 -60 0 0 " + (c.name().startsWith("3_") ? "14" : c.name().startsWith("g4_") ? "-28" : "0"));
        ServerLevel level = p.serverLevel();
        for (int i = 0; i < 3; i++) {
            Mob m = EntityType.HUSK.create(level);
            if (m == null) continue;
            m.moveTo(-3 + i * 3, -60, 9 + i, 180f, 0f);
            m.finalizeSpawn(level, level.getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.COMMAND, null);
            m.setNoAi(!c.ai());
            level.addFreshEntity(m);
        }
        if (c.name().startsWith("g3_")) {
            // un husk lejos para ver que el area crece hasta alcanzarlo
            Mob far = EntityType.HUSK.create(level);
            if (far != null) {
                far.moveTo(0, -60, 28, 180f, 0f);
                far.setNoAi(false);
                level.addFreshEntity(far);
            }
        }
        log(c.name() + ": preparada");
    }

    private static void speedLog(MinecraftServer server, Cast c, int dt) {
        ServerPlayer p = player(server);
        StringBuilder sb = new StringBuilder(c.name() + " t=" + dt);
        for (net.minecraft.world.entity.LivingEntity e : p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                p.getBoundingBox().inflate(48), e -> e != p && !(e instanceof net.minecraft.world.entity.decoration.ArmorStand))) {
            sb.append(String.format(" | %s vel=%.3f d=%.1f", e.getType().toShortString(),
                e.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED), e.distanceTo(p)));
        }
        log(sb.toString());
    }

    private static void cmd(MinecraftServer server, ServerPlayer p, String c) {
        server.getCommands().performPrefixedCommand(p.createCommandSourceStack().withPermission(4).withSuppressedOutput(), c);
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, (SIDE ? "wips_lado_" : "wips_") + name + ".png", mc.getMainRenderTarget(), c -> { });
        log("Captura " + name);
    }

    private static synchronized void log(String s) {
        String line = "[wips-autotest] " + s;
        OUT.add(line);
        System.out.println(line);
    }

    private static synchronized void write(Minecraft mc) {
        try {
            Files.write(new File(mc.gameDirectory, "wips_autotest.txt").toPath(), OUT, StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
