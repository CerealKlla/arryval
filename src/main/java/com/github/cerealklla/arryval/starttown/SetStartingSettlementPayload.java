package com.github.cerealklla.arryval.starttown;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.arryval.ArryvalMod;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client-to-server: the chosen index into the picker's own list -- the server re-validates against its own cached candidate list ({@code PendingOnboarding#getCachedCandidates}), never trusting this blindly. */
public record SetStartingSettlementPayload(int index) implements CustomPacketPayload {

    public static final Type<SetStartingSettlementPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(ArryvalMod.MODID, "set_starting_settlement"));

    public static final StreamCodec<ByteBuf, SetStartingSettlementPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SetStartingSettlementPayload::index,
            SetStartingSettlementPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
