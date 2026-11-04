package wips.powers;

import com.awakenedmod.awakened.core.power.Ability;
import com.awakenedmod.awakened.core.power.Power;
import com.devilfruits.core.form.AbilityModifiers;
import com.devilfruits.core.form.FormAbility;
import com.devilfruits.fruits.uo_uo_seiryu.SeiryuAbilities;
import com.devilfruits.fruits.uo_uo_seiryu.UoUoSeiryu;
import java.util.List;
import wips.powers.bridge.GlBridgeAbility;

/** Poder nuevo: 5 tecnicas de la Uo Uo no Mi, modelo Seiryuu, siempre en forma humana. */
public final class SeiryuuPower extends Power {
    public static final String ID = "seiryuu";

    public SeiryuuPower() {
        super(ID, List.<Ability>of(new ThunderBagua(), new FlameCloud(), new DraconicThunder(), new BoloBarrage(), new BlastBreath()));
    }

    @Override
    public int themeColor() {
        return 0xFF4DB8FF;
    }

    @Override
    public int signatureSlot() {
        return 0;
    }

    public static final class ThunderBagua extends GlBridgeAbility {
        public ThunderBagua() {
            super("seiryuu_thunder_bagua", 25.0F, 0, new FormAbility(SeiryuAbilities.THUNDER_BAGUA,
                    AbilityModifiers.variant("hybrid").damage(2.5F).range(1.8F).knockback(1.5F).energyCost(1.4F)
                            .animation(UoUoSeiryu.anim("hybrid/thunder_bagua")).build()), "seiryuu_thunder_bagua", "hybrid");
        }
    }

    public static final class FlameCloud extends GlBridgeAbility {
        public FlameCloud() {
            super("seiryuu_flame_cloud", 20.0F, 2, new FormAbility(SeiryuAbilities.FLAME_CLOUD), "seiryuu_flame_cloud");
        }
    }

    public static final class DraconicThunder extends GlBridgeAbility {
        public DraconicThunder() {
            super("seiryuu_draconic_thunder", 30.0F, 5, new FormAbility(SeiryuAbilities.DRACONIC_THUNDERFALL), "seiryuu_draconic_thunder", "hybrid");
        }
    }

    public static final class BoloBarrage extends GlBridgeAbility {
        public BoloBarrage() {
            super("seiryuu_bolo_barrage", 40.0F, 8, new FormAbility(SeiryuAbilities.BOLO_BREATH,
                    AbilityModifiers.variant("dragon").damage(2.5F).range(2.0F).energyCost(1.4F)
                            .animation(UoUoSeiryu.anim("dragon/bolo_breath")).build()), "seiryuu_bolo_barrage", "dragon");
        }

        /** Forma dragon completa: carga 3 s (la de Grand Line es 1,2 s). Lo masivo esta en BoloDragonMixin. */
        @Override
        protected int chargeTicks() {
            return 60;
        }
    }

    public static final class BlastBreath extends GlBridgeAbility {
        public BlastBreath() {
            super("seiryuu_blast_breath", 45.0F, 10, new FormAbility(SeiryuAbilities.BLAST_BREATH,
                    AbilityModifiers.variant("dragon").damage(3.5F).range(3.0F).energyCost(1.6F)
                            .animation(UoUoSeiryu.anim("dragon/blast_breath")).build()), "seiryuu_blast_breath", "hybrid");
        }
    }

    public static final class IslandShaker extends GlBridgeAbility {
        public IslandShaker() {
            super("gamma_island_shaker", 35.0F, 10, new FormAbility(com.devilfruits.fruits.gura_gura.GuraAbilities.SHIMA_YURASHI), "gamma_island_shaker");
        }

        @Override
        protected int fixedCooldown() {
            return 500; // 25 s
        }
    }
}
