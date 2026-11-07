package wips.powers;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Sello de energia que acompana cada tecnica (lo dibuja client.SigilRenderer). El servidor solo
 * avisa "empezo" (ventana larga mientras carga) y "se solto" (estallido y desvanecido).
 */
public final class CastSigil {
    /** Estado en el cliente; los tiempos se fijan en ticks del mundo cliente al llegar. */
    public static final class State {
        public final String ability;
        public long start = Long.MIN_VALUE;
        public long end = Long.MIN_VALUE;
        public long releasedAt = Long.MIN_VALUE;
        int pendingTicks;
        boolean pendingRelease;

        State(String ability) {
            this.ability = ability;
        }

        /** Convierte lo recibido a ticks del mundo; lo llama el cliente en cada tick. */
        public void resolve(long now) {
            if (this.start == Long.MIN_VALUE) {
                this.start = now;
            }
            if (this.pendingTicks > 0) {
                this.end = now + this.pendingTicks;
                this.pendingTicks = 0;
            }
            if (this.pendingRelease) {
                this.releasedAt = now;
                this.pendingRelease = false;
            }
        }
    }

    public record Payload(int entityId, String ability, int ticks, boolean release) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(WipsPowers.MOD_ID, "cast_sigil"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Payload> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Payload::entityId,
                ByteBufCodecs.STRING_UTF8, Payload::ability,
                ByteBufCodecs.VAR_INT, Payload::ticks,
                ByteBufCodecs.BOOL, Payload::release,
                Payload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static final Map<Integer, State> CLIENT = new ConcurrentHashMap<>();

    private CastSigil() {
    }

    /** start: abre (o reabre) el sello por `ticks`; release: estalla y se apaga en `ticks`. */
    public static void send(ServerPlayer player, String ability, int ticks, boolean release) {
        wips.powers.kit.PowerKit.sendAround(player, new Payload(player.getId(), ability, ticks, release));
    }

    static void receive(Payload p) {
        State s = CLIENT.get(p.entityId());
        if (s == null || !s.ability.equals(p.ability()) || (!p.release() && s.releasedAt != Long.MIN_VALUE)) {
            s = new State(p.ability());
            CLIENT.put(p.entityId(), s);
        }
        s.pendingTicks = Math.max(1, p.ticks());
        if (p.release()) {
            s.pendingRelease = true;
        }
    }
}
