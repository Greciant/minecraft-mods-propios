package wips.powers.content;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.animal.sniffer.Sniffer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;

/**
 * Sniffers naturales (data/wipspowers/neoforge/biome_modifier/sniffer_*): salen como cualquier animal pasivo, con
 * peso bajo (3, grupos de 1-2), en casi todos los biomas con pasto, desiertos, badlands y el bosque carmesi. Cada
 * uno nace con la variante de Sniffer Overhaul de su bioma (la misma que daria alimentarlo con el item del bioma).
 */
public final class SnifferSpawns {
    private static final String ROLLED = "wipspowers:variant_rolled";
    private static final Map<ResourceKey<Biome>, String> VARIANT = Map.ofEntries(
            Map.entry(Biomes.SAVANNA, "sniffer_acacia"), Map.entry(Biomes.SAVANNA_PLATEAU, "sniffer_acacia"),
            Map.entry(Biomes.WINDSWEPT_SAVANNA, "sniffer_acacia"),
            Map.entry(Biomes.OLD_GROWTH_PINE_TAIGA, "sniffer_ancient"), Map.entry(Biomes.OLD_GROWTH_SPRUCE_TAIGA, "sniffer_ancient"),
            Map.entry(Biomes.CHERRY_GROVE, "sniffer_cherry"),
            Map.entry(Biomes.SNOWY_PLAINS, "sniffer_cold"), Map.entry(Biomes.SNOWY_TAIGA, "sniffer_cold"),
            Map.entry(Biomes.ICE_SPIKES, "sniffer_cold"), Map.entry(Biomes.GROVE, "sniffer_cold"),
            Map.entry(Biomes.DESERT, "sniffer_desert_biome"),
            Map.entry(Biomes.BADLANDS, "sniffer_desert_dungeon"), Map.entry(Biomes.WOODED_BADLANDS, "sniffer_desert_dungeon"),
            Map.entry(Biomes.ERODED_BADLANDS, "sniffer_desert_dungeon"),
            Map.entry(Biomes.JUNGLE, "sniffer_jungle"), Map.entry(Biomes.SPARSE_JUNGLE, "sniffer_jungle"),
            Map.entry(Biomes.BAMBOO_JUNGLE, "sniffer_jungle"),
            Map.entry(Biomes.MUSHROOM_FIELDS, "sniffer_mushroom"),
            Map.entry(Biomes.CRIMSON_FOREST, "sniffer_nether_crismon_forest"),
            Map.entry(Biomes.SWAMP, "sniffer_swamp"), Map.entry(Biomes.MANGROVE_SWAMP, "sniffer_swamp"),
            Map.entry(Biomes.END_HIGHLANDS, "sniffer_ender"), Map.entry(Biomes.END_MIDLANDS, "sniffer_ender"));

    private SnifferSpawns() {
    }

    /** Variante segun el bioma, una sola vez por sniffer (los nacidos de huevo tambien la toman). */
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || event.loadedFromDisk() || !(event.getEntity() instanceof Sniffer sniffer)) {
            return;
        }
        CompoundTag data = sniffer.getPersistentData();
        if (data.getBoolean(ROLLED) || !data.getString("VariantTexture").isEmpty()) {
            return;
        }
        data.putBoolean(ROLLED, true);
        Holder<Biome> biome = event.getLevel().getBiome(sniffer.blockPosition());
        biome.unwrapKey().map(VARIANT::get).ifPresent(v -> data.putString("VariantTexture", v));
    }

    /** Pueden salir sobre cualquier bloque firme con luz (arena, terracota, micelio, nylium), no solo pasto. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void onSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(EntityType.SNIFFER, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (type, level, reason, pos, random) -> {
                    BlockPos below = pos.below();
                    boolean firm = level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
                    boolean nether = level instanceof Level l ? l.dimension() == Level.NETHER
                            : level.getLevel().dimension() == Level.NETHER;
                    return firm && (nether || level.getRawBrightness(pos, 0) > 8);
                }, RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }
}
