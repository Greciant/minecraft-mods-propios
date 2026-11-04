package com.haki;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record HakiKeyPayload(boolean pressed) implements CustomPacketPayload {
    public static final Type<HakiKeyPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(HakiMod.MODID, "haki_key"));
    public static final StreamCodec<ByteBuf, HakiKeyPayload> CODEC =
        ByteBufCodecs.BOOL.map(HakiKeyPayload::new, HakiKeyPayload::pressed);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
