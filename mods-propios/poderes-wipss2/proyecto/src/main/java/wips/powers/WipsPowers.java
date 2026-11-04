package wips.powers;

import java.util.Set;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@Mod(WipsPowers.MOD_ID)
public final class WipsPowers {
    public static final String MOD_ID = "wipspowers";
    public static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    /** Poderes de Awakened que se conservan; el resto se oculta del sorteo y de las pantallas. */
    public static final Set<String> KEEP = Set.of(
            "pyrokinetic", "summoning", "hydrokinetic", "kinetic_charger", "velocity",
            "graviton", "ruin", "storm_caller", "phytokinetic", "scaler", "gamma",
            SeiryuuPower.ID);

    public WipsPowers(IEventBus modBus, net.neoforged.fml.ModContainer container) {
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.COMMON, wips.powers.crit.CritConfig.SPEC);
        modBus.addListener(WipsPowers::registerPayloads);
        WipsSounds.SOUNDS.register(modBus);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToClient(FormFx.Payload.TYPE, FormFx.Payload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> FormFx.receive(payload)));
        event.registrar("1").optional().playToClient(CastSigil.Payload.TYPE, CastSigil.Payload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> CastSigil.receive(payload)));
        event.registrar("1").optional().playToClient(wips.powers.fx.WipsFx.Payload.TYPE, wips.powers.fx.WipsFx.Payload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> wips.powers.fx.WipsFx.receive(payload)));
        event.registrar("1").optional().playToClient(wips.powers.crit.Criticals.Payload.TYPE, wips.powers.crit.Criticals.Payload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> wips.powers.crit.ClientCrit.receive(payload)));
    }
}
