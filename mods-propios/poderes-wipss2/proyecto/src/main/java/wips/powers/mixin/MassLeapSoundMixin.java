package wips.powers.mixin;

import com.awakenedmod.awakened.content.scaler.MassLeapAbility;
import com.awakenedmod.awakened.core.Fx;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import wips.powers.WipsSounds;

/** Mass Leap: la carga suena con el audio del usuario (en vez del squish de slime) y se corta al saltar o cancelar. */
@Mixin(value = MassLeapAbility.class, remap = false)
public abstract class MassLeapSoundMixin {
    @Redirect(method = {"activate", "tickCharge"}, at = @At(value = "INVOKE",
            target = "Lcom/awakenedmod/awakened/core/Fx;sound(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V"))
    private void wips$muteSquish(Level level, Vec3 pos, SoundEvent sound, SoundSource source, float volume, float pitch) {
        if (sound != SoundEvents.SLIME_SQUISH) {
            Fx.sound(level, pos, sound, source, volume, pitch);
        }
    }

    @Inject(method = "activate", at = @At("RETURN"))
    private void wips$chargeSound(ServerPlayer player, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), WipsSounds.MASS_LEAP_CHARGE.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }

    @Inject(method = "launch", at = @At("HEAD"))
    private void wips$stopOnLaunch(ServerPlayer player, @org.spongepowered.asm.mixin.injection.Coerce Object charge, CallbackInfo ci) {
        wips$stop(player);
    }

    @Inject(method = "abortCharge", at = @At("HEAD"))
    private void wips$stopOnAbort(ServerPlayer player, CallbackInfo ci) {
        wips$stop(player);
    }

    private static void wips$stop(ServerPlayer player) {
        ClientboundStopSoundPacket stop = new ClientboundStopSoundPacket(WipsSounds.MASS_LEAP_CHARGE.getId(), SoundSource.PLAYERS);
        for (ServerPlayer p : player.serverLevel().players()) {
            if (p.distanceToSqr(player) < 64 * 64) {
                p.connection.send(stop);
            }
        }
    }
}
