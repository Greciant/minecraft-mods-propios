package wips.powers.level;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import wips.powers.WipsPowers;

/**
 * Nivel de jugador propio de wipspowers: empieza en 1, maximo 50 (configurable). Es independiente de la experiencia
 * vanilla y del nivel de personaje de Awakened, no toca ningun atributo del jugador: solo lo usa WipsScaling para el
 * dano de los poderes. Se guarda en el jugador (attachment) y se conserva al morir.
 */
@EventBusSubscriber(modid = WipsPowers.MOD_ID)
public final class WipsLevel {
    /** Datos guardados: nivel, XP dentro del nivel y biomas ya pisados (para XP de exploracion). */
    public static final class Data {
        int level = 1;
        double xp;
        final Set<String> biomes = new HashSet<>();

        static final Codec<Data> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("level").forGetter(d -> d.level),
                Codec.DOUBLE.fieldOf("xp").forGetter(d -> d.xp),
                Codec.STRING.listOf().optionalFieldOf("biomes", List.of()).forGetter(d -> new ArrayList<>(d.biomes))
        ).apply(i, (l, x, b) -> {
            Data d = new Data();
            d.level = Math.max(1, l);
            d.xp = Math.max(0, x);
            d.biomes.addAll(b);
            return d;
        }));

        public int level() {
            return this.level;
        }

        public double xp() {
            return this.xp;
        }
    }

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, WipsPowers.MOD_ID);
    public static final Supplier<AttachmentType<Data>> DATA = ATTACHMENTS.register("level",
            () -> AttachmentType.builder(Data::new).serialize(Data.CODEC).copyOnDeath().build());

    /** Sincronizacion al cliente (HUD). */
    public record Sync(int level, float xp, float need, int max) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(WipsPowers.MOD_ID, "level"));
        public static final StreamCodec<ByteBuf, Sync> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Sync::level, ByteBufCodecs.FLOAT, Sync::xp, ByteBufCodecs.FLOAT, Sync::need,
                ByteBufCodecs.VAR_INT, Sync::max, Sync::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private WipsLevel() {
    }

    public static Data data(ServerPlayer player) {
        return player.getData(DATA);
    }

    public static int level(ServerPlayer player) {
        return Math.min(maxLevel(), data(player).level);
    }

    public static int maxLevel() {
        return LevelConfig.MAX_LEVEL.get();
    }

    /** XP para pasar del nivel L al L+1. */
    public static double xpForNext(int level) {
        return LevelConfig.XP_BASE.get() + LevelConfig.XP_FACTOR.get() * Math.pow(level, LevelConfig.XP_EXPONENT.get());
    }

    /** Suma XP ya calculada (sin rendimientos decrecientes: eso lo hace LevelXp). Devuelve niveles subidos. */
    public static int addXp(ServerPlayer player, double amount) {
        if (amount <= 0 || player.isSpectator()) {
            return 0;
        }
        Data d = data(player);
        int max = maxLevel();
        if (d.level >= max) {
            d.level = max;
            d.xp = 0;
            return 0;
        }
        int before = d.level;
        d.xp += amount;
        while (d.level < max && d.xp >= xpForNext(d.level)) {
            d.xp -= xpForNext(d.level);
            d.level++;
        }
        if (d.level >= max) {
            d.xp = 0;
        }
        player.setData(DATA, d);
        if (d.level > before) {
            onLevelUp(player, d.level);
        }
        sync(player);
        return d.level - before;
    }

    public static void setLevel(ServerPlayer player, int level) {
        Data d = data(player);
        d.level = Math.max(1, Math.min(maxLevel(), level));
        d.xp = 0;
        player.setData(DATA, d);
        sync(player);
    }

    private static void onLevelUp(ServerPlayer player, int level) {
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.3F);
        player.displayClientMessage(Component.translatable("message.wipspowers.level_up", level,
                String.format(java.util.Locale.ROOT, "%.2f", WipsScaling.levelMultiplier(level))).withStyle(ChatFormatting.AQUA), false);
    }

    public static void sync(ServerPlayer player) {
        Data d = data(player);
        PacketDistributor.sendToPlayer(player, new Sync(d.level, (float) d.xp, (float) xpForNext(d.level), maxLevel()));
    }

    /** Bioma nuevo para este jugador? (y lo marca). */
    static boolean firstBiome(ServerPlayer player, String biome) {
        Data d = data(player);
        if (d.biomes.add(biome)) {
            player.setData(DATA, d);
            return true;
        }
        return false;
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            sync(sp);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            sync(sp);
        }
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            sync(sp);
        }
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wips").then(Commands.literal("level")
                .then(Commands.literal("get").then(Commands.argument("player", EntityArgument.player()).executes(c -> {
                    ServerPlayer p = EntityArgument.getPlayer(c, "player");
                    Data d = data(p);
                    c.getSource().sendSuccess(() -> Component.literal(p.getName().getString() + ": nivel " + d.level + " ("
                            + (int) d.xp + "/" + (int) xpForNext(d.level) + " XP), multiplicador de dano x"
                            + String.format(java.util.Locale.ROOT, "%.2f", WipsScaling.multiplier(p))), false);
                    return d.level;
                })))
                .then(Commands.literal("set").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player()).then(Commands.argument("level", IntegerArgumentType.integer(1, 200))
                                .executes(c -> {
                                    ServerPlayer p = EntityArgument.getPlayer(c, "player");
                                    setLevel(p, IntegerArgumentType.getInteger(c, "level"));
                                    c.getSource().sendSuccess(() -> Component.literal(p.getName().getString() + " -> nivel " + level(p)), true);
                                    return 1;
                                }))))
                .then(Commands.literal("addxp").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player()).then(Commands.argument("xp", DoubleArgumentType.doubleArg(0))
                                .executes(c -> {
                                    ServerPlayer p = EntityArgument.getPlayer(c, "player");
                                    addXp(p, DoubleArgumentType.getDouble(c, "xp"));
                                    c.getSource().sendSuccess(() -> Component.literal(p.getName().getString() + " -> nivel " + level(p)), true);
                                    return 1;
                                }))))));
    }
}
