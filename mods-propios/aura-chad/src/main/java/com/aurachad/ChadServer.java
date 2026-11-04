package com.aurachad;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Lado servidor: valida la tecla, aplica el cooldown y avisa a quienes ven al jugador. */
public final class ChadServer {
    private ChadServer() {}

    /** Duracion del modo (la musica dura 21,9 s). */
    public static final int DURATION_TICKS = 440;
    /** Espera tras terminar antes de poder repetir. */
    public static final int COOLDOWN_TICKS = 100;

    private static final Map<UUID, Long> ACTIVE_UNTIL = new HashMap<>();
    private static final Map<UUID, Long> READY_AT = new HashMap<>();

    public static void onKey(ChadKeyPayload payload, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer p)) return;
        ctx.enqueueWork(() -> {
            long now = p.serverLevel().getGameTime();
            Long until = ACTIVE_UNTIL.get(p.getUUID());
            if (until != null && until > now) { stop(p); return; }
            if (READY_AT.getOrDefault(p.getUUID(), 0L) > now) return;
            ACTIVE_UNTIL.put(p.getUUID(), now + DURATION_TICKS);
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(p, new ChadStatePayload(p.getId(), true));
        });
    }

    private static void stop(ServerPlayer p) {
        ACTIVE_UNTIL.remove(p.getUUID());
        READY_AT.put(p.getUUID(), p.serverLevel().getGameTime() + COOLDOWN_TICKS);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(p, new ChadStatePayload(p.getId(), false));
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || ACTIVE_UNTIL.isEmpty()) return;
        Long until = ACTIVE_UNTIL.get(p.getUUID());
        if (until != null && until <= p.serverLevel().getGameTime()) stop(p);
    }

    /** Quien empieza a ver a un jugador activo recibe el estado (con la edad ya empezada no se sincroniza: solo aura). */
    @SubscribeEvent
    public static void onTrack(net.neoforged.neoforge.event.entity.player.PlayerEvent.StartTracking event) {
        if (!(event.getTarget() instanceof ServerPlayer t) || !(event.getEntity() instanceof ServerPlayer viewer)) return;
        Long until = ACTIVE_UNTIL.get(t.getUUID());
        if (until != null && until > t.serverLevel().getGameTime()) {
            long left = until - t.serverLevel().getGameTime();
            // Solo vale la pena mostrarlo si queda tiempo: el cliente arranca en la edad correspondiente (via entityId, active)
            PacketDistributor.sendToPlayer(viewer, new ChadStatePayload(t.getId(), left > 20));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ACTIVE_UNTIL.remove(event.getEntity().getUUID());
        READY_AT.remove(event.getEntity().getUUID());
    }
}
