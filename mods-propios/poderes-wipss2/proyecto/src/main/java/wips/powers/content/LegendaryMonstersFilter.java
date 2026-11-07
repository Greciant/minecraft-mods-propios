package wips.powers.content;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

/**
 * Legendary Monsters recortado: quedan solo 15 mobs (Shulker Mimic, Stratling, Fractured, Bomber, Resurrected Knight,
 * Flameborn Warrior/Guard, Hovering Hurricane, Flame Drifter, Annihilation Pursuer, Fractured Apostle, The Obliterator,
 * Cumulonimbus y Cloud Golem, Possessed Paladin). El resto no aparece por ningun medio (estructura, huevo, comando)
 * ni en el creativo. Sin sus estructuras, los que quedan salen por bioma (data/wipspowers/neoforge/biome_modifier/lm_*)
 * con stats bajados para ser enemigos comunes. Solo queda la estructura del Obliterator (estacion del End).
 */
public final class LegendaryMonstersFilter {
    public static final String MOD = "legendary_monsters";

    /** Items que siguen: drops de los 15 mobs, lo que se fabrica con ellos, bloques de la estacion del End y huevos. */
    private static final Set<String> KEEP_ITEMS = Set.of("air_rune", "ancient_sandstone_shard", "annihilation_lantern", "annihilation_pursuer_spawn_egg", "annihilator_boots", "annihilator_chestplate", "annihilator_helmet", "annihilator_leggings", "annihilator_upgrade_smithing_template", "atmospheric_boots", "atom_splitter", "axe_of_lightning", "bomber_spawn_egg", "bottle_of_annihilation", "buckler_of_annihilation", "chiseled_enderstone_bricks", "chiseled_enderstone_pillar", "cloud_golem_spawn_egg", "cloud_rod", "cobbled_enderstone", "cobbled_enderstone_slab", "cobbled_enderstone_stairs", "cobbled_enderstone_wall", "corrupted_soul", "dark_cloud_golem_spawn_egg", "ender_anchor", "enderitium_axe", "enderitium_block", "enderitium_gem", "enderitium_hoe", "enderitium_ingot", "enderitium_ore", "enderitium_pickaxe", "enderitium_shovel", "enderitium_sickle", "enderitium_sword", "enderitium_upgrade_smithing_template", "enderstone", "enderstone_brick_slab", "enderstone_brick_stairs", "enderstone_brick_wall", "enderstone_bricks", "enderstone_pillar", "enderstone_slab", "enderstone_stairs", "enderstone_wall", "eye_crystal", "eye_of_annihilation", "flame_drifter_spawn_egg", "flameborn_guard_spawn_egg", "flameborn_warrior_spawn_egg", "fractured_apostle_spawn_egg", "fractured_spawn_egg", "guide_book", "hovering_hurricane_spawn_egg", "large_shulker_shell", "metal_debris", "music_disc_obliterator", "music_disc_possessed_paladin", "portal_shard", "posessed_paladin_spawn_egg", "raw_enderitium", "resurrected_javelin", "resurrected_knight_spawn_egg", "shattered_greatsword", "shulker_helmet", "shulker_mimic_spawn_egg", "shulker_shield", "smooth_enderstone", "smooth_enderstone_slab", "smooth_enderstone_stairs", "smooth_enderstone_wall", "soul_great_sword", "stratling_spawn_egg", "teleport_machine", "the_obliterator_spawn_egg", "the_tesseract", "wand_of_clouds");

    /** Mobs que se quitan del todo (los proyectiles y efectos de los que quedan no se tocan). */
    private static final Set<String> REMOVED = Set.of(
            "skeletosaurus", "skeloraptor", "mossy_golem", "overgrown_colossus", "warped_fungussus", "frostbitten_golem",
            "lava_eater", "endersent", "chorusling", "ambusher", "spiky_bug", "withered_abomination", "ancient_guardian",
            "knights_armor", "guard", "haunted_knight", "haunted_guard", "dune_sentinel", "beheaded_knight", "wandering_eye");

    /** Nuevas stats base: {vida, dano, armadura}. -1 = sin cambio. */
    private static final Map<String, double[]> STATS = Map.ofEntries(
            Map.entry("resurrected_knight", new double[]{50, 7, 8}),
            Map.entry("fractured", new double[]{30, 6, 6}),
            Map.entry("fractured_apostle", new double[]{40, 7, 6}),
            Map.entry("stratling", new double[]{24, 4, 3}),
            Map.entry("hovering_hurricane", new double[]{26, 5, 3}),
            Map.entry("flameborn_warrior", new double[]{35, 7, 4}),
            Map.entry("flameborn_guard", new double[]{40, 7, 6}),
            Map.entry("flame_drifter", new double[]{40, 6, 6}),
            Map.entry("annihilation_pursuer", new double[]{80, 8, 10}),
            Map.entry("shulker_mimic", new double[]{70, 8, 10}),
            Map.entry("cloud_golem", new double[]{150, -1, 8}));

    private static final Set<String> GROUND = Set.of("bomber", "fractured", "fractured_apostle", "resurrected_knight", "posessed_paladin",
            "flameborn_warrior", "flameborn_guard", "annihilation_pursuer", "shulker_mimic");
    private static final Set<String> FLYING = Set.of("stratling", "hovering_hurricane", "cloud_golem", "flame_drifter");

    private LegendaryMonstersFilter() {
    }

    public static boolean hiddenItem(Item item, String path) {
        return !KEEP_ITEMS.contains(path);
    }

    public static boolean hiddenEntity(EntityType<?> type) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return id.getNamespace().equals(MOD) && REMOVED.contains(id.getPath());
    }

    private static Optional<EntityType<?>> type(String path) {
        return BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.fromNamespaceAndPath(MOD, path));
    }

    /** Juego: ningun mob quitado entra al mundo (estructuras viejas, spawners, comandos). */
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && hiddenEntity(event.getEntity().getType())) {
            event.setCanceled(true);
        }
    }

    /** Altura y rareza de los spawns naturales. */
    public static void onPositionCheck(MobSpawnEvent.PositionCheck event) {
        if (event.getSpawnType() != MobSpawnType.NATURAL && event.getSpawnType() != MobSpawnType.CHUNK_GENERATION) {
            return;
        }
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType());
        if (!id.getNamespace().equals(MOD)) {
            return;
        }
        LevelAccessor level = event.getLevel();
        BlockPos pos = event.getEntity().blockPosition();
        double y = event.getY();
        boolean ok = switch (id.getPath()) {
            case "stratling", "hovering_hurricane" -> y >= 110;
            case "cloud_golem" -> y >= 140 && level.getRandom().nextFloat() < 0.08F;
            case "resurrected_knight" -> y < 40 && !level.canSeeSky(pos);
            case "posessed_paladin" -> y < 0 && !level.canSeeSky(pos) && level.getRandom().nextFloat() < 0.03F;
            case "annihilation_pursuer", "shulker_mimic" -> level.getRandom().nextFloat() < 0.5F;
            default -> true;
        };
        if (!ok) {
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void onSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        for (String g : GROUND) {
            type(g).ifPresent(t -> event.register((EntityType) t, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    (net.minecraft.world.entity.SpawnPlacements.SpawnPredicate) (et, lvl, reason, p, rnd) -> Monster.checkMonsterSpawnRules((EntityType<? extends Monster>) et, (net.minecraft.world.level.ServerLevelAccessor) lvl, reason, p, rnd),
                    RegisterSpawnPlacementsEvent.Operation.REPLACE));
        }
        for (String f : FLYING) {
            type(f).ifPresent(t -> event.register((EntityType) t, SpawnPlacementTypes.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING,
                    (net.minecraft.world.entity.SpawnPlacements.SpawnPredicate) (et, lvl, reason, p, rnd) -> Mob.checkMobSpawnRules((EntityType<? extends Mob>) et, lvl, reason, p, rnd),
                    RegisterSpawnPlacementsEvent.Operation.REPLACE));
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void onAttributes(EntityAttributeModificationEvent event) {
        STATS.forEach((path, v) -> type(path).ifPresent(t -> {
            set(event, (EntityType) t, Attributes.MAX_HEALTH, v[0]);
            set(event, (EntityType) t, Attributes.ATTACK_DAMAGE, v[1]);
            set(event, (EntityType) t, Attributes.ARMOR, v[2]);
        }));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void set(EntityAttributeModificationEvent event, EntityType type, Holder<Attribute> attr, double value) {
        if (value >= 0 && event.has(type, attr)) {
            event.add(type, attr, value);
        }
    }
}
