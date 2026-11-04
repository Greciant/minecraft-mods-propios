package com.haki;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/** Estado "Haki del Rey": inmoviliza, anula salto y aplasta contra el suelo. */
public class HakiKingEffect extends MobEffect {

    public HakiKingEffect() {
        super(MobEffectCategory.HARMFUL, 0x8B0000);
        addAttributeModifier(Attributes.MOVEMENT_SPEED, id("speed"), -1.0,
            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(Attributes.FLYING_SPEED, id("fly"), -1.0,
            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(Attributes.JUMP_STRENGTH, id("jump"), -1.0,
            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(Attributes.ATTACK_SPEED, id("attack"), -1.0,
            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        // Gravedad x3: sensacion de peso / presion
        addAttributeModifier(Attributes.GRAVITY, id("gravity"), 2.0,
            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(HakiMod.MODID, "haki_king_" + path);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true; // cada tick
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity instanceof Mob mob) {
            mob.getNavigation().stop();
            mob.setTarget(null);
        }
        // Sin movimiento horizontal, y empujado hacia el suelo
        Vec3 v = entity.getDeltaMovement();
        if (entity instanceof Mob mob && mob.isNoAi()) {
            // Vanilla no ejecuta travel() en mobs sin IA (no caen), asi que la
            // caida con gravedad x3 se aplica a mano, solo en el servidor.
            if (!mob.level().isClientSide) {
                double vy = mob.onGround() ? 0.0 : (Math.min(v.y, 0.0) - mob.getGravity()) * 0.98;
                mob.setDeltaMovement(0.0, vy, 0.0);
                if (vy != 0.0) mob.move(MoverType.SELF, mob.getDeltaMovement());
            }
            return true;
        }
        entity.setDeltaMovement(0.0, Math.min(v.y, 0.0) - 0.1, 0.0);
        entity.hurtMarked = true; // sincroniza la velocidad con el cliente
        return true;
    }
}
