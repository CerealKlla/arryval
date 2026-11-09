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
    private static final Map<UUID, UUID> WELCOME_QUEST_INSTANCE_IDS = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> TUTORIAL_ENTRANCE_IDS = new ConcurrentHashMap<>();
    private static final Set<UUID> PROACTIVELY_PROMPTED = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> PICKER_PENDING_AFTER_REWARD = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Set<Integer>> TALKED_NPC_IDS = new ConcurrentHashMap<>();

    private PendingOnboarding() {
    }

    public static void markAwaitingPick(UUID playerId) {
        AWAITING_PICK.add(playerId);
    }

    public static boolean isAwaitingPick(UUID playerId) {
        return AWAITING_PICK.contains(playerId);
    }

    /**
     * Whether {@code OnboardingExitHandler#onApproach}'s proactive popup has already fired for this
     * player since the last time they were marked awaiting a pick -- a real live bug, 2026-11-06: a
     * time-based resend cooldown there kept reopening the same screen every few seconds while a
     * player simply stood near the portal ("it's spamming me"). The proactive popup is now a true
     * one-shot per approach instead of a periodic refresh; {@code WyrldExitHandler#tryHandle} (actual
     * portal contact) is unaffected and still fires every time, unconditionally, as the real fallback.
     */
    public static boolean hasBeenProactivelyPrompted(UUID playerId) {
        return PROACTIVELY_PROMPTED.contains(playerId);
    }

    public static void markProactivelyPrompted(UUID playerId) {
        PROACTIVELY_PROMPTED.add(playerId);
    }

    /** Cached at the moment the picker is opened -- the player's eventual pick is re-validated against this exact list, not a fresh re-resolution that could've changed while the screen was open. */
    public static void cacheCandidates(UUID playerId, List<StartingSettlementResolver.Candidate> candidates) {
        RESOLVED_CANDIDATES.put(playerId, candidates);
    }

    public static List<StartingSettlementResolver.Candidate> getCachedCandidates(UUID playerId) {
        return RESOLVED_CANDIDATES.getOrDefault(playerId, List.of());
    }

    /** The instance id of this player's own "Welcome to Kyngdoms" quest (assigned at first login, see {@code onboarding.FirstLoginListener}) -- tracked here since nothing else in Arryval needs to look it up by definition id the way a QuestChain follow-up does. */
    public static void setWelcomeQuestInstanceId(UUID playerId, UUID instanceId) {
        WELCOME_QUEST_INSTANCE_IDS.put(playerId, instanceId);
    }

    public static java.util.Optional<UUID> getWelcomeQuestInstanceId(UUID playerId) {
        return java.util.Optional.ofNullable(WELCOME_QUEST_INSTANCE_IDS.get(playerId));
    }

    /** The {@code entranceId} {@code onboarding.FirstLoginListener} spawned this player's tutorial Wyrld with -- lets {@code tutorial.TutorialGuideEntity} resolve {@code api.TinyWyrlds#getEnterResult} for the real generated dungeon instance id, needed to call {@code api.TinyWyrlds#toggleState}. */
    public static void setTutorialEntranceId(UUID playerId, UUID entranceId) {
        TUTORIAL_ENTRANCE_IDS.put(playerId, entranceId);
    }

    public static java.util.Optional<UUID> getTutorialEntranceId(UUID playerId) {
        return java.util.Optional.ofNullable(TUTORIAL_ENTRANCE_IDS.get(playerId));
    }

    /**
     * Real bug, found live 2026-11-07: "I got the quest completion menu but before I could click
     * accept the teleport destination menu popped up and closed the quest completion menu" -- both
     * UIs used to open in the same server tick via {@code OnboardingExitHandler#resolveAndSendPicker},
     * and a vanilla menu-open always replaces whatever screen the client currently has up. Marked the
     * instant the Welcome quest's reward GUI opens instead of sending the picker right away;
     * consumed (and the picker finally sent) once that GUI actually closes -- see {@code
     * OnboardingExitHandler}'s own {@code PlayerContainerEvent.Close} listener.
     */
    public static void markPickerPendingAfterReward(UUID playerId) {
        PICKER_PENDING_AFTER_REWARD.add(playerId);
    }

    public static boolean consumePickerPendingAfterReward(UUID playerId) {
        return PICKER_PENDING_AFTER_REWARD.remove(playerId);
    }

    /** Dedup for {@code starttown.VillagerTalkTracker}'s "talk to N different villagers" milestone (Finding Your Footing, replaced 2026-11-07 -- see that quest's own doc) -- returns true the first time this NPC's own entity id is recorded for this player, false on every repeat so spam-clicking the same NPC can't count toward the total more than once. */
    public static boolean recordTalkedToNpc(UUID playerId, int npcEntityId) {
        return TALKED_NPC_IDS.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet()).add(npcEntityId);
    }

    public static void clear(UUID playerId) {
        AWAITING_PICK.remove(playerId);
        RESOLVED_CANDIDATES.remove(playerId);
        WELCOME_QUEST_INSTANCE_IDS.remove(playerId);
        TUTORIAL_ENTRANCE_IDS.remove(playerId);
        PROACTIVELY_PROMPTED.remove(playerId);
        PICKER_PENDING_AFTER_REWARD.remove(playerId);
        TALKED_NPC_IDS.remove(playerId);
    }
}
