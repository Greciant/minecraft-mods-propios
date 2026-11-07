package wips.powers.content;

import com.awakenedmod.awakened.core.quest.QuestReward;
import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import wips.powers.WipsPowers;

/**
 * Objetos quitados del pack (Awakened salvo catalizadores, lo quitado de Legendary Monsters y de Hyper 'N Hiei):
 * no basta con esconderlos del creativo, se cortan en TODAS las fuentes.
 *  - Recompensas de trials/misiones: QuestRewards (ver QuestRewardPurgeMixin) cambia la recompensa por XP de nivel.
 *  - Entregas directas: ItemGrant.to y RelicItem.give (ItemGrantPurgeMixin).
 *  - Cualquier objeto en el suelo (drops de jefes, RelicDrops, mobs, bloques): EntityJoinLevelEvent.
 *  - Loot tables (cofres, mobs, bloques): modificador global wipspowers:purge.
 * Un solo criterio para todo: ContentFilter.hidden(...) + huevos de mobs quitados.
 */
@EventBusSubscriber(modid = WipsPowers.MOD_ID)
public final class ItemPurge {
    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> LOOT =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, WipsPowers.MOD_ID);
    static {
        LOOT.register("purge", () -> Purge.CODEC);
    }

    /** Cuantos objetos se cortaron (registro y autotest). */
    public static long blocked;
    /** Compensacion en XP de nivel por una recompensa quitada (lo engancha el sistema de nivel). */
    public static java.util.function.ObjIntConsumer<ServerPlayer> onCompensate = (p, xp) -> { };

    private ItemPurge() {
    }

    public static boolean banned(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (ContentFilter.hidden(stack.getItem())) {
            return true;
        }
        return stack.getItem() instanceof SpawnEggItem e && LegendaryMonstersFilter.hiddenEntity(e.getType(stack));
    }

    public static void count(ItemStack stack) {
        blocked++;
        WipsPowers.LOGGER.debug("[wipspowers] objeto quitado bloqueado: {}", stack);
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof ItemEntity ie && banned(ie.getItem())) {
            count(ie.getItem());
            event.setCanceled(true);
        }
    }

    /** Recompensa de reemplazo: en vez del objeto quitado, XP de nivel. Se decide al mostrar/entregar (lazy). */
    public static QuestReward guard(QuestReward original, int xp) {
        return new QuestReward() {
            private Boolean bad;

            private boolean bad() {
                if (this.bad == null) {
                    try {
                        this.bad = banned(original.preview());
                    } catch (RuntimeException e) {
                        return false; // registros todavia no listos: decide despues
                    }
                }
                return this.bad;
            }

            @Override
            public ItemStack preview() {
                return this.bad() ? new ItemStack(Items.EXPERIENCE_BOTTLE) : original.preview();
            }

            @Override
            public Component label() {
                return this.bad() ? Component.translatable("quest.wipspowers.level_xp", xp) : original.label();
            }

            @Override
            public boolean grant(ServerPlayer player) {
                if (this.bad()) {
                    onCompensate.accept(player, xp);
                    return true;
                }
                return original.grant(player);
            }
        };
    }

    /** Las reliquias siempre estan quitadas: su recompensa pasa directo a XP de nivel. */
    public static QuestReward relicReplacement(int xp) {
        return new QuestReward() {
            @Override
            public ItemStack preview() {
                return new ItemStack(Items.EXPERIENCE_BOTTLE);
            }

            @Override
            public Component label() {
                return Component.translatable("quest.wipspowers.level_xp", xp);
            }

            @Override
            public boolean grant(ServerPlayer player) {
                onCompensate.accept(player, xp);
                return true;
            }
        };
    }

    public static final class Purge extends LootModifier {
        public static final MapCodec<Purge> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.mapCodec(
                inst -> codecStart(inst).apply(inst, Purge::new));

        public Purge(LootItemCondition[] conditions) {
            super(conditions);
        }

        @Override
        protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
            loot.removeIf(s -> {
                if (banned(s)) {
                    count(s);
                    return true;
                }
                return false;
            });
            return loot;
        }

        @Override
        public MapCodec<? extends IGlobalLootModifier> codec() {
            return CODEC;
        }
    }
}
