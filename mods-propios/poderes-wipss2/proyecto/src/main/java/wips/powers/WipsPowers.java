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
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.COMMON, wips.powers.level.LevelConfig.SPEC, "wipspowers-nivel.toml");
        wips.powers.level.WipsLevel.ATTACHMENTS.register(modBus);
        modBus.addListener(WipsPowers::registerPayloads);
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient() && net.neoforged.fml.ModList.get().isLoaded("lodestar_trail")) {
            wips.powers.client.compat.CompassTrail.register();
        }
        if (Boolean.getBoolean("wips.audit")) {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStartedEvent e) -> {
                LOGGER.info("[wipspowers] AUDIT: aplicando todos los mixins");
                org.spongepowered.asm.mixin.MixinEnvironment.getCurrentEnvironment().audit();
                var level = e.getServer().overworld();
                int n = 0;
                for (var kit : com.awakenedmod.awakened.core.npc.NpcKits.all()) {
                    var npc = kit.type().get().create(level);
                    LOGGER.info("[wipspowers] AUDIT mago {} -> {} ({} movimientos)", kit.id(), npc == null ? "NULL" : npc.getName().getString(),
                            kit.createMoves().size());
                    n++;
                }
                LOGGER.info("[wipspowers] AUDIT magos: {} | maestria max: {}", n, com.awakenedmod.awakened.core.npc.NpcConfig.maxMastery());
                var hidden = net.minecraft.core.registries.BuiltInRegistries.ITEM.stream().filter(wips.powers.content.ContentFilter::hidden).count();
                LOGGER.info("[wipspowers] AUDIT items ocultos: {} | sniffer biome modifiers y amuletos: {}", hidden,
                        wips.powers.item.WipsItems.STAMINA_CHARM_3.getId());
                var reg = e.getServer().registryAccess();
                LOGGER.info("[wipspowers] AUDIT santuario: {}", reg.registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
                        .containsKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MOD_ID, "ritual_shrine")));
                LOGGER.info("[wipspowers] AUDIT plantilla: {}", e.getServer().getStructureManager()
                        .get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MOD_ID, "ritual_shrine")).map(t -> t.getSize().toString()).orElse("NO"));
                if (net.neoforged.fml.ModList.get().isLoaded("irons_spellbooks")) {
                    wips.powers.compat.AuditSpells.log();
                }
                wips.powers.level.BalanceDump.log();
                LOGGER.info("[wipspowers] AUDIT OK");
                e.getServer().halt(false);
            });
        }
        WipsSounds.SOUNDS.register(modBus);
        wips.powers.item.WipsItems.ITEMS.register(modBus);
        wips.powers.content.ItemPurge.LOOT.register(modBus);
        modBus.addListener(wips.powers.content.ContentFilter::onBuildTabs);
        modBus.addListener(wips.powers.content.LegendaryMonstersFilter::onSpawnPlacements);
        modBus.addListener(wips.powers.content.LegendaryMonstersFilter::onAttributes);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(wips.powers.content.LegendaryMonstersFilter::onJoin);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(wips.powers.content.SnifferSpawns::onJoin);
        modBus.addListener(wips.powers.content.SnifferSpawns::onSpawnPlacements);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(wips.powers.content.LegendaryMonstersFilter::onPositionCheck);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToClient(FormFx.Payload.TYPE, FormFx.Payload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> FormFx.receive(payload)));
        event.registrar("1").optional().playToClient(CastSigil.Payload.TYPE, CastSigil.Payload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> CastSigil.receive(payload)));
        event.registrar("1").optional().playToClient(wips.powers.fx.WipsFx.Payload.TYPE, wips.powers.fx.WipsFx.Payload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> wips.powers.fx.WipsFx.receive(payload)));
        event.registrar("1").optional().playToClient(wips.powers.level.WipsLevel.Sync.TYPE, wips.powers.level.WipsLevel.Sync.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> wips.powers.client.LevelHud.receive(payload)));
        event.registrar("1").optional().playToClient(wips.powers.crit.Criticals.Payload.TYPE, wips.powers.crit.Criticals.Payload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> wips.powers.crit.ClientCrit.receive(payload)));
    }
}
