package com.github.cerealklla.arryval.starttown;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.github.cerealklla.settlemynts.api.Settlemynts;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Checks each of {@link StartingSettlementStore}'s cached starting-settlement locations for a real,
 * currently-available bind location -- the starting-town picker's candidate list. The locations
 * themselves are discovered exactly once per server (see {@link StartingSettlementStore}'s own doc,
 * explicit user correction 2026-11-06: no reason to re-run that discovery on every player's own
 * approach); only this per-call readiness check is genuinely live, since whether a given location's
 * settlement/plot has actually finished forming changes over the life of the server.
 *
 * <p><b>Real, explicit user correction, 2026-11-06</b>: an earlier version of this class only ever
 * listed an already-{@code REALIZED}, already-founded settlement -- "No, because they are supposed
 * to automatically generate, NPC settlements are perfectly viable options for a player to select.
 * You are supposed to be using the world seed to know where settlements WILL generate already." A
 * never-visited vanilla village the world seed guarantees will generate is a real, pickable candidate
 * too (a {@link Candidate#isReady()} {@code false} one). See {@code PendingSettlementFormation} for
 * what actually happens once a not-yet-ready pick is confirmed -- the real settlement doesn't exist
 * the instant this method returns, only its predicted name and position do.
 *
 * <p><b>No plot/ownership concept at all, confirmed with the user 2026-11-06</b> (a prior
 * unconfirmed assumption from the original plan, corrected on direct challenge -- "at what point did
 * I ever say anything about 'assigning the player a plot'?"): picking a town only ever binds the
 * Recallcinite Totem to a location there and teleports the player -- it never claims a
 * {@code PlotRecord}.
 */
public final class StartingSettlementResolver {

    private StartingSettlementResolver() {
    }

    /**
     * {@code bindPos} is empty for a seed-predicted-but-not-yet-founded village (or a founded one
     * with no plot to bind to yet) -- {@link #isReady()} is {@code false} in that case, and a caller
     * should teleport to {@link #predictedPos()} and wait for the real settlement to form (see
     * {@code PendingSettlementFormation}) rather than binding the Totem there immediately.
     */
    public record Candidate(String name, BlockPos predictedPos, Optional<BlockPos> bindPos) {
        public boolean isReady() {
            return bindPos.isPresent();
        }
    }

    /** Generous radius around each cached location to search for a real bind location -- a settlement's own geo-registered center and its real plots are never far apart, player-founded or natural. */
    private static final double BIND_SEARCH_RADIUS_BLOCKS = 128.0;

    /**
     * {@code level.getChunk(...)} here is safe and cheap, not the earlier stall -- real live
     * follow-up, 2026-11-06: "why are you not force loading all 8 settlements from the moment the
     * first player logs into the server?" {@link StartingSettlementStore}'s own one-time discovery
     * now force-loads (and so generates/saves to disk) every location's chunk immediately at server
     * start, well before any player could possibly reach the portal -- so by the time this runs,
     * each chunk is either still resident in memory, or a cheap on-disk read of already-generated
     * terrain, never real world-gen from scratch. The original 4-second stall was real world-gen
     * happening here, on the main thread, for up to 8 never-before-touched locations, every single
     * approach -- moving the one real generation cost to server start instead fixes that at the root.
     */
    public static List<Candidate> findStartingCandidates(ServerLevel level) {
        List<Candidate> result = new ArrayList<>();
        for (StartingSettlementEntry entry : StartingSettlementStore.all()) {
            BlockPos geoPos = new BlockPos(entry.x(), level.getSeaLevel(), entry.z());
            level.getChunk(entry.x() >> 4, entry.z() >> 4);
            Optional<BlockPos> bindPos = Settlemynts.findStartingBindLocation(level, geoPos, BIND_SEARCH_RADIUS_BLOCKS);
            result.add(new Candidate(entry.name(), geoPos, bindPos));
        }
        return result;
    }
}
