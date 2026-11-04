package wips.powers.mixin;

import com.awakenedmod.awakened.core.AimUtil;
import com.awakenedmod.awakened.core.power.Ability;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import wips.powers.WipsPowers;

/**
 * Cuando Ruin dice "Nothing here can rot away", deja en latest.log por que: que bloque mira, si Awakened
 * lo acepta, si el jugador puede romper ahi y si algun mod cancela romperlo. Solo escribe en el log.
 */
@Mixin(value = Ability.class, remap = false)
public abstract class RuinDiagnosticMixin {
    private static java.lang.reflect.Method wips$canTake;

    /** TerrainSafety.canTake es privada del paquete de Awakened: se llama por reflexion. */
    private static String wips$canTake(ServerLevel level, BlockPos pos, BlockState st) {
        try {
            if (wips$canTake == null) {
                Class<?> c = Class.forName("com.awakenedmod.awakened.core.terrain.TerrainSafety");
                java.lang.reflect.Method m = c.getDeclaredMethod("canTake", ServerLevel.class, BlockPos.class, BlockState.class);
                m.setAccessible(true);
                wips$canTake = m;
            }
            return String.valueOf(wips$canTake.invoke(null, level, pos, st));
        } catch (Throwable e) {
            return "?";
        }
    }

    @Inject(method = "fail", at = @At("HEAD"))
    private void wips$diag(ServerPlayer player, String langKey, Object[] args, CallbackInfoReturnable<Boolean> cir) {
        if (!"awakened.ruin.nothing_to_rot".equals(langKey)) {
            return;
        }
        try {
            ServerLevel level = player.serverLevel();
            Vec3 eye = player.getEyePosition();
            Vec3 look = player.getLookAngle();
            BlockHitResult hit = AimUtil.clipBlocks(level, player, eye, eye.add(look.scale(6.0)));
            StringBuilder sb = new StringBuilder("[wipspowers] Ruin sin bloques: ");
            sb.append("modo=").append(player.gameMode.getGameModeForPlayer().getName());
            sb.append(" ojo=").append(String.format("%.1f,%.1f,%.1f", eye.x, eye.y, eye.z));
            sb.append(" mira=").append(String.format("%.2f,%.2f,%.2f", look.x, look.y, look.z));
            sb.append(" minY=").append(level.getMinBuildHeight());
            if (hit.getType() != HitResult.Type.BLOCK) {
                sb.append(" | el rayo de 6 bloques no toca nada");
            } else {
                for (BlockPos pos : new BlockPos[]{hit.getBlockPos(), hit.getBlockPos().relative(hit.getDirection().getOpposite()), player.blockPosition().below()}) {
                    BlockState st = level.getBlockState(pos);
                    BlockEvent.BreakEvent probe = new BlockEvent.BreakEvent(level, pos, st, player);
                    NeoForge.EVENT_BUS.post(probe);
                    sb.append(" | ").append(pos.toShortString()).append(' ').append(st)
                            .append(" acepta=").append(wips$canTake(level, pos, st))
                            .append(" interactuar=").append(level.mayInteract(player, pos))
                            .append(" restringido=").append(player.blockActionRestricted(level, pos, player.gameMode.getGameModeForPlayer()))
                            .append(" vetoOtroMod=").append(probe.isCanceled())
                            .append(" dureza=").append(st.getDestroySpeed(level, pos));
                }
                sb.append(" cara=").append(hit.getDirection()).append(String.format(" dist=%.1f", eye.distanceTo(hit.getLocation())));
            }
            WipsPowers.LOGGER.warn(sb.toString());
        } catch (Throwable e) {
            WipsPowers.LOGGER.warn("[wipspowers] Ruin sin bloques (no se pudo diagnosticar: {})", e.toString());
        }
    }
}
