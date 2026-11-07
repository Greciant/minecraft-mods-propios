package wips.powers.level;

import com.awakenedmod.awakened.core.Dmg;
import com.awakenedmod.awakened.core.progression.AbilityBalance;
import com.awakenedmod.awakened.core.progression.BalanceConfig;
import com.awakenedmod.awakened.core.progression.MasteryScaling;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import wips.powers.WipsPowers;

/**
 * UNA sola formula de escalado para el dano de TODOS los poderes del jugador (Awakened, wipspowers y los nuevos):
 *
 *   dano final = dano base de la habilidad * balance de la habilidad (AbilityBalance + BalanceConfig de Awakened)
 *                * multiplicador de nivel(L) * (1 + ability power)
 *   multiplicador de nivel(L) = min + (max - min) * ((L - 1) / (Lmax - 1))^curva     (por defecto 0,30 .. 1,50)
 *
 * El bonus de maestria de Awakened (+3 % por nivel de maestria, hasta +60 %) deja de aplicarse al dano del jugador
 * (MasteryScalingMixin / PlayerCasterScalingMixin), asi el multiplicador se aplica una sola vez, aca, en el evento
 * de dano. La maestria sigue desbloqueando habilidades. Los NPC (magos) conservan su escala propia.
 */
@EventBusSubscriber(modid = WipsPowers.MOD_ID)
public final class WipsScaling {
    /** Autotest: ultimo golpe escalado (antes, despues). */
    public static float lastBefore;
    public static float lastAfter;

    private WipsScaling() {
    }

    public static double levelMultiplier(int level) {
        int max = LevelConfig.MAX_LEVEL.get();
        double t = max <= 1 ? 1.0 : Math.max(0.0, Math.min(1.0, (level - 1) / (double) (max - 1)));
        double min = LevelConfig.DMG_MIN.get();
        return min + (LevelConfig.DMG_MAX.get() - min) * Math.pow(t, LevelConfig.DMG_CURVE.get());
    }

    public static float multiplier(ServerPlayer player) {
        double m = levelMultiplier(WipsLevel.level(player));
        if (LevelConfig.DMG_ABILITY_POWER.get()) {
            m *= 1.0 + MasteryScaling.abilityPower(player);
        }
        return (float) m;
    }

    /** Lo que antes hacia la maestria, sin la maestria: balance de la habilidad y bonus propios del golpe (combo, etc.). */
    public static float neutral(float base, String abilityKey, float[] extras) {
        return base * extrasMultiplier(extras) * AbilityBalance.damageMultiplier(abilityKey) * BalanceConfig.damageMultiplier();
    }

    public static float extrasMultiplier(float[] extras) {
        if (BalanceConfig.additiveDamageStacking()) {
            float total = 0;
            for (float e : extras) {
                total += e;
            }
            return 1.0F + total;
        }
        float m = 1.0F;
        for (float e : extras) {
            m *= 1.0F + e;
        }
        return m;
    }

    /** Es un golpe de un poder? Dmg.hurt de Awakened (que marca ABILITY) o los tipos de dano de los poderes. */
    public static boolean isPowerHit(DamageSource source) {
        if (Dmg.inAbilityHit()) {
            return true;
        }
        ResourceLocation type = source.typeHolder().unwrapKey().map(k -> k.location()).orElse(null);
        if (type == null) {
            return false;
        }
        String ns = type.getNamespace();
        return ns.equals(WipsPowers.MOD_ID) || ns.equals("awakened")
                // Seiryuu usa ataques de Grand Line; solo cuentan si el atacante tiene ese poder
                || (ns.equals("devilfruits") && source.getEntity() instanceof ServerPlayer sp && hasSeiryuu(sp));
    }

    private static boolean hasSeiryuu(ServerPlayer sp) {
        var data = com.awakenedmod.awakened.core.PowerService.data(sp);
        return data != null && wips.powers.SeiryuuPower.ID.equals(data.powerId());
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onIncoming(LivingIncomingDamageEvent event) {
        DamageSource src = event.getSource();
        if (!(src.getEntity() instanceof ServerPlayer player) || src.getEntity() == event.getEntity() || !isPowerHit(src)) {
            return;
        }
        float before = event.getAmount();
        float after = before * multiplier(player);
        event.setAmount(after);
        lastBefore = before;
        lastAfter = after;
    }
}
