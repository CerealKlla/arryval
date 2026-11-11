package com.github.cerealklla.arryval.onboarding;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Per-player flag: has this player ever had the welcome tutorial triggered? Server-only
 * bookkeeping -- not synced, nothing client-side reads it, same convention Kyt's own now-removed
 * {@code welcome.WelcomeGrantState} once followed.
 */
public record TutorialGrantState(boolean granted) {

    public static final MapCodec<TutorialGrantState> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            com.mojang.serialization.Codec.BOOL.fieldOf("granted").forGetter(TutorialGrantState::granted)
    ).apply(i, TutorialGrantState::new));

    public static final TutorialGrantState DEFAULT = new TutorialGrantState(false);
}
