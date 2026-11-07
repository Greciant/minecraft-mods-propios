package wips.powers.kit;

import java.util.PriorityQueue;
import java.util.Comparator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import wips.powers.WipsPowers;

/**
 * Bloques temporales de los poderes (plataformas de hielo, suelo helado): se ponen solo donde hay aire o
 * se cambian por encima de bloques simples, y vuelven a como estaban pasado un tiempo, si nadie los cambio.
 * Al cerrar el servidor todo vuelve, asi no quedan restos guardados en el mundo.
 */
@EventBusSubscriber(modid = WipsPowers.MOD_ID)
public final class TempBlocks {
    private record Temp(ServerLevel level, BlockPos pos, BlockState placed, BlockState original, long until) {}

    private static final PriorityQueue<Temp> QUEUE = new PriorityQueue<>(Comparator.comparingLong(Temp::until));

    private TempBlocks() {
    }

    /** Pone el bloque si hay aire (o algo reemplazable sin fluido). Devuelve true si lo puso. */
    public static boolean placeInAir(ServerLevel level, BlockPos pos, BlockState state, int ticks) {
        BlockState now = level.getBlockState(pos);
        if (!(now.isAir() || now.canBeReplaced() && now.getFluidState().isEmpty()) || level.isOutsideBuildHeight(pos)) {
            return false;
        }
        level.setBlock(pos, state, Block.UPDATE_ALL);
        QUEUE.add(new Temp(level, pos.immutable(), state, Blocks.AIR.defaultBlockState(), level.getGameTime() + ticks));
        return true;
    }

    /** Cambia un bloque solido simple (sin datos) por otro de forma temporal. */
    public static boolean swap(ServerLevel level, BlockPos pos, BlockState state, int ticks) {
        BlockState now = level.getBlockState(pos);
        if (now.isAir() || now.hasBlockEntity() || !now.getFluidState().isEmpty() || now.getDestroySpeed(level, pos) < 0
                || !now.isCollisionShapeFullBlock(level, pos) || now.is(state.getBlock())) {
            return false;
        }
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        QUEUE.add(new Temp(level, pos.immutable(), state, now, level.getGameTime() + ticks));
        return true;
    }

    private static void restore(Temp t) {
        if (t.level().isLoaded(t.pos()) && t.level().getBlockState(t.pos()).is(t.placed().getBlock())) {
            t.level().setBlock(t.pos(), t.original(), t.original().isAir() ? Block.UPDATE_ALL : Block.UPDATE_CLIENTS);
        }
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        int budget = 2000;
        while (budget-- > 0 && !QUEUE.isEmpty()) {
            Temp t = QUEUE.peek();
            if (t.level().getGameTime() < t.until()) {
                break;
            }
            QUEUE.poll();
            restore(t);
        }
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        while (!QUEUE.isEmpty()) {
            restore(QUEUE.poll());
        }
    }
}
