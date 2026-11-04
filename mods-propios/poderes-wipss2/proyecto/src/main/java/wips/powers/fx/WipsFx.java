package wips.powers.fx;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import wips.powers.WipsPowers;

/**
 * Evento visual generico servidor -> cliente. El servidor solo dice "que" y "donde"; el cliente
 * compone el efecto (geometria procedural con el motor de VFX de Grand Line). Cada tipo de efecto
 * se registra en el cliente con {@link #handle(String, Consumer)}.
 */
public final class WipsFx {
    public record Payload(String kind, int entity, double x, double y, double z, float dx, float dy, float dz,
                          float power, long seed, int extra) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(WipsPowers.MOD_ID, "fx"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Payload> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeUtf(p.kind);
                    buf.writeVarInt(p.entity);
                    buf.writeDouble(p.x);
                    buf.writeDouble(p.y);
                    buf.writeDouble(p.z);
                    buf.writeFloat(p.dx);
                    buf.writeFloat(p.dy);
                    buf.writeFloat(p.dz);
                    buf.writeFloat(p.power);
                    buf.writeLong(p.seed);
                    buf.writeVarInt(p.extra);
                },
                buf -> new Payload(buf.readUtf(), buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                        buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readLong(), buf.readVarInt()));

        public Vec3 pos() {
            return new Vec3(this.x, this.y, this.z);
        }

        public Vec3 dir() {
            return new Vec3(this.dx, this.dy, this.dz);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static final Map<String, java.util.List<Consumer<Payload>>> HANDLERS = new ConcurrentHashMap<>();

    private WipsFx() {
    }

    /** Solo cliente: agrega quien dibuja un tipo de efecto (puede haber varias capas por tipo). */
    public static void handle(String kind, Consumer<Payload> handler) {
        HANDLERS.computeIfAbsent(kind, k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(handler);
    }

    public static void receive(Payload p) {
        java.util.List<Consumer<Payload>> hs = HANDLERS.get(p.kind());
        if (hs != null) {
            for (Consumer<Payload> h : hs) {
                h.accept(p);
            }
        }
    }

    /** Envia a todos los que ven la entidad (y a ella si es jugador). */
    public static void around(Entity source, String kind, Vec3 pos, Vec3 dir, float power, long seed, int extra) {
        Payload p = new Payload(kind, source.getId(), pos.x, pos.y, pos.z, (float) dir.x, (float) dir.y, (float) dir.z, power, seed, extra);
        if (source instanceof ServerPlayer sp) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(sp, p);
        } else {
            PacketDistributor.sendToPlayersTrackingEntity(source, p);
        }
    }

    /** Envia a los jugadores que tienen cargado el chunk de la posicion. */
    public static void at(ServerLevel level, int entityId, String kind, Vec3 pos, Vec3 dir, float power, long seed, int extra) {
        PacketDistributor.sendToPlayersTrackingChunk(level, new ChunkPos(net.minecraft.core.BlockPos.containing(pos)),
                new Payload(kind, entityId, pos.x, pos.y, pos.z, (float) dir.x, (float) dir.y, (float) dir.z, power, seed, extra));
    }
}
