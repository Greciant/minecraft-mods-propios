package wips.powers.level;

import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import wips.powers.WipsPowers;
import wips.powers.content.ItemPurge;

/**
 * XP de nivel por actividades cotidianas: minar, plantar y cosechar, matar, explorar, lootear y sobrevivir.
 * Premia la variedad y corta los abusos:
 *  - rendimientos decrecientes por fuente: pasados N eventos en la ventana (5 min), cada uno vale menos;
 *  - tope de XP por minuto relativo al nivel;
 *  - bonus por variedad cuando hubo varias fuentes distintas en la ventana;
 *  - minar/talar solo cuenta bloques que no puso un jugador; cosechar solo cultivos maduros;
 *  - matar: nada por mobs de spawner/huevo/comando, ni si el golpe final no fue del jugador;
 *  - explorar: chunks que nadie habia habitado (tiempo habitado bajo) y biomas nunca pisados;
 *  - lootear: solo contenedores que todavia tienen loot table (se rellenan una sola vez);
 *  - sobrevivir: por minuto solo si el jugador se movio, y por cada dia completo sin morir.
 */
@EventBusSubscriber(modid = WipsPowers.MOD_ID)
public final class LevelXp {
    public enum Source { MINE, PLANT, HARVEST, KILL, EXPLORE, LOOT, SURVIVE, QUEST }

    private static final class Tracker {
        final EnumMap<Source, Double> load = new EnumMap<>(Source.class);
        long lastDecay;
        double minuteXp;
        long minuteStart;
        double lastX;
        double lastZ;
        long lastDayCounted = -1;
        long lastChunk = Long.MIN_VALUE;
    }

    private static final Map<UUID, Tracker> TRACK = new HashMap<>();
    /** Bloques colocados por jugadores (por dimension), acotado: los mas viejos se olvidan. */
    private static final Map<ServerLevel, LongLinkedOpenHashSet> PLACED = new java.util.IdentityHashMap<>();
    private static final int PLACED_CAP = 200_000;

    /** Autotest/registro: total entregado por fuente. */
    public static final EnumMap<Source, Double> GIVEN = new EnumMap<>(Source.class);

    static {
        ItemPurge.onCompensate = (p, xp) -> award(p, Source.QUEST, xp);
    }

    private LevelXp() {
    }

    private static Tracker tracker(ServerPlayer p) {
        return TRACK.computeIfAbsent(p.getUUID(), u -> new Tracker());
    }

    /** Entrega XP aplicando rendimientos decrecientes, variedad y tope por minuto. Devuelve la XP real dada. */
    public static double award(ServerPlayer player, Source source, double base) {
        if (base <= 0 || player.isSpectator() || player instanceof net.neoforged.neoforge.common.util.FakePlayer && !SELFTEST) {
            return 0;
        }
        Tracker t = tracker(player);
        long now = player.serverLevel().getGameTime();
        double window = LevelConfig.WINDOW_SECONDS.get() * 20.0;
        // decaimiento exponencial de la carga de cada fuente
        if (t.lastDecay != 0 && now > t.lastDecay) {
            double f = Math.exp(-(now - t.lastDecay) / window);
            t.load.replaceAll((k, v) -> v * f);
        }
        t.lastDecay = now;
        double load = t.load.getOrDefault(source, 0.0);
        int free = LevelConfig.FREE_EVENTS.get();
        double dimin = source == Source.QUEST ? 1.0 : 1.0 / (1.0 + Math.max(0.0, load - free) / (free * 0.5));
        t.load.put(source, load + 1.0);
        int distinct = 0;
        for (var e : t.load.entrySet()) {
            if (e.getValue() >= 1.0) {
                distinct++;
            }
        }
        double variety = 1.0 + LevelConfig.VARIETY_BONUS.get() * Math.min(4, Math.max(0, distinct - 1));
        double xp = base * dimin * variety;
        // tope por minuto
        if (now - t.minuteStart >= 1200) {
            t.minuteStart = now;
            t.minuteXp = 0;
        }
        double cap = Math.max(30.0, WipsLevel.xpForNext(WipsLevel.level(player)) * LevelConfig.CAP_PER_MINUTE.get());
        if (source != Source.QUEST) {
            xp = Math.min(xp, Math.max(0.0, cap - t.minuteXp));
            t.minuteXp += xp;
        }
        if (xp > 0) {
            WipsLevel.addXp(player, xp);
            GIVEN.merge(source, xp, Double::sum);
        }
        return xp;
    }

    /** Solo para el autotest con jugadores falsos. */
    public static boolean SELFTEST;

    // ---------------- minar, talar, cosechar, plantar ----------------

    private static LongLinkedOpenHashSet placed(ServerLevel level) {
        return PLACED.computeIfAbsent(level, l -> new LongLinkedOpenHashSet());
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        LongLinkedOpenHashSet set = placed(level);
        set.add(event.getPos().asLong());
        if (set.size() > PLACED_CAP) {
            set.removeFirstLong();
        }
        Block b = event.getPlacedBlock().getBlock();
        if (b instanceof CropBlock || b instanceof StemBlock || b instanceof SaplingBlock) {
            award(player, Source.PLANT, LevelConfig.XP_PLANT.get());
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getPlayer() instanceof ServerPlayer player) || player.isCreative()) {
            return;
        }
        BlockPos pos = event.getPos();
        BlockState st = event.getState();
        boolean byPlayer = placed(level).remove(pos.asLong());
        if (st.getBlock() instanceof CropBlock crop) {
            if (crop.isMaxAge(st)) {
                award(player, Source.HARVEST, LevelConfig.XP_HARVEST.get());
            }
            return;
        }
        if (byPlayer) {
            return; // poner y romper no da nada
        }
        if (st.is(Tags.Blocks.ORES)) {
            award(player, Source.MINE, LevelConfig.XP_ORE.get() * oreTier(st));
        } else if (st.is(BlockTags.LOGS)) {
            award(player, Source.MINE, LevelConfig.XP_LOG.get());
        } else if (st.is(Blocks.MELON) || st.is(Blocks.PUMPKIN)) {
            award(player, Source.HARVEST, LevelConfig.XP_HARVEST.get());
        }
    }

    private static double oreTier(BlockState st) {
        if (st.is(Blocks.ANCIENT_DEBRIS)) {
            return 5.0;
        }
        if (st.is(Tags.Blocks.ORES_DIAMOND) || st.is(Tags.Blocks.ORES_EMERALD)) {
            return 4.0;
        }
        if (st.is(Tags.Blocks.ORES_GOLD) || st.is(Tags.Blocks.ORES_REDSTONE) || st.is(Tags.Blocks.ORES_LAPIS) || st.is(Tags.Blocks.ORES_QUARTZ)) {
            return 2.0;
        }
        if (st.is(Tags.Blocks.ORES_IRON) || st.is(Tags.Blocks.ORES_COPPER)) {
            return 1.5;
        }
        return 1.0;
    }

    // ---------------- matar ----------------

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide() || dead instanceof net.minecraft.world.entity.player.Player) {
            return;
        }
        ServerPlayer player = event.getSource().getEntity() instanceof ServerPlayer sp ? sp
                : dead.getKillCredit() instanceof ServerPlayer sp2 ? sp2 : null;
        if (player == null || player == dead) {
            return;
        }
        if (dead instanceof Mob mob) {
            MobSpawnType st = mob.getSpawnType();
            if (st == MobSpawnType.SPAWNER || st == MobSpawnType.TRIAL_SPAWNER || st == MobSpawnType.SPAWN_EGG
                    || st == MobSpawnType.COMMAND || st == MobSpawnType.BUCKET || st == MobSpawnType.BREEDING) {
                return;
            }
        }
        boolean peaceful = dead.getType().getCategory() == MobCategory.CREATURE || dead.getType().getCategory() == MobCategory.AMBIENT
                || dead.getType().getCategory() == MobCategory.WATER_AMBIENT;
        double xp = Math.min(LevelConfig.XP_KILL_MAX.get(), dead.getMaxHealth() * LevelConfig.XP_KILL_PER_HP.get());
        award(player, Source.KILL, peaceful ? xp * 0.4 : xp);
    }

    // ---------------- lootear ----------------

    @SubscribeEvent
    public static void onOpen(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator()) {
            return;
        }
        BlockEntity be = event.getLevel().getBlockEntity(event.getPos());
        if (be instanceof RandomizableContainerBlockEntity c && c.getLootTable() != null) {
            award(player, Source.LOOT, LevelConfig.XP_LOOT.get());
        }
    }

    // ---------------- explorar y sobrevivir ----------------

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator() || player.tickCount % 20 != 0) {
            return;
        }
        Tracker t = tracker(player);
        ServerLevel level = player.serverLevel();
        long chunk = ChunkPos.asLong(player.getBlockX() >> 4, player.getBlockZ() >> 4);
        if (chunk != t.lastChunk) {
            t.lastChunk = chunk;
            ChunkAccess c = level.getChunk(player.getBlockX() >> 4, player.getBlockZ() >> 4);
            if (c.getInhabitedTime() < 600) {
                award(player, Source.EXPLORE, LevelConfig.XP_CHUNK.get());
            }
            String biome = level.getBiome(player.blockPosition()).unwrapKey().map(k -> k.location().toString()).orElse(null);
            if (biome != null && WipsLevel.firstBiome(player, biome)) {
                award(player, Source.EXPLORE, LevelConfig.XP_BIOME.get());
            }
        }
        if (player.tickCount % 1200 == 0) {
            double moved = Math.abs(player.getX() - t.lastX) + Math.abs(player.getZ() - t.lastZ);
            t.lastX = player.getX();
            t.lastZ = player.getZ();
            if (moved > 8.0) {
                award(player, Source.SURVIVE, LevelConfig.XP_SURVIVE_MINUTE.get());
            }
            long alive = player.getStats().getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_DEATH));
            long days = alive / 24000L;
            if (t.lastDayCounted < 0) {
                t.lastDayCounted = days;
            } else if (days > t.lastDayCounted) {
                t.lastDayCounted = days;
                award(player, Source.SURVIVE, LevelConfig.XP_SURVIVE_DAY.get());
            }
        }
    }

    /** La carga de rendimientos decrecientes NO se borra al desconectarse (si no, reconectar la reiniciaria). */
    @SubscribeEvent
    public static void onStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        TRACK.clear();
        PLACED.clear();
    }
}
