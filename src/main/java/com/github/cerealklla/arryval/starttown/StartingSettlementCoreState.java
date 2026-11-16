package com.github.cerealklla.arryval.starttown;

import java.util.Optional;
import java.util.UUID;

import com.github.cerealklla.arryval.registration.ModAttachments;
import com.github.cerealklla.settlemynts.api.Settlemynts;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Per-player, permanent record of the real Settlemynts {@code settlementCoreId} (see {@code
 * api.Settlemynts#findPlotAt}) the player's Recallcinite Totem ended up bound to during onboarding
 * -- their "home village." Added 2026-11-08 for the new "do 10 quests for your village" quest
 * ({@code VillageQuestTracker}) and reused later by Terrytories' first-Friendly letter rule. Resolved
 * from the real bind position (never the seed-predicted one) at the exact moment the Totem actually
 * binds -- see {@code ArryvalMod#confirmStartingSettlement} and {@code
 * PendingSettlementFormation#finishBinding}, the two real places that happens.
 *
 * <p>Empty by default/for any player who onboarded before this existed -- a quest tracker reading
 * this should simply never progress for such a player rather than guessing.
 */
public record StartingSettlementCoreState(Optional<UUID> settlementCoreId) {

    public static final MapCodec<StartingSettlementCoreState> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            UUIDUtil.CODEC.optionalFieldOf("settlement_core_id").forGetter(StartingSettlementCoreState::settlementCoreId)
    ).apply(i, StartingSettlementCoreState::new));

    public static final StartingSettlementCoreState DEFAULT = new StartingSettlementCoreState(Optional.empty());

    /** Generous enough to cover a bind spot sitting in a settlement's open paths/commons (never covered by any plot's own tighter polygon) without reaching into a genuinely different, nearby settlement -- see {@link #recordFor}'s own doc. */
    private static final double SETTLEMENT_MATCH_MARGIN_BLOCKS = 32.0;

    /**
     * Resolves the real {@code settlementCoreId} for wherever the player's Totem just bound (via
     * {@code api.Settlemynts#findPlotAt}, which already handles both player-founded and natural
     * villages transparently) and persists it as this player's permanent "home village." Falls back
     * to the broader, margin-based {@code api.Settlemynts#findSettlementCoreIdNear} when the bind
     * position doesn't land inside any specific plot's own polygon -- **real bug, found 2026-11-16**:
     * this used to be treated as a harmless "edge case... tolerated silently," but a bind spot in a
     * natural village's open paths/commons is common, not an edge case, and it permanently stalled
     * "Lending a Hand" for whichever player it happened to (this attachment is never written a second
     * time after onboarding, so a one-time miss here was forever, not just at bind time). Called from
     * the two real places a Totem bind actually happens: {@code ArryvalMod#confirmStartingSettlement}
     * (an already-founded candidate) and {@code PendingSettlementFormation#finishBinding} (a
     * not-yet-founded one, once it finally forms).
     */
    public static void recordFor(ServerPlayer player, ServerLevel level, BlockPos bindPos) {
        Settlemynts.findPlotAt(level, bindPos).map(plot -> plot.settlementCoreId())
                .or(() -> Settlemynts.findSettlementCoreIdNear(level, bindPos, SETTLEMENT_MATCH_MARGIN_BLOCKS))
                .ifPresent(id -> player.setData(ModAttachments.STARTING_SETTLEMENT_CORE_STATE,
                        new StartingSettlementCoreState(Optional.of(id))));
    }
}
