package wips.powers.scaler;

import com.awakenedmod.awakened.content.scaler.ScalerFx;
import com.awakenedmod.awakened.content.scaler.ScalerPower;
import com.awakenedmod.awakened.core.PowerService;
import com.awakenedmod.awakened.core.data.PowerData;
import com.awakenedmod.awakened.core.progression.MasteryScaling;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import wips.powers.WipsPowers;
import wips.powers.fx.WipsFx;
import wips.powers.terrain.WipsTerrain;

/**
 * Ondas expansivas del Scaler que hacen dano:
 * - al crecer a Titan o mas, la onda de los pies;
 * - en Colossus o mas, al caer desde altura: onda grande con dano = bloques de caida;
 * - Mass Leap: al aterrizar, onda GIGANTE que crece con la altura de la caida y el tamano;
 * - Stomp: radio, dano y crater escalan con el tamano (ver ScalerStompMixin).
 */
@EventBusSubscriber(modid = WipsPowers.MOD_ID)
public final class ScalerWaves {
    /** Altura mas alta alcanzada en el Mass Leap en curso. */
    public static final Map<UUID, Double> LEAP_PEAK = new ConcurrentHashMap<>();

    private ScalerWaves() {
    }

    static boolean isScaler(ServerPlayer player) {
        PowerData data = PowerService.data(player);
        return data.awakened() && ScalerPower.ID.equals(data.powerId());
    }

    /** Al crecer: si la nueva escala es Titan (x3) o mas, la onda de los pies golpea. */
    public static void growWave(ServerPlayer player, double scale) {
        if (scale < 3.0) {
            return;
        }
        double radius = Math.min(40.0, 2.5 + 1.6 * scale);
        float damage = MasteryScaling.scaledDamage((float) (2.0 + 2.0 * scale), player, "size_change");
        ScalerFx.shockwave(player, player.position(), radius, damage, 0.6 + 0.04 * scale, 0.3 + 0.03 * scale, (float) scale);
    }

    /** Stomp escalado: devuelve {radio, dano} segun el tamano real; conserva la parte que venia de la altura. */
    public static double stompRadius(double givenRadius, float stageScale, float scale) {
        double base = stageScale >= 5.0F ? 8.0 : (stageScale >= 3.0F ? 6.0 : 4.0);
        double heightPart = givenRadius / base;
        return Math.min(56.0, (2.0 + 1.6 * scale) * heightPart);
    }

    public static float stompDamage(float givenDamage, float stageScale, float scale) {
        // givenDamage ya trae maestria y altura; se reescala de la base de la fase a la del tamano real
        float base = stageScale >= 5.0F ? 12.0F : (stageScale >= 3.0F ? 8.0F : 5.0F);
        return givenDamage / base * (2.0F + 3.0F * scale);
    }

    /** Crater del stomp / aterrizaje: mas grande y hondo cuanto mas grande el personaje. */
    public static void crater(ServerPlayer player, double radius, float scale) {
        if (!WipsTerrain.enabled() || scale < 2.0F) {
            return;
        }
        ServerLevel level = player.serverLevel();
        double r = Math.min(radius * 0.45, 22.0);
        int depth = Mth.clamp(Math.round(scale / 2.5F), 1, 7);
        double keep = Math.max(0.0, player.getBbWidth() * 0.5 - 0.5); // no se hunde el propio jugador
        BlockPos c = player.blockPosition().below();
        List<BlockPos> blocks = new ArrayList<>();
        int ri = Mth.ceil(r);
        for (int dx = -ri; dx <= ri; dx++) {
            for (int dz = -ri; dz <= ri; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > r || d < keep) {
                    continue;
                }
                int dd = (int) Math.round(depth * (1.0 - (d / r) * (d / r)));
                for (int y = 0; y < Math.max(1, dd); y++) {
                    blocks.add(c.offset(dx, -y, dz));
                }
            }
        }
        WipsTerrain.crumble(level, player, blocks, 400, 6.0F, 6);
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !isScaler(player)) {
            return;
        }
        float scale = player.getScale();
        float height = event.getDistance();
        if (scale < 5.0F || height < 4.0F) {
            return;
        }
        // altura real del salto: la distancia de caida en bloques del mundo
        double radius = Math.min(48.0, 6.0 + 1.2 * scale + height * 0.4);
        float damage = MasteryScaling.scaledDamage(height, player, "mass_leap");
        ScalerFx.shockwave(player, player.position(), radius, damage, 1.0, 0.6, scale);
        WipsFx.around(player, "scaler_shift", player.position(), Vec3.ZERO, scale, player.getRandom().nextLong(), 1);
        crater(player, radius * 0.6, scale);
    }

    /** Mass Leap toco el suelo. */
    public static void leapLanded(ServerPlayer player) {
        Double peak = LEAP_PEAK.remove(player.getUUID());
        float scale = player.getScale();
        double drop = peak == null ? 0.0 : Math.max(0.0, peak - player.getY());
        double radius = Math.min(64.0, 10.0 + 2.5 * scale + drop * 0.5);
        float damage = MasteryScaling.scaledDamage((float) (8.0 + drop + 2.0 * scale), player, "mass_leap");
        ScalerFx.shockwave(player, player.position(), radius, damage, 1.4, 0.9, Math.max(scale, 3.0F));
        WipsFx.around(player, "scaler_shift", player.position(), Vec3.ZERO, Math.max(scale, 3.0F) * 1.5F, player.getRandom().nextLong(), 1);
        crater(player, radius * 0.5, scale);
    }
}
