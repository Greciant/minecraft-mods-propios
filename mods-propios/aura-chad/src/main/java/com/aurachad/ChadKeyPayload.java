package com.aurachad;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** El jugador pulso la tecla: alterna el modo chad. */
public record ChadKeyPayload() implements CustomPacketPayload {
    public static final Type<ChadKeyPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(AuraChadMod.MODID, "chad_key"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ChadKeyPayload> CODEC = StreamCodec.unit(new ChadKeyPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
