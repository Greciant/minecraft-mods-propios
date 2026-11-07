package wips.powers.npc;

import com.awakenedmod.awakened.content.pyrokinetic.npc.PyroNpcKit;
import com.awakenedmod.awakened.core.npc.NpcCaster;
import com.awakenedmod.awakened.core.npc.NpcKit;
import com.awakenedmod.awakened.core.npc.NpcMove;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Mago de un poder: misma IA que el de Pyrokinetic de Awakened (se aleja, mantiene distancia, esquiva y
 * ataca), con cuatro tecnicas tematicas que se desbloquean con la maestria (1-25):
 * proyectil (siempre), nova de cerca (5), rayo sostenido (10) y golpe definitivo (16).
 */
public final class ElementalMageKit extends NpcKit {
    private final MageTheme theme;

    public ElementalMageKit(MageTheme theme) {
        super(theme.powerId() + "_mage", theme.powerId());
        this.theme = theme;
    }

    public MageTheme theme() {
        return this.theme;
    }

    @Override
    public ResourceLocation skin() {
        return this.theme.skin();
    }

    @Override
    public List<NpcMove> createMoves() {
        return List.of(new MageMoves.Bolt(this.theme), new MageMoves.Nova(this.theme), new MageMoves.Beam(this.theme),
                new MageMoves.Ultimate(this.theme));
    }

    /** Usa las animaciones de lanzamiento del mago de fuego (lanzar, rayo, rafaga). */
    @Override
    public List<ResourceLocation> clips() {
        return List.of(PyroNpcKit.FIREBALL_R, PyroNpcKit.FIREBALL_L, PyroNpcKit.FLAME_BEAM, PyroNpcKit.BACKDRAFT,
                PyroNpcKit.BARRAGE, PyroNpcKit.NOTICE);
    }

    @Override
    public AttributeSupplier.Builder attributes() {
        return super.attributes().add(Attributes.MAX_HEALTH, 34.0).add(Attributes.ARMOR, 3.0);
    }

    @Override
    public void passiveTick(NpcCaster caster) {
        // aura tenue de su poder mientras camina
        if (caster.level().getGameTime() % 10L == 0L) {
            var npc = caster.npc();
            caster.level().sendParticles(this.theme.dust(0.8F), npc.getX(), npc.getY() + 1.0, npc.getZ(), 2, 0.3, 0.5, 0.3, 0.0);
        }
    }

    @Override
    public double preferredMin() {
        return "scaler".equals(this.theme.powerId()) || "gamma".equals(this.theme.powerId()) || "velocity".equals(this.theme.powerId())
                ? 3.0 : 8.0;
    }

    @Override
    public double preferredMax() {
        return "scaler".equals(this.theme.powerId()) || "gamma".equals(this.theme.powerId()) ? 8.0 : 16.0;
    }
}
