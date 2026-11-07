package wips.powers.crit;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import wips.powers.WipsPowers;

/**
 * Critico: cualquier dano que haga un jugador (golpe o habilidad) tiene CHANCE de ser critico (x3).
 * Si sale, se avisa a ese jugador para que su cliente dispare el impact frame grande. Los impact
 * frames automaticos del mod se bloquean en el cliente (ImpactFireGateMixin).
 */
@EventBusSubscriber(modid = WipsPowers.MOD_ID)
public final class Criticals {
    public record Payload(int targetId, float damage) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(WipsPowers.MOD_ID, "crit"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Payload> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Payload::targetId, ByteBufCodecs.FLOAT, Payload::damage, Payload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static final Map<UUID, Long> LAST = new ConcurrentHashMap<>();

    private Criticals() {
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onIncoming(LivingIncomingDamageEvent event) {
        if (!CritConfig.ENABLED.get() || event.getAmount() <= 0.0F || event.getSource().getEntity() instanceof net.neoforged.neoforge.common.util.FakePlayer) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker) || event.getEntity() == attacker) {
            return;
        }
        long now = attacker.serverLevel().getGameTime();
        Long last = LAST.get(attacker.getUUID());
        if (last != null && now - last < CritConfig.COOLDOWN_TICKS.get()) {
            return;
        }
        if (attacker.getRandom().nextDouble() >= CritConfig.CHANCE.get()) {
            return;
        }
        LAST.put(attacker.getUUID(), now);
        float dealt = (float) (event.getAmount() * CritConfig.MULTIPLIER.get());
        event.setAmount(dealt);
        if (wips.powers.kit.PowerKit.real(attacker) && attacker.connection.hasChannel(Payload.TYPE)) {
            PacketDistributor.sendToPlayer(attacker, new Payload(event.getEntity().getId(), dealt));
        }
    }
}
