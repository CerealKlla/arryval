package com.github.cerealklla.arryval.tutorial;

import java.util.List;

import com.github.cerealklla.arryval.starttown.VillageQuestTracker;
import com.github.cerealklla.arryval.starttown.VillagerTalkTracker;
import com.github.cerealklla.myssions.api.Myssions;
import com.github.cerealklla.myssions.quest.QuestDefinition;
import com.github.cerealklla.myssions.quest.QuestMilestone;
import com.github.cerealklla.myssions.quest.QuestReward;
import com.github.cerealklla.myssions.quest.QuestTimeLimit;

import net.minecraft.resources.Identifier;

/**
 * The tutorial's own real quest content -- explicit user direction, 2026-11-06: a scripted
 * monologue naming mods by name "breaks immersion"; the tutorial should teach actual gameplay
 * (gaining XP, opening the character menu, grabbing starter gear) through a real quest with
 * optional milestones and real rewards, demonstrating Myssions' new QuestChain mechanic
 * (completing one quest unlocks the next as "Available," accepted explicitly from the Quest Log).
 *
 * <p>Registered from {@code ArryvalMod}'s common setup, per {@code api.Myssions}' own documented
 * "adding a brand-new quest type from another mod" recipe -- every milestone here is a {@code
 * simple}/{@code gather} one, resolved either by Myssions' own built-in gather-item detection or by
 * a direct {@code completeMilestone} call from this mod's own code (see {@code
 * starttown.OnboardingExitHandler}/{@code ArryvalMod#confirmStartingSettlement}), never anything
 * fragile like trying to detect what the LLM-driven conversation actually said.
 */
public final class TutorialQuests {

    private TutorialQuests() {
    }

    public static final Identifier WELCOME_QUEST_ID = Identifier.fromNamespaceAndPath("arryval", "welcome_to_kyngdoms");
    public static final Identifier FOOTING_QUEST_ID = Identifier.fromNamespaceAndPath("arryval", "finding_your_footing");
    public static final Identifier LENDING_A_HAND_QUEST_ID = Identifier.fromNamespaceAndPath("arryval", "lending_a_hand");

    public static void registerAll() {
        QuestMilestone setActive = QuestMilestone.simple("set_active", "Set this quest as your active quest");
        // A required combat step, added 2026-11-06 once the recipe's room gained real enemy_spawn
        // markers -- Myssions' own QuestKillListener already tracks a matching hostile kill
        // automatically (see its own doc), no custom detection needed from this mod at all.
        QuestMilestone defeatFirstHostile = QuestMilestone.killAnyHostile("defeat_first_hostile", "Defeat your first hostile mob", 1);
        // Tracks independently alongside defeatFirstHostile -- Myssions' QuestKillListener
        // increments every matching kill-type milestone on a qualifying kill, not just one, so both
        // progress off the same 4 real kills. Added 2026-11-06 once the room's door got a real
        // tagged lever ("PreExitDoor") wired to it -- see TutorialGuideEntity#checkDoorProgress.
        QuestMilestone clearAllHostiles = QuestMilestone.killAnyHostile("clear_all_hostiles", "Clear every hostile from the halls", 4);
        QuestMilestone reachPortal = QuestMilestone.simple("reach_portal", "Step through the exit portal when you're ready");

        QuestDefinition welcome = new QuestDefinition(
                WELCOME_QUEST_ID, "Welcome to Kyngdoms",
                "Your first real task -- get settled in before you head out into the world.",
                "arryval", List.of(setActive, defeatFirstHostile, clearAllHostiles, reachPortal),
                List.of(QuestReward.gold(75)), QuestTimeLimit.NONE)
                .withUnlocksQuestId(FOOTING_QUEST_ID)
                // Explicit user spec, 2026-11-06 (request #5, corrected twice same day): offered via
                // the new "Press <key> to review quest offer" flow instead of auto-accepted at login
                // -- both Decline and Abandon disabled outright, since the tutorial has no recovery
                // path once its one quest is declined or abandoned.
                .withOfferFlags(true, true);
        Myssions.registerQuestDefinition(welcome);

        // Replaced "Choose and settle into your new home town" (added 2026-11-06), 2026-11-07 --
        // real live-test complaint: by the time this quest is even visible, the player has already
        // done that (it's literally what unlocked this quest) -- "this quest makes no sense at all,"
        // and worse, nothing could ever actually complete it anyway (the one-shot completion attempt
        // fired at Totem-bind time, before the player had a chance to accept this quest at all, so an
        // instance created afterward just sat at 0 progress forever). Explicit user replacement: "if
        // the quest was to talk to N villagers, that would make sense. That's an actually achievable
        // objective" -- see starttown.VillagerTalkTracker for the real tally.
        QuestMilestone talkToVillagers = QuestMilestone.simple("talk_to_villagers", "Talk to " + VillagerTalkTracker.REQUIRED_COUNT + " different villagers in your new town", VillagerTalkTracker.REQUIRED_COUNT);
        QuestDefinition footing = new QuestDefinition(
                FOOTING_QUEST_ID, "Finding Your Footing",
                "Your new home has real people in it -- go meet some of them.",
                "arryval", List.of(talkToVillagers),
                List.of(QuestReward.gold(50), QuestReward.item(Identifier.fromNamespaceAndPath("minecraft", "bread"), 16)),
                QuestTimeLimit.NONE)
                .withUnlocksQuestId(LENDING_A_HAND_QUEST_ID);
        Myssions.registerQuestDefinition(footing);

        // Added 2026-11-08, explicit user request: a third chain quest, unlocked by Finding Your
        // Footing -- "do 10 quests for the village you started in." Tracked by starttown
        // .VillageQuestTracker, which only counts a completed quest (any quest, from any mod) whose
        // issuing plot belongs to this player's own recorded starting settlement (see starttown
        // .StartingSettlementCoreState) -- a quest done for a different village doesn't count.
        QuestMilestone helpYourVillage = QuestMilestone.simple("help_your_village", "Complete " + VillageQuestTracker.REQUIRED_COUNT + " quests for your new home", VillageQuestTracker.REQUIRED_COUNT);
        QuestDefinition lendingAHand = new QuestDefinition(
                LENDING_A_HAND_QUEST_ID, "Lending a Hand",
                "You're settled in now -- time to earn your place. Help out around town.",
                "arryval", List.of(helpYourVillage),
                List.of(QuestReward.gold(100)),
                QuestTimeLimit.NONE);
        Myssions.registerQuestDefinition(lendingAHand);
    }
}
