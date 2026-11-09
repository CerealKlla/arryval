package com.github.cerealklla.arryval.starttown;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** One of the (up to 8) discovered starting-settlement locations -- persisted so discovery only ever runs once per server, see {@link StartingSettlementStore}'s own doc. {@code y} is a fixed sea-level sample, recomputed fresh at read time if ever needed precisely -- this is only ever used to force-load the right chunk and search nearby for a real bind location, never rendered. */
public record StartingSettlementEntry(int index, String name, int x, int z) {

    public static final Codec<StartingSettlementEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("index").forGetter(StartingSettlementEntry::index),
            Codec.STRING.fieldOf("name").forGetter(StartingSettlementEntry::name),
            Codec.INT.fieldOf("x").forGetter(StartingSettlementEntry::x),
            Codec.INT.fieldOf("z").forGetter(StartingSettlementEntry::z)
    ).apply(i, StartingSettlementEntry::new));
}
