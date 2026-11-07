package wips.powers.mixin;

import com.awakenedmod.awakened.content.scaler.HitchStompAbility;
import com.awakenedmod.awakened.content.scaler.ScalerFx;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import wips.powers.scaler.ScalerWaves;

/** Stomp: mas grande = mas rango, mas dano y mas destruccion (crater que vuelve a los 20 s). */
@Mixin(value = HitchStompAbility.class, remap = false)
public abstract class ScalerStompMixin {
    @Redirect(method = "slam", at = @At(value = "INVOKE",
            target = "Lcom/awakenedmod/awakened/content/scaler/ScalerFx;shockwave(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/phys/Vec3;DFDDF)Ljava/util/List;"))
    private List<LivingEntity> wips$scaledStomp(ServerPlayer player, Vec3 center, double radius, float damage, double knockback, double knockUp, float stageScale) {
        float scale = Math.max(stageScale, player.getScale());
        double r = ScalerWaves.stompRadius(radius, stageScale, scale);
        float d = ScalerWaves.stompDamage(damage, stageScale, scale);
        List<LivingEntity> struck = ScalerFx.shockwave(player, center, r, d, knockback * (1.0 + scale * 0.05), knockUp + scale * 0.02, scale);
        ScalerWaves.crater(player, r, scale);
        return struck;
    }
}
