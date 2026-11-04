package com.haki.client;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.haki.HakiAffectedPayload;
import com.haki.HakiMod;
import com.haki.HakiStatePayload;
import com.haki.HakiVisuals;
import com.haki.client.pal.HakiPalBridge;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Estado visual del Haki en el cliente: quien lo usa y quien esta paralizado. */
@EventBusSubscriber(modid = HakiMod.MODID, value = Dist.CLIENT)
public final class HakiClientFx {
    private HakiClientFx() {}

    /** Un lanzador visible: edad del efecto y opacidad (con valor anterior para interpolar). */
    public static final class Caster {
        public boolean active;
        public int age;
        public float fade, prevFade;
        /** Altura del suelo bajo el lanzador en este cuadro (NaN si no hay suelo cerca). */
        public double groundY = Double.NaN;
        /** Tick (age) en que se calculo groundY por ultima vez, y si habia suelo (para no lanzar el rayo en cada cuadro). */
        public int rayAge = -1;
        public boolean hasGround;
        /** true mientras la capa PAL de la pose esta reproduciendose para este lanzador. */
        public boolean palOn;
    }

    /** id de entidad -> lanzador */
    public static final Map<Integer, Caster> CASTERS = new HashMap<>();
    /** id de entidad -> tick de juego (cliente) en que deja de estar paralizada */
    public static final Map<Integer, Long> AFFECTED = new HashMap<>();

    // ---------------- Paquetes ----------------
    public static void onState(HakiStatePayload payload) {
        Caster c = CASTERS.computeIfAbsent(payload.casterId(), id -> new Caster());
        if (payload.active() && !c.active) c.age = 0;
        c.active = payload.active();
    }

    public static void onAffected(HakiAffectedPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long until = mc.level.getGameTime() + com.haki.HakiServer.STUN_LINGER;
        for (int id : payload.ids()) AFFECTED.put(id, until);
    }

    // ---------------- Tick ----------------
    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { clear(); return; }
        if (mc.isPaused()) return;

        Iterator<Map.Entry<Integer, Caster>> it = CASTERS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Caster> entry = it.next();
            Caster c = entry.getValue();
            c.prevFade = c.fade;
            syncPal(mc, entry.getKey(), c);
            if (c.active) {
                c.age++;
                c.fade = Math.min(1f, c.fade + 1f / HakiVisuals.CASTER_FADE_IN_TICKS);
            } else {
                c.fade = Math.max(0f, c.fade - 1f / HakiVisuals.CASTER_FADE_OUT_TICKS);
                if (c.fade <= 0f && c.prevFade <= 0f) it.remove();
            }
        }

        long now = mc.level.getGameTime();
        AFFECTED.values().removeIf(until -> until <= now);
    }

    /** Empieza/termina la capa de Player Animation Library (si esta instalada) de la pose de lanzamiento. */
    private static void syncPal(Minecraft mc, int id, Caster c) {
        if (c.active && !c.palOn) {
            net.minecraft.world.entity.Entity e = mc.level.getEntity(id);
            if (e == null) return;           // aun no esta cargada en el cliente: se reintenta el proximo tick
            HakiPalBridge.play(e);
            c.palOn = true;
        } else if (!c.active && c.palOn) {
            net.minecraft.world.entity.Entity e = mc.level.getEntity(id);
            if (e != null) HakiPalBridge.stop(e);
            c.palOn = false;
        }
    }

    /** Ticks que le quedan de stun a una entidad (0 si no esta paralizada). */
    public static long stunTicksLeft(int entityId) {
        Minecraft mc = Minecraft.getInstance();
        Long until = AFFECTED.get(entityId);
        if (until == null || mc.level == null) return 0;
        return Math.max(0, until - mc.level.getGameTime());
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    private static void clear() {
        CASTERS.clear();
        AFFECTED.clear();
        HakiScreenFx.reset();
    }
}
