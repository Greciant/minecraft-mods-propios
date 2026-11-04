package com.haki;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Servidor -> cliente: el jugador casterId empezo (true) o dejo (false) de usar el Haki. */
public record HakiStatePayload(int casterId, boolean active) implements CustomPacketPayload {
    public static final Type<HakiStatePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(HakiMod.MODID, "haki_state"));
    public static final StreamCodec<ByteBuf, HakiStatePayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, HakiStatePayload::casterId,
        ByteBufCodecs.BOOL, HakiStatePayload::active,
        HakiStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
