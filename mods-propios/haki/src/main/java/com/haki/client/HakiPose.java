package com.haki.client;

import com.haki.HakiVisuals;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Pose de lanzamiento del Haki: brazos abiertos y algo adelantados, cabeza hacia atras, pecho arqueado,
 * piernas separadas y un temblor leve. Se mezcla con la animacion normal segun la opacidad del efecto del lanzador,
 * asi entra y sale suave. Solo modifica las partes del ModelPart del modelo (no cambia nada del juego).
 */
public final class HakiPose {
    private HakiPose() {}

    public static void apply(PlayerModel<?> m, LivingEntity entity, float ageInTicks) {
        if (!HakiVisuals.POSE_ENABLED || HakiClientFx.CASTERS.isEmpty() || !(entity instanceof Player)) return;
        HakiClientFx.Caster c = HakiClientFx.CASTERS.get(entity.getId());
        if (c == null) return;
        // Con Player Animation Library la pose la pone su capa haki:cast; con aurachad en modo chad manda su pose
        if (com.haki.client.pal.HakiPalBridge.drives(entity) || HakiCompat.chadActive(entity)) return;
        float pt = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        float b = HakiFxRender.smooth(c.prevFade + (c.fade - c.prevFade) * pt);
        if (b <= 0.001f) return;
        float t = ageInTicks * HakiVisuals.POSE_RUMBLE_SPEED;
        float rumble = HakiVisuals.POSE_RUMBLE * b;
        // El signo de zRot es opuesto en cada brazo: el derecho se abre con +, el izquierdo con -
        m.rightArm.zRot = Mth.lerp(b, m.rightArm.zRot, HakiVisuals.POSE_ARM_SPREAD) + rumble * Mth.sin(t * 2.1f);
        m.leftArm.zRot = Mth.lerp(b, m.leftArm.zRot, -HakiVisuals.POSE_ARM_SPREAD) + rumble * Mth.sin(t * 2.3f + 1.7f);
        m.rightArm.xRot = Mth.lerp(b, m.rightArm.xRot, HakiVisuals.POSE_ARM_FORWARD) + rumble * Mth.sin(t * 1.7f + 0.4f);
        m.leftArm.xRot = Mth.lerp(b, m.leftArm.xRot, HakiVisuals.POSE_ARM_FORWARD) + rumble * Mth.sin(t * 1.9f + 2.2f);
        m.rightArm.yRot = Mth.lerp(b, m.rightArm.yRot, 0f);
        m.leftArm.yRot = Mth.lerp(b, m.leftArm.yRot, 0f);
        m.head.xRot = Mth.lerp(b, m.head.xRot, m.head.xRot + HakiVisuals.POSE_HEAD_BACK);
        m.body.xRot = Mth.lerp(b, m.body.xRot, HakiVisuals.POSE_BODY_LEAN);
        m.rightLeg.zRot = Mth.lerp(b, m.rightLeg.zRot, HakiVisuals.POSE_LEG_SPREAD);
        m.leftLeg.zRot = Mth.lerp(b, m.leftLeg.zRot, -HakiVisuals.POSE_LEG_SPREAD);
        // La capa exterior de la skin copia las partes: hay que volver a copiarlas
        m.hat.copyFrom(m.head);
        m.jacket.copyFrom(m.body);
        m.leftSleeve.copyFrom(m.leftArm);
        m.rightSleeve.copyFrom(m.rightArm);
        m.leftPants.copyFrom(m.leftLeg);
        m.rightPants.copyFrom(m.rightLeg);
    }
}
