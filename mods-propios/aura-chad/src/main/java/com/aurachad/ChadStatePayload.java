package com.aurachad;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Un jugador entra o sale del modo chad (lo reciben el y quienes lo ven). */
public record ChadStatePayload(int entityId, boolean active) implements CustomPacketPayload {
    public static final Type<ChadStatePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(AuraChadMod.MODID, "chad_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ChadStatePayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, ChadStatePayload::entityId,
        ByteBufCodecs.BOOL, ChadStatePayload::active,
        ChadStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
