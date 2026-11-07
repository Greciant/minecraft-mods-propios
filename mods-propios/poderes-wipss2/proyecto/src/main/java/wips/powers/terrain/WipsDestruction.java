package wips.powers.terrain;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import wips.powers.WipsPowers;

/**
 * Destruccion PERMANENTE de bloques (Ruin 4 y 5, Shinra Tensei, Golpe Serio). Intencional: no hay restauracion ni
 * veto de proteccion; solo se respeta lo fisicamente indestructible (dureza &lt; 0: bedrock, barrera, portal del End...).
 * Los contenedores sueltan su contenido para no borrar objetos. Todo pasa por una cola repartida en ticks con un
 * presupuesto global por tick, asi una destruccion enorme nunca bloquea el servidor.
 */
@EventBusSubscriber(modid = WipsPowers.MOD_ID)
public final class WipsDestruction {
    /** Bloques por tick, sumando todas las destrucciones en curso. */
    public static int budgetPerTick = 1200;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private record Job(BlockPos pos, int particles) {}

    private static final class Queue {
        final ArrayDeque<Job> jobs = new ArrayDeque<>();
        final LongOpenHashSet queued = new LongOpenHashSet();
    }

    private static final Map<ServerLevel, Queue> QUEUES = new IdentityHashMap<>();
    /** Contador total (para el autotest y el registro). */
    public static long destroyedTotal;

    private WipsDestruction() {
    }

    /** Se puede destruir? Todo lo que no sea aire ni indestructible y este dentro del mundo. */
    public static boolean breakable(ServerLevel level, BlockPos pos, BlockState st) {
        if (st.isAir() || level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)) {
            return false;
        }
        return st.getDestroySpeed(level, pos) >= 0.0F;
    }

    /**
     * Agenda la destruccion. @param particles 0 = sin particulas; n = una nube de polvo cada n bloques.
     * Devuelve cuantos bloques quedaron en cola (los repetidos y los indestructibles no cuentan).
     */
    public static int destroy(ServerLevel level, Collection<BlockPos> positions, int particles) {
        Queue q = QUEUES.computeIfAbsent(level, l -> new Queue());
        int n = 0;
        for (BlockPos p : positions) {
            BlockState st = level.getBlockState(p);
            if (!breakable(level, p, st)) {
                continue;
            }
            BlockPos pos = p.immutable();
            if (q.queued.add(pos.asLong())) {
                q.jobs.add(new Job(pos, particles > 0 && n % particles == 0 ? 1 : 0));
                n++;
            }
        }
        return n;
    }

    /** Pendientes en cola (todas las dimensiones). */
    public static int pending() {
        int n = 0;
        for (Queue q : QUEUES.values()) {
            n += q.jobs.size();
        }
        return n;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (QUEUES.isEmpty()) {
            return;
        }
        int budget = budgetPerTick;
        var it = QUEUES.entrySet().iterator();
        while (it.hasNext() && budget > 0) {
            var e = it.next();
            ServerLevel level = e.getKey();
            Queue q = e.getValue();
            while (budget > 0 && !q.jobs.isEmpty()) {
                Job j = q.jobs.poll();
                q.queued.remove(j.pos().asLong());
                budget--;
                if (!level.isLoaded(j.pos())) {
                    continue;
                }
                BlockState st = level.getBlockState(j.pos());
                if (!breakable(level, j.pos(), st)) {
                    continue;
                }
                BlockEntity be = st.hasBlockEntity() ? level.getBlockEntity(j.pos()) : null;
                if (be instanceof Container c) {
                    Containers.dropContents(level, j.pos(), c);
                }
                if (j.particles() > 0) {
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), j.pos().getX() + 0.5, j.pos().getY() + 0.5,
                            j.pos().getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.05);
                }
                level.setBlock(j.pos(), Blocks.AIR.defaultBlockState(), be != null ? Block.UPDATE_ALL : FLAGS);
                destroyedTotal++;
            }
            if (q.jobs.isEmpty()) {
                it.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        QUEUES.clear();
    }
}
