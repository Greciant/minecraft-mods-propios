package wips.powers.mixin;

import com.devilfruits.fruits.uo_uo_seiryu.client.vfx.BreathEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import wips.powers.FormFx;

/** El aliento sale de la boca humana aunque los efectos usen la version dragon. */
@Mixin(value = BreathEffect.class, remap = false)
public abstract class BreathMouthMixin {
    @Inject(method = "mouth", at = @At("HEAD"), cancellable = true)
    private static void wips$humanMouth(Entity e, float partialTick, CallbackInfoReturnable<Vec3> cir) {
        if (e instanceof Player p && FormFx.clientForm(p) != null) {
            float scale = p.getScale();
            Vec3 look = e.getViewVector(partialTick);
            cir.setReturnValue(e.getEyePosition(partialTick).add(look.scale(0.45 * scale)).add(0.0, -0.12 * scale, 0.0));
        }
    }
}
