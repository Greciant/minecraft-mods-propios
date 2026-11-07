package wips.powers.terrain;

import com.awakenedmod.awakened.core.terrain.TempTerrain;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;
import wips.powers.WipsPowers;

/**
 * Rotura de terreno propia de wipspowers. El sistema de Awakened (TempTerrain) no planea ningun bloque en el
 * pack de WIPSS2 ("Nothing here can rot away"), asi que las habilidades nuevas usan esto: quita los bloques
 * directamente y los devuelve pasado un tiempo. Respeta gameplay.blockBreaking de Awakened (OFF = no rompe,
 * PERMANENT = no devuelve), la proteccion del spawn, el borde del mundo y los bloques con datos (cofres, etc.).
 */
@EventBusSubscriber(modid = WipsPowers.MOD_ID)
public final class WipsTerrain {
    private record Hole(ServerLevel level, BlockPos pos, BlockState state, long restoreAt) {}

    /** Ordenados por momento de vuelta: cada tick solo mira los que ya vencieron. */
    private static final java.util.PriorityQueue<Hole> PENDING = new java.util.PriorityQueue<>(java.util.Comparator.comparingLong(Hole::restoreAt));
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private WipsTerrain() {
    }

    public static boolean enabled() {
        return !TempTerrain.visualOnly();
    }

    /** Se puede quitar este bloque? @param maxHardness tope de dureza (obsidiana = 50). */
    public static boolean canTake(ServerLevel level, @Nullable ServerPlayer player, BlockPos pos, BlockState st, float maxHardness) {
        if (st.isAir() || st.hasBlockEntity() || !st.getFluidState().isEmpty()) {
            return false;
        }
        if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)) {
            return false;
        }
        float hardness = st.getDestroySpeed(level, pos);
        if (hardness < 0.0F || hardness > maxHardness || st.is(BlockTags.WITHER_IMMUNE) || st.is(BlockTags.DRAGON_IMMUNE)
                || st.is(BlockTags.PORTALS) || st.is(Blocks.BEDROCK)) {
            return false;
        }
        return player == null || level.mayInteract(player, pos);
    }

    /**
     * Quita los bloques y los agenda para volver en restoreTicks. Devuelve cuantos quito.
     * @param particles 0 = sin particulas; n = una nube de polvo cada n bloques.
     */
    public static int crumble(ServerLevel level, @Nullable ServerPlayer player, Collection<BlockPos> positions, int restoreTicks,
                              float maxHardness, int particles) {
        if (!enabled()) {
            return 0;
        }
        boolean permanent = TempTerrain.permanent();
        long at = level.getGameTime() + restoreTicks;
        int removed = 0;
        for (BlockPos p : positions) {
            BlockState st = level.getBlockState(p);
            if (!canTake(level, player, p, st, maxHardness)) {
                continue;
            }
            BlockPos pos = p.immutable();
            if (particles > 0 && removed % particles == 0) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        4, 0.3, 0.3, 0.3, 0.05);
            }
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
            if (!permanent) {
                PENDING.add(new Hole(level, pos, st, at + (removed % 20)));
            }
            removed++;
        }
        return removed;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) {
            return;
        }
        List<Hole> later = null;
        int budget = 1500; // como mucho 1500 bloques por tick
        while (budget > 0 && !PENDING.isEmpty()) {
            Hole h = PENDING.peek();
            if (h.level().getGameTime() < h.restoreAt()) {
                break; // el resto vence despues
            }
            PENDING.poll();
            budget--;
            if (!h.level().isLoaded(h.pos())) {
                continue;
            }
            AABB box = new AABB(h.pos());
            if (!h.level().getEntities((Entity) null, box, e -> e.isAlive() && !e.isSpectator() && e.isPushable()).isEmpty()) {
                if (later == null) {
                    later = new ArrayList<>();
                }
                later.add(new Hole(h.level(), h.pos(), h.state(), h.level().getGameTime() + 20)); // alguien adentro: espera
                continue;
            }
            restore(h);
        }
        if (later != null) {
            PENDING.addAll(later);
        }
    }

    private static void restore(Hole h) {
        BlockState now = h.level().getBlockState(h.pos());
        if (now.isAir() || now.canBeReplaced()) {
            h.level().setBlock(h.pos(), h.state(), Block.UPDATE_ALL);
        }
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        for (Hole h : PENDING) {
            restore(h); // al cerrar el servidor todo vuelve: no quedan agujeros guardados en el mundo
        }
        PENDING.clear();
    }
}
