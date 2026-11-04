package wips.powers;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Forma "visual" de un jugador que usa una tecnica de Seiryuu: el cliente de Grand Line elige la
 * cinematica grande segun la forma (hibrida/dragon). Le decimos esa forma solo a los efectos,
 * sin transformar ni cambiar el modelo.
 */
public final class FormFx {
    private record Window(String form, long until) {}

    private static final Map<Integer, Window> CLIENT = new ConcurrentHashMap<>();

    public record Payload(int entityId, String form, int ticks) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(WipsPowers.MOD_ID, "form_fx"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Payload> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Payload::entityId,
                ByteBufCodecs.STRING_UTF8, Payload::form,
                ByteBufCodecs.VAR_INT, Payload::ticks,
                Payload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private FormFx() {
    }

    public static void send(ServerPlayer player, String form, int ticks) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new Payload(player.getId(), form, ticks));
    }

    static void receive(Payload p) {
        CLIENT.put(p.entityId(), new Window(p.form(), System.currentTimeMillis() + p.ticks() * 50L));
    }

    /** Forma visual activa en el cliente para esta entidad, o null. */
    public static String clientForm(Entity entity) {
        Window w = CLIENT.get(entity.getId());
        if (w == null) {
            return null;
        }
        if (System.currentTimeMillis() > w.until()) {
            CLIENT.remove(entity.getId());
            return null;
        }
        return w.form();
    }
}
