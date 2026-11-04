package com.aurachad.client;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.aurachad.AuraChadMod;
import com.aurachad.ChadConfig;
import com.aurachad.ChadKeyPayload;
import com.aurachad.ChadParticles;
import com.aurachad.ChadSounds;
import com.aurachad.ChadStatePayload;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.math.Axis;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Estado cliente del modo chad: quien esta activo y desde cuando, tecla, sonido, camara, chispas y giro del cuerpo. */
public final class ChadClient {
    private ChadClient() {}

    public static final int FADE_TICKS = 10;
    public static final int TOTAL_TICKS = (int) (ChadPoses.TOTAL_SECONDS * 20f) + 4;

    public static final class State {
        int age, prevAge;
        boolean active;
        float fade, prevFade;
    }

    private static final Map<Integer, State> STATES = new HashMap<>();
    private static CameraType savedCamera;
    private static boolean keyWasDown;

    public static final KeyMapping KEY = new KeyMapping("key.aurachad.chad", InputConstants.Type.MOUSE,
        GLFW.GLFW_MOUSE_BUTTON_5, "key.categories.aurachad");

    /** Segundos desde el inicio (interpolado), o -1 si no esta activo. */
    public static float ageSeconds(int id) {
        State s = STATES.get(id);
        if (s == null || !s.active) return -1f;
        float pt = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        return (s.prevAge + (s.age - s.prevAge) * pt) / 20f;
    }

    public static float fade(int id, float pt) {
        State s = STATES.get(id);
        return s == null ? 0f : s.prevFade + (s.fade - s.prevFade) * pt;
    }

    public static void onState(ChadStatePayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        State s = STATES.get(p.entityId());
        if (p.active()) {
            if (s != null && s.active) return;
            if (s == null) STATES.put(p.entityId(), s = new State());
            s.age = s.prevAge = 0;
            s.active = true;
            Entity e = mc.level.getEntity(p.entityId());
            boolean self = e == mc.player;
            if (ChadConfig.MUSIC.get() && e != null) {
                if (self) mc.getSoundManager().play(SimpleSoundInstance.forUI(ChadSounds.AURA_CHAD.get(), 1f, 1f));
                else mc.level.playLocalSound(e.getX(), e.getY(), e.getZ(), ChadSounds.AURA_CHAD_OTHERS.get(), SoundSource.PLAYERS, 1f, 1f, false);
            }
            if (self && ChadConfig.THIRD_PERSON.get()) {
                savedCamera = mc.options.getCameraType();
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            }
        } else if (s != null && s.active) {
            end(p.entityId(), s);
        }
    }

    private static void end(int id, State s) {
        s.active = false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) com.aurachad.client.pal.ChadPalBridge.stop(mc.level.getEntity(id));
        if (mc.player != null && mc.player.getId() == id) {
            if (savedCamera != null) { mc.options.setCameraType(savedCamera); savedCamera = null; }
            mc.getSoundManager().stop(ChadSounds.AURA_CHAD.getId(), null);
            mc.getSoundManager().stop(ChadSounds.AURA_CHAD_OTHERS.getId(), null);
        }
    }

    // ----- Eventos de juego (bus NeoForge) -----
    @EventBusSubscriber(modid = AuraChadMod.MODID, value = Dist.CLIENT)
    public static final class Game {
        @SubscribeEvent
        public static void onTick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) { STATES.clear(); savedCamera = null; return; }

            boolean down = mc.screen == null && KEY.isDown();
            if (down && !keyWasDown) PacketDistributor.sendToServer(new ChadKeyPayload());
            keyWasDown = down;

            if (mc.isPaused()) return;
            Iterator<Map.Entry<Integer, State>> it = STATES.entrySet().iterator();
            while (it.hasNext()) {
                var en = it.next();
                State s = en.getValue();
                s.prevAge = s.age;
                s.prevFade = s.fade;
                Entity e = mc.level.getEntity(en.getKey());
                if (e == null) { it.remove(); continue; }
                if (s.active) {
                    s.age++;
                    // La edad mostrada va un tick por detras (interpola prevAge..age); PAL arranca un tick despues para compartir reloj con ella
                    if (s.age == 1) com.aurachad.client.pal.ChadPalBridge.play(e);
                    // Se desvanece al empezar y en el ultimo segundo
                    int left = TOTAL_TICKS - s.age;
                    float target = Math.min(1f, Math.min(s.age / (float) FADE_TICKS, left / 20f));
                    s.fade += (Math.max(0f, target) - s.fade) * 0.4f;
                    if (s.age >= TOTAL_TICKS) end(en.getKey(), s);
                    else { sparks(mc, e, s); bursts(mc, e, en.getKey(), s); }
                } else {
                    s.fade = Math.max(0f, s.fade - 0.08f);
                    if (s.fade <= 0f && s.prevFade <= 0f) it.remove();
                }
            }
        }

        /**
         * Estallido de aura cuando la edad cruza un golpe. Tras un tick con edad n el cliente muestra el intervalo [n-1, n] ticks,
         * asi que el corte (en ticks, t*20) cae en (prevAge-1, age-1] y se lanza entonces: las chispas nacen con la pose nueva ya
         * visible y nunca antes. El golpe t=0 sale en el primer tick.
         */
        private static void bursts(Minecraft mc, Entity e, int id, State s) {
            float from = s.prevAge - 1, to = s.age - 1;
            for (ChadHits.Hit h : ChadHits.all()) {
                float tt = h.t() * 20f;
                if (from < tt && tt <= to) {
                    int n = ChadBurst.fire(mc, e, h.strength());
                    var l = ChadHits.listener;
                    if (l != null) l.onBurst(id, h, to / 20f, n);
                }
            }
        }

        private static void sparks(Minecraft mc, Entity e, State s) {
            var r = mc.level.random;
            int n = 2 + (int) (3 * s.fade);
            for (int i = 0; i < n; i++) {
                double ang = r.nextDouble() * Math.PI * 2;
                double rad = 0.3 + r.nextDouble() * 0.9;
                double x = e.getX() + Math.cos(ang) * rad;
                double z = e.getZ() + Math.sin(ang) * rad;
                double y = e.getY() + r.nextDouble() * 1.9;
                mc.level.addParticle(ChadParticles.SPARK.get(), x, y, z, 0, 0.02 + r.nextDouble() * 0.035, 0);
            }
        }

        @SubscribeEvent
        public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
            STATES.clear();
            savedCamera = null;
        }

        @SubscribeEvent
        public static void onMove(MovementInputUpdateEvent event) {
            if (!ChadConfig.LOCK_MOVEMENT.get()) return;
            if (ageSeconds(event.getEntity().getId()) < 0f) return;
            var in = event.getInput();
            in.forwardImpulse = 0; in.leftImpulse = 0; in.jumping = false;
            in.up = in.down = in.left = in.right = false;
        }

        @SubscribeEvent
        public static void onCameraDistance(CalculateDetachedCameraDistanceEvent event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && ageSeconds(mc.player.getId()) >= 0f && ChadConfig.THIRD_PERSON.get()) {
                event.setDistance(ChadConfig.CAMERA_DISTANCE.get().floatValue() * ChadPoses.zoomAt(ageSeconds(mc.player.getId())));
            }
        }

        /** Gira el cuerpo entero (perfil, de espaldas) sin tocar la orientacion real del jugador. */
        @SubscribeEvent
        public static void onRenderPre(RenderLivingEvent.Pre<?, ?> event) {
            if (!(event.getEntity() instanceof net.minecraft.client.player.AbstractClientPlayer p)) return;
            float secs = ageSeconds(p.getId());
            if (secs < 0f) return;
            event.getPoseStack().pushPose();
            event.getPoseStack().mulPose(Axis.YP.rotationDegrees(-ChadPoses.yawAt(secs)));
            PUSHED.add(p.getId());
        }

        @SubscribeEvent
        public static void onRenderPost(RenderLivingEvent.Post<?, ?> event) {
            if (event.getEntity() != null && PUSHED.remove(event.getEntity().getId())) event.getPoseStack().popPose();
        }

        private static final java.util.Set<Integer> PUSHED = new java.util.HashSet<>();
    }

    // ----- Registros (bus del mod) -----
    @EventBusSubscriber(modid = AuraChadMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Mod {
        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent event) { event.register(KEY); }

        /** PAL exige registrar sus capas dentro de enqueueWork del setup de cliente. */
        @SubscribeEvent
        public static void setup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
            event.enqueueWork(com.aurachad.client.pal.ChadPalBridge::register);
            event.enqueueWork(com.aurachad.client.emf.ChadEmfBridge::register);
        }

        @SubscribeEvent
        public static void particles(RegisterParticleProvidersEvent event) {
            event.registerSpriteSet(ChadParticles.SPARK.get(), ChadSparkParticle.Provider::new);
            event.registerSpriteSet(ChadParticles.BURST.get(), ChadBurstParticle.Provider::new);
        }
    }
}
