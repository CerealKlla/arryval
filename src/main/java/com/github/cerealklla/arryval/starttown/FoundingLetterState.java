package com.github.cerealklla.arryval.starttown;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Per-player, permanent flag: has this player ever received the "found your own settlement"
 * mayor's letter yet? See {@link FoundingLetterListener}'s own doc -- "the first time a player
 * achieves Friendly status with any natural NPC settlement," ever, across their whole account, not
 * once per settlement.
 */
public record FoundingLetterState(boolean sent) {

    public static final MapCodec<FoundingLetterState> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            com.mojang.serialization.Codec.BOOL.fieldOf("sent").forGetter(FoundingLetterState::sent)
    ).apply(i, FoundingLetterState::new));

    public static final FoundingLetterState DEFAULT = new FoundingLetterState(false);
}
