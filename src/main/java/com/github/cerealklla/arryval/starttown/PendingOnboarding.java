package com.github.cerealklla.arryval.starttown;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory, non-persisted tracking for "this player is mid-tutorial, the next Wyrld exit they
 * trigger should open the starting-settlement picker instead of teleporting normally." Deliberately
 * NOT the same flag as {@code onboarding.TutorialGrantState} (which means "has ever been sent to the
 * tutorial," set permanently at first login) -- this one only needs to live for the duration of one
 * tutorial session, and is the actual gate {@code OnboardingExitHandler} checks so it never
 * intercepts an unrelated Wyrld exit (a Myssions quest dungeon, a settlement Guardhouse's own
 * entrance) for a player who isn't currently in Arryval's own tutorial flow. Losing this on a server
 * restart mid-tutorial is an accepted, rare edge case -- that player's exit just behaves normally
 * (teleports back to their original entry point) instead of showing the picker; not worth
 * persisting for.
 */
public final class PendingOnboarding {

    private static final Set<UUID> AWAITING_PICK = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, List<StartingSettlementResolver.Candidate>> RESOLVED_CANDIDATES = new ConcurrentHashMap<>();

    private PendingOnboarding() {
    }

    public static void markAwaitingPick(UUID playerId) {
        AWAITING_PICK.add(playerId);
    }

    public static boolean isAwaitingPick(UUID playerId) {
        return AWAITING_PICK.contains(playerId);
    }

    /** Cached at the moment the picker is opened -- the player's eventual pick is re-validated against this exact list, not a fresh re-resolution that could've changed while the screen was open. */
    public static void cacheCandidates(UUID playerId, List<StartingSettlementResolver.Candidate> candidates) {
        RESOLVED_CANDIDATES.put(playerId, candidates);
    }

    public static List<StartingSettlementResolver.Candidate> getCachedCandidates(UUID playerId) {
        return RESOLVED_CANDIDATES.getOrDefault(playerId, List.of());
    }

    public static void clear(UUID playerId) {
        AWAITING_PICK.remove(playerId);
        RESOLVED_CANDIDATES.remove(playerId);
    }
}
