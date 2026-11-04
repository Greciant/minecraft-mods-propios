package com.aurachad.client.bend;

import java.util.Map;
import java.util.WeakHashMap;

import com.aurachad.client.ChadClient;
import io.github.kosmx.bendylib.ModelPartAccessor;
import io.github.kosmx.bendylib.MutableCuboid;
import io.github.kosmx.bendylib.impl.BendableCuboid;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;

/**
 * Codos y rodillas con bendy-lib (KosmX). Unica clase que importa bendy-lib. Se registra un mutator propio ("aurachad_bend"),
 * distinto del "bend" de playerAnimator, para no pisarlo; solo se activa mientras la entidad esta en modo chad.
 */
final class ChadBend {
    private ChadBend() {}

    static final String MUTATOR = "aurachad_bend";
    private static final String[] BONES = {"right_arm", "left_arm", "right_leg", "left_leg"};
    private static final float[] TMP = new float[2];
    /** Modelos que pueden tener un mutator nuestro activo; el modelo se comparte entre jugadores. */
    private static final Map<PlayerModel<?>, Boolean> DIRTY = new WeakHashMap<>();

    /** Parte principal y su capa exterior (manga / pantalon). */
    private static ModelPart[] parts(PlayerModel<?> m, int bone) {
        return switch (bone) {
            case 0 -> new ModelPart[]{m.rightArm, m.rightSleeve};
            case 1 -> new ModelPart[]{m.leftArm, m.leftSleeve};
            case 2 -> new ModelPart[]{m.rightLeg, m.rightPants};
            default -> new ModelPart[]{m.leftLeg, m.leftPants};
        };
    }

    static void apply(PlayerModel<?> m, LivingEntity entity) {
        float age = ChadClient.ageSeconds(entity.getId());
        if (age < 0f) {
            if (DIRTY.remove(m) != null) for (int b = 0; b < 4; b++) for (ModelPart p : parts(m, b)) deactivate(p);
            return;
        }
        DIRTY.put(m, Boolean.TRUE);
        for (int b = 0; b < 4; b++) {
            ChadBendTrack.sample(BONES[b], age, TMP);
            for (ModelPart p : parts(m, b)) {
                if (Math.abs(TMP[0]) < 1e-3f) { deactivate(p); continue; }
                BendableCuboid c = cuboid(p);
                if (c != null) c.applyBend(TMP[1], TMP[0]);
            }
        }
    }

    private static BendableCuboid cuboid(ModelPart p) {
        MutableCuboid mc = ModelPartAccessor.optionalGetCuboid(p, 0).orElse(null);
        if (mc == null) return null;
        // Los brazos, piernas, mangas y pantalones se extienden hacia abajo en el modelo: la direccion de bend es UP (como playerAnimator)
        mc.registerMutator(MUTATOR, data -> new BendableCuboid.Builder().setDirection(Direction.UP).build(data));
        return mc.getAndActivateMutator(MUTATOR) instanceof BendableCuboid bc ? bc : null;
    }

    private static void deactivate(ModelPart p) {
        MutableCuboid mc = ModelPartAccessor.optionalGetCuboid(p, 0).orElse(null);
        if (mc != null && mc.getActiveMutator() != null && MUTATOR.equals(mc.getActiveMutator().getA())) mc.getAndActivateMutator(null);
    }

    /**
     * Direccion del antebrazo/pierna inferior en espacio del personaje (derecha, arriba, frente), medida sobre la geometria real
     * del cubo doblado y la rotacion actual de la parte. null si no hay mutator activo. Para el autotest.
     */
    static float[] measure(PlayerModel<?> m, String bone) {
        int b = java.util.Arrays.asList(BONES).indexOf(bone);
        ModelPart p = parts(m, b)[0];
        MutableCuboid mc = ModelPartAccessor.optionalGetCuboid(p, 0).orElse(null);
        if (mc == null || mc.getActiveMutator() == null || !(mc.getActiveMutator().getB() instanceof BendableCuboid bc)) return null;
        Vector3f hand = new Vector3f(), shoulder = new Vector3f();
        int[] n = {0, 0};
        bc.iteratePositions(pos -> {
            Vector3f o = pos.getOriginalPos();
            if (o.y > bc.maxY - 0.01f) { hand.add(pos.getPos()); n[0]++; }
            else if (o.y < bc.minY + 0.01f) { shoulder.add(pos.getPos()); n[1]++; }
        });
        hand.div(n[0]); shoulder.div(n[1]);
        Vector3f elbow = new Vector3f(bc.getBendX(), bc.getBendY(), bc.getBendZ());
        Vector3f f = hand.sub(elbow);
        var q = new org.joml.Quaternionf().rotationZYX(p.zRot, p.yRot, p.xRot);   // igual que ModelPart.translateAndRotate
        q.transform(f);
        f.normalize();
        // Se miden tambien el brazo superior para comprobar el angulo del codo
        Vector3f u = elbow.sub(shoulder, new Vector3f()); q.transform(u); u.normalize();
        return new float[]{-f.x, -f.y, -f.z, -u.x, -u.y, -u.z, (float) Math.toDegrees(Math.acos(Math.max(-1f, Math.min(1f, f.dot(u)))))};
    }

    /** Descripcion de la parte para el autotest cuando no hay bend: clase, numero de cubos y mutator activo. */
    static String describe(PlayerModel<?> m, String bone) {
        ModelPart p = parts(m, java.util.Arrays.asList(BONES).indexOf(bone))[0];
        var cubes = ModelPartAccessor.getCuboids(p);
        MutableCuboid mc = ModelPartAccessor.optionalGetCuboid(p, 0).orElse(null);
        return p.getClass().getName() + " cubos=" + (cubes == null ? -1 : cubes.size()) + " mutator=" + (mc == null ? "-" : mc.hasMutator(MUTATOR) + "/" + (mc.getActiveMutator() == null ? "inactivo" : mc.getActiveMutator().getA()));
    }
}
