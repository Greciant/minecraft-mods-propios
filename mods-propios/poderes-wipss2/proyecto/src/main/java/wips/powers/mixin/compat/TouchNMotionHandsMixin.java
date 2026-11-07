package wips.powers.mixin.compat;

import com.awakenedmod.awakened.client.AwakenedAnimations;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Touch'n'Motion: no mueve los brazos/cuerpo del jugador local mientras una habilidad de Awakened los anima. */
@Mixin(targets = "strm.touchnmotion.TouchNMotionHook", remap = false)
public abstract class TouchNMotionHandsMixin {
    @Inject(method = "applyAll", at = @At("HEAD"), cancellable = true)
    private static void wips$yieldToPowers(UUID uuid, Function<String, ModelPart> parts, boolean player, boolean mainModel, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (player && mc.player != null && mc.player.getUUID().equals(uuid) && AwakenedAnimations.libraryOwnsFirstPerson()) {
            ci.cancel();
        }
    }
}
