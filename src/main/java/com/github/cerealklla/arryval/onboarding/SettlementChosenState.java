package com.github.cerealklla.arryval.onboarding;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Per-player flag: has this player actually picked a starting settlement yet? Server-only
 * bookkeeping, same convention as {@link TutorialGrantState}.
 *
 * <p>Added 2026-11-06 to fix a real bug: {@code starttown.PendingOnboarding#isAwaitingPick} is
 * deliberately in-memory, not persisted (see that class's own doc) -- but it was only ever set via
 * {@link FirstLoginListener}, which itself only runs the real first time {@link TutorialGrantState}
 * is granted. Any server/client restart after that one moment wiped the in-memory flag for good,
 * silently breaking the exit-portal picker for the rest of that account's life, even mid-tutorial --
 * a real live bug, not the "rare edge case" {@code PendingOnboarding}'s own doc originally assumed.
 * {@link FirstLoginListener} now re-marks a player awaiting a pick on every login (not just the
 * first) as long as this flag is still false, so the picker survives any number of restarts until
 * the player actually finishes picking.
 */
public record SettlementChosenState(boolean chosen) {

    public static final MapCodec<SettlementChosenState> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            com.mojang.serialization.Codec.BOOL.fieldOf("chosen").forGetter(SettlementChosenState::chosen)
    ).apply(i, SettlementChosenState::new));

    public static final SettlementChosenState DEFAULT = new SettlementChosenState(false);
}
