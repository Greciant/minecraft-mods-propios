package wips.powers.kit;

import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredRegister;
import wips.powers.WipsPowers;
import wips.powers.ice.IceShardEntity;

/** Entidades de los poderes nuevos (ninguna se guarda en el mundo). */
public final class WipsEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, WipsPowers.MOD_ID);

    public static final Supplier<EntityType<IceShardEntity>> ICE_SHARD = ENTITIES.register("ice_shard",
            () -> EntityType.Builder.<IceShardEntity>of(IceShardEntity::new, MobCategory.MISC).sized(0.5F, 0.5F)
                    .clientTrackingRange(8).updateInterval(1).noSave().noSummon().build("ice_shard"));

    public static final Supplier<EntityType<wips.powers.slime.MiniSlimeEntity>> MINI_SLIME = ENTITIES.register("mini_slime",
            () -> EntityType.Builder.<wips.powers.slime.MiniSlimeEntity>of(wips.powers.slime.MiniSlimeEntity::new, MobCategory.MISC)
                    .sized(0.52F, 0.52F).eyeHeight(0.325F).clientTrackingRange(10).noSave().noSummon().build("mini_slime"));
    public static final Supplier<EntityType<wips.powers.slime.SlimeGlobEntity>> SLIME_GLOB = ENTITIES.register("slime_glob",
            () -> EntityType.Builder.<wips.powers.slime.SlimeGlobEntity>of(wips.powers.slime.SlimeGlobEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).clientTrackingRange(8).updateInterval(2).noSave().noSummon().build("slime_glob"));
    public static final Supplier<EntityType<wips.powers.slime.SlimeShotEntity>> SLIME_SHOT = ENTITIES.register("slime_shot",
            () -> EntityType.Builder.<wips.powers.slime.SlimeShotEntity>of(wips.powers.slime.SlimeShotEntity::new, MobCategory.MISC)
                    .sized(0.3F, 0.3F).clientTrackingRange(6).updateInterval(5).noSave().noSummon().build("slime_shot"));

    public static void attributes(net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent event) {
        event.put(MINI_SLIME.get(), wips.powers.slime.MiniSlimeEntity.attributes().build());
    }

    private WipsEntities() {
    }
}
