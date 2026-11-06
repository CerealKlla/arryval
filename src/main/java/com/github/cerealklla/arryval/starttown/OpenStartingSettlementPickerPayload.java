package com.github.cerealklla.arryval.starttown;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.arryval.ArryvalMod;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server-to-client: the resolved starting-settlement candidates, name + distance only (the real ids stay server-side -- the client only ever reports back a plain index). */
public record OpenStartingSettlementPickerPayload(List<Entry> entries) implements CustomPacketPayload {

    public record Entry(String name, double distanceBlocks) {
    }

    private static final StreamCodec<ByteBuf, Entry> ENTRY_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, Entry::name,
            ByteBufCodecs.DOUBLE, Entry::distanceBlocks,
            Entry::new);

    public static final Type<OpenStartingSettlementPickerPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(ArryvalMod.MODID, "open_starting_settlement_picker"));

    public static final StreamCodec<ByteBuf, OpenStartingSettlementPickerPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.collection(ArrayList::new, ENTRY_CODEC), OpenStartingSettlementPickerPayload::entries,
            OpenStartingSettlementPickerPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
