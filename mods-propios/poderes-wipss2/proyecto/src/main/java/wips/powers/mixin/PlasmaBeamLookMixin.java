package wips.powers.mixin;

import com.devilfruits.client.vfx.VfxFrame;
import com.devilfruits.fruits.uo_uo_seiryu.client.vfx.PlasmaBeamCinematic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Blast Breath: el rayo era blanco puro con mucho bloom. Damos color de fuego al cono exterior,
 * nucleo dorado claro, y achicamos la bola de luz de la boca para que se vea el personaje.
 */
@Mixin(value = PlasmaBeamCinematic.class, remap = false)
public abstract class PlasmaBeamLookMixin {
    private static final int OUTER = 0xFF8A2E;
    private static final int CORE = 0xFFE7B0;
    private static final int LANCE = 0xFFC468;

    @ModifyConstant(method = "render", constant = @Constant(intValue = 0xFFFFFF, ordinal = 0))
    private int wips$outerA(int c) { return OUTER; }

    @ModifyConstant(method = "render", constant = @Constant(intValue = 0xFFFFFF, ordinal = 1))
    private int wips$outerB(int c) { return OUTER; }

    @ModifyConstant(method = "render", constant = @Constant(intValue = 0xFFFFFF, ordinal = 2))
    private int wips$coreA(int c) { return CORE; }

    @ModifyConstant(method = "render", constant = @Constant(intValue = 0xFFFFFF, ordinal = 3))
    private int wips$coreB(int c) { return CORE; }

    @ModifyConstant(method = "render", constant = @Constant(intValue = 0xFFFFFF, ordinal = 4))
    private int wips$lance(int c) { return LANCE; }

    @ModifyConstant(method = "tickBeam", constant = @Constant(floatValue = 2.8F))
    private float wips$smallerMouthGlow(float v) { return 1.5F; }
}
