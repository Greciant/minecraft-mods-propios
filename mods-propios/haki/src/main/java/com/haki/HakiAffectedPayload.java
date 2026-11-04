package com.haki;

import java.util.List;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Servidor -> cliente: ids de las entidades paralizadas ahora mismo (se manda cada pocos ticks). */
public record HakiAffectedPayload(List<Integer> ids) implements CustomPacketPayload {
    public static final Type<HakiAffectedPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(HakiMod.MODID, "haki_affected"));
    public static final StreamCodec<ByteBuf, HakiAffectedPayload> CODEC =
        ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(HakiVisuals.AFFECTED_MAX_IDS))
            .map(HakiAffectedPayload::new, HakiAffectedPayload::ids);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
