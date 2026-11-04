package wips.powers.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import wips.powers.WipsPowers;
import wips.powers.crit.CritConfig;

/**
 * Antes de romper terreno, Awakened lanza un BreakEvent de prueba y, si algun mod lo cancela, no rompe nada
 * ("Nothing here can rot away"). En el pack de WIPSS2 algun mod lo cancela siempre: se deja constancia en el log
 * y, con terrain.ignoreBreakVeto=true, se ignora esa cancelacion.
 */
@Mixin(targets = "com.awakenedmod.awakened.core.terrain.TerrainProtection", remap = false)
public abstract class TerrainVetoMixin {
    private static long wips$lastLog;

    @Redirect(method = "allows", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/bus/api/IEventBus;post(Lnet/neoforged/bus/api/Event;)Lnet/neoforged/bus/api/Event;"))
    private Event wips$post(IEventBus bus, Event event) {
        Event out = bus.post(event);
        if (out instanceof ICancellableEvent c && c.isCanceled()) {
            boolean ignore = CritConfig.SPEC.isLoaded() && CritConfig.IGNORE_BREAK_VETO.get();
            long now = System.currentTimeMillis();
            if (now - wips$lastLog > 5000) {
                wips$lastLog = now;
                String what = event instanceof BlockEvent be ? be.getState() + " en " + be.getPos().toShortString() : "?";
                WipsPowers.LOGGER.warn("[wipspowers] Otro mod cancelo la prueba de romper bloque de Awakened ({}). {}", what,
                        ignore ? "Se ignora (terrain.ignoreBreakVeto=true)." : "Awakened no rompera nada aqui.");
            }
            if (ignore) {
                c.setCanceled(false);
            }
        }
        return out;
    }

    @Redirect(method = "allows", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;mayInteract(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean wips$mayInteract(ServerLevel level, Player player, BlockPos pos) {
        boolean ok = level.mayInteract(player, pos);
        if (!ok) {
            long now = System.currentTimeMillis();
            if (now - wips$lastLog > 5000) {
                wips$lastLog = now;
                WipsPowers.LOGGER.warn("[wipspowers] Awakened no rompe bloques en {}: zona protegida (proteccion del spawn o similar).", pos.toShortString());
            }
        }
        return ok;
    }
}
