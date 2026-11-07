package wips.powers.mixin;

import com.awakenedmod.awakened.content.quest.QuestRewards;
import com.awakenedmod.awakened.core.quest.QuestReward;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import wips.powers.content.ItemPurge;

/** Trials/misiones: las recompensas de objetos quitados (reliquias, adamantium...) se cambian por XP de nivel. */
@Mixin(value = QuestRewards.class, remap = false)
public abstract class QuestRewardPurgeMixin {
    @Inject(method = "stack", at = @At("RETURN"), cancellable = true)
    private static void wips$stack(CallbackInfoReturnable<QuestReward> cir) {
        cir.setReturnValue(ItemPurge.guard(cir.getReturnValue(), 60));
    }

    @Inject(method = "relic", at = @At("RETURN"), cancellable = true)
    private static void wips$relic(String relicId, CallbackInfoReturnable<QuestReward> cir) {
        cir.setReturnValue(ItemPurge.relicReplacement(150));
    }

    @Inject(method = "relicBand", at = @At("RETURN"), cancellable = true)
    private static void wips$relicBand(CallbackInfoReturnable<QuestReward> cir) {
        cir.setReturnValue(ItemPurge.relicReplacement(120));
    }
}
