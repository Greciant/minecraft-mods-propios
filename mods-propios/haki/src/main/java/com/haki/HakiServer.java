package com.haki;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Toda la logica del Haki del Rey vive aqui, en el servidor. */
public class HakiServer {

    // ---- Valores de balance (luego pasan a un archivo de config) ----
    public static final int   MAX_TICKS         = 200;   // 10 s manteniendo la tecla
    public static final double RADIUS           = 15.0;  // bloques
    public static final int   STUN_LINGER       = 100;   // 5 s de stun tras dejar de estar en el area
    public static final int   DAMAGE_INTERVAL   = 20;    // 1 golpe por segundo
    public static final float DAMAGE            = 2.0f;  // 1 corazon por golpe
    public static final float EXHAUSTION_TICK   = 0.05f; // 10 s = ~2.5 de hambre
    public static final int   MIN_FOOD          = 7;     // hambre minima para empezar
    public static final float SOUND_VOLUME      = 5.0f;  // se oye hasta ~80 bloques
    // Enfriamiento = ticks mantenidos x 2 (minimo 2 s, maximo 20 s)

    private static final String NOAI_TAG = "haki_noai";
    private static final Map<UUID, Integer> ACTIVE = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWN_UNTIL = new HashMap<>();

    // ---------------- Paquete de la tecla ----------------
    public static void onKey(HakiKeyPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (payload.pressed()) start(player); else stop(player);
        });
    }

    private static void start(ServerPlayer player) {
        UUID id = player.getUUID();
        if (ACTIVE.containsKey(id) || !player.isAlive() || player.isSpectator()) return;

        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        long until = COOLDOWN_UNTIL.getOrDefault(id, 0L);
        if (now < until) {
            player.displayClientMessage(
                Component.literal("Haki en enfriamiento: " + ((until - now + 19) / 20) + " s"), true);
            return;
        }
        if (!player.isCreative() && player.getFoodData().getFoodLevel() < MIN_FOOD) {
            player.displayClientMessage(Component.literal("Sin energia suficiente (hambre)"), true);
            return;
        }

        ACTIVE.put(id, 0);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new HakiStatePayload(player.getId(), true));
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
            HakiSounds.HAKI_KING.get(), SoundSource.PLAYERS, SOUND_VOLUME, 1.0f);
    }

    private static void stop(ServerPlayer player) {
        Integer ticks = ACTIVE.remove(player.getUUID());
        if (ticks == null) return;
        long cd = Math.min(400L, Math.max(40L, ticks * 2L));
        COOLDOWN_UNTIL.put(player.getUUID(), player.serverLevel().getGameTime() + cd);
        stopSound(player);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new HakiStatePayload(player.getId(), false));
    }

    private static void stopSound(ServerPlayer caster) {
        var packet = new ClientboundStopSoundPacket(HakiSounds.HAKI_KING.getId(), SoundSource.PLAYERS);
        double range = SOUND_VOLUME * 16.0 + 16.0;
        for (ServerPlayer p : caster.serverLevel().players()) {
            if (p.distanceToSqr(caster) < range * range) p.connection.send(packet);
        }
    }

    // ---------------- Tick del servidor ----------------
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        for (UUID id : new ArrayList<>(ACTIVE.keySet())) {
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(id);
            if (player == null) { ACTIVE.remove(id); continue; }
            if (!player.isAlive() || player.isSpectator()) { stop(player); continue; }

            int ticks = ACTIVE.merge(id, 1, Integer::sum);

            if (!player.isCreative()) {
                player.causeFoodExhaustion(EXHAUSTION_TICK);
                if (player.getFoodData().getFoodLevel() <= 0) { stop(player); continue; }
            }

            pulse(player, ticks);
            if (ticks >= MAX_TICKS) stop(player);
        }
    }

    /** Aplica stun + gravedad a todo lo cercano, y dano cada segundo. */
    private static void pulse(ServerPlayer caster, int ticks) {
        ServerLevel level = caster.serverLevel();
        AABB box = caster.getBoundingBox().inflate(RADIUS);
        boolean damageTick = ticks % DAMAGE_INTERVAL == 0;
        boolean syncTick = ticks % HakiVisuals.AFFECTED_SYNC_TICKS == 1;
        List<Integer> affected = syncTick ? new ArrayList<>() : null;

        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box, e -> canAffect(caster, e))) {
            if (target.distanceToSqr(caster) > RADIUS * RADIUS) continue;
            if (affected != null && affected.size() < HakiVisuals.AFFECTED_MAX_IDS) affected.add(target.getId());

            MobEffectInstance current = target.getEffect(HakiEffects.HAKI_KING);
            if (current == null || current.getDuration() < STUN_LINGER - 20) {
                if (target instanceof Mob mob && !mob.isNoAi()) {
                    mob.setNoAi(true); // sin IA mientras dura el stun
                    mob.getPersistentData().putBoolean(NOAI_TAG, true);
                }
                target.addEffect(new MobEffectInstance(
                    HakiEffects.HAKI_KING, STUN_LINGER, 0, false, false, false), caster);
            }
            if (damageTick) {
                target.hurt(level.damageSources().indirectMagic(caster, caster), DAMAGE);
            }
        }
        if (affected != null && !affected.isEmpty()) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new HakiAffectedPayload(affected));
        }
    }

    /** Quien empieza a ver a un lanzador activo tambien recibe sus efectos visuales. */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer caster && ACTIVE.containsKey(caster.getUUID())
                && event.getEntity() instanceof ServerPlayer viewer) {
            PacketDistributor.sendToPlayer(viewer, new HakiStatePayload(caster.getId(), true));
        }
    }

    private static boolean canAffect(ServerPlayer caster, LivingEntity e) {
        if (e == caster || !e.isAlive() || e instanceof ArmorStand) return false;
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
        // Voluntad fuerte: los jefes resisten
        if (e instanceof EnderDragon || e instanceof WitherBoss || e instanceof Warden) return false;
        if (e instanceof OwnableEntity o && caster.getUUID().equals(o.getOwnerUUID())) return false;
        return !e.isAlliedTo(caster);
    }

    // ---------------- Devolver la IA al terminar el stun ----------------
    @SubscribeEvent
    public static void onEffectExpired(MobEffectEvent.Expired event) {
        restoreAi(event.getEntity(), event.getEffectInstance());
    }

    @SubscribeEvent
    public static void onEffectRemoved(MobEffectEvent.Remove event) {
        restoreAi(event.getEntity(), event.getEffectInstance());
    }

    private static void restoreAi(LivingEntity entity, MobEffectInstance inst) {
        if (inst == null || entity.level().isClientSide) return;
        if (!inst.getEffect().is(HakiEffects.HAKI_KING.getId())) return;
        if (entity instanceof Mob mob && mob.getPersistentData().getBoolean(NOAI_TAG)) {
            mob.setNoAi(false);
            mob.getPersistentData().remove(NOAI_TAG);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) stop(p);
    }

    // Al cerrar el mundo se olvidan los enfriamientos: el tiempo de juego de otro
    // mundo no tiene nada que ver y podria dejar un enfriamiento enorme.
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ACTIVE.clear();
        COOLDOWN_UNTIL.clear();
    }
}
