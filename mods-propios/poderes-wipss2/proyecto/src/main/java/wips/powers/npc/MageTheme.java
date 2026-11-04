package wips.powers.npc;

import com.awakenedmod.awakened.core.Fx;
import java.util.function.BiConsumer;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Como se ve y que hace cada mago: colores, particulas, sonidos y el efecto que deja cada golpe.
 * Un mago por poder obtenible (Pyrokinetic ya lo trae Awakened).
 */
public record MageTheme(
        String powerId,
        int color,
        int accent,
        ParticleOptions trail,
        ParticleOptions burst,
        SoundEvent castSound,
        SoundEvent hitSound,
        @Nullable Holder<MobEffect> effect,
        int effectTicks,
        int effectAmp,
        double knockback,
        ResourceLocation skin,
        @Nullable BiConsumer<LivingEntity, Vec3> onHit) {

    public ParticleOptions dust(float size) {
        return Fx.dust(this.color, size);
    }

    public ParticleOptions accentDust(float size) {
        return Fx.dust(this.accent, size);
    }

    public void apply(LivingEntity victim, Vec3 from, float strength) {
        if (this.effect != null) {
            victim.addEffect(new MobEffectInstance(this.effect, Math.round(this.effectTicks * strength), this.effectAmp));
        }
        if (this.onHit != null) {
            this.onHit.accept(victim, from);
        }
    }

    private static ResourceLocation skin(String name) {
        return ResourceLocation.withDefaultNamespace("textures/entity/player/wide/" + name + ".png");
    }

    public static MageTheme[] all() {
        return new MageTheme[]{
                new MageTheme("summoning", 0x3FE0C8, 0x9B5CFF, ParticleTypes.PORTAL, ParticleTypes.REVERSE_PORTAL,
                        SoundEvents.EVOKER_PREPARE_SUMMON, SoundEvents.ENDERMAN_TELEPORT, MobEffects.WEAKNESS, 80, 0, 0.5,
                        skin("zuri"), null),
                new MageTheme("hydrokinetic", 0x2E8BFF, 0xA8E4FF, ParticleTypes.SPLASH, ParticleTypes.BUBBLE_POP,
                        SoundEvents.TRIDENT_RIPTIDE_1.value(), SoundEvents.PLAYER_SPLASH_HIGH_SPEED, MobEffects.MOVEMENT_SLOWDOWN, 60, 1, 0.9,
                        skin("noor"), (v, f) -> v.clearFire()),
                new MageTheme("kinetic_charger", 0x9B5CFF, 0xE0C8FF, ParticleTypes.WITCH, ParticleTypes.SONIC_BOOM,
                        SoundEvents.BREEZE_CHARGE, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), null, 0, 0, 1.6,
                        skin("kai"), null),
                new MageTheme("velocity", 0xFF3B30, 0xFFC0B8, ParticleTypes.CRIT, ParticleTypes.SWEEP_ATTACK,
                        SoundEvents.TRIDENT_RIPTIDE_3.value(), SoundEvents.PLAYER_ATTACK_SWEEP, MobEffects.MOVEMENT_SLOWDOWN, 30, 0, 1.0,
                        skin("efe"), null),
                new MageTheme("graviton", 0x5A2A8A, 0x1A0A2A, ParticleTypes.REVERSE_PORTAL, ParticleTypes.SQUID_INK,
                        SoundEvents.WARDEN_SONIC_CHARGE, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), MobEffects.LEVITATION, 30, 0, -0.6,
                        skin("ari"), null),
                new MageTheme("ruin", 0x8F8F86, 0x3D3A35, ParticleTypes.ASH, ParticleTypes.LARGE_SMOKE,
                        SoundEvents.SCULK_BLOCK_SPREAD, SoundEvents.WITHER_SHOOT, MobEffects.WITHER, 60, 0, 0.5,
                        skin("makena"), null),
                new MageTheme("storm_caller", 0xFFE14D, 0x7FD4FF, ParticleTypes.ELECTRIC_SPARK, ParticleTypes.FLASH,
                        SoundEvents.TRIDENT_THUNDER.value(), SoundEvents.LIGHTNING_BOLT_IMPACT, MobEffects.MOVEMENT_SLOWDOWN, 20, 3, 0.7,
                        skin("sunny"), null),
                new MageTheme("phytokinetic", 0x4CAF50, 0xC5F27A, ParticleTypes.HAPPY_VILLAGER, ParticleTypes.SPORE_BLOSSOM_AIR,
                        SoundEvents.AZALEA_LEAVES_BREAK, SoundEvents.ROOTED_DIRT_BREAK, MobEffects.POISON, 80, 0, 0.4,
                        skin("alex"), null),
                new MageTheme("scaler", 0x5BE0A8, 0xC0FFE8, ParticleTypes.CLOUD, ParticleTypes.EXPLOSION,
                        SoundEvents.RAVAGER_ROAR, SoundEvents.MACE_SMASH_GROUND_HEAVY, MobEffects.MOVEMENT_SLOWDOWN, 40, 1, 1.4,
                        skin("steve"), null),
                new MageTheme("gamma", 0x76E03A, 0xD8FF7A, ParticleTypes.GLOW, ParticleTypes.EXPLOSION,
                        SoundEvents.RAVAGER_ROAR, SoundEvents.GENERIC_EXPLODE.value(), MobEffects.CONFUSION, 80, 0, 1.5,
                        skin("kai"), null),
                new MageTheme("seiryuu", 0x4DB8FF, 0xFF8A2A, ParticleTypes.FLAME, ParticleTypes.ELECTRIC_SPARK,
                        SoundEvents.ENDER_DRAGON_GROWL, SoundEvents.LIGHTNING_BOLT_THUNDER, null, 0, 0, 1.0,
                        skin("efe"), (v, f) -> v.igniteForSeconds(4.0F)),
        };
    }
}
