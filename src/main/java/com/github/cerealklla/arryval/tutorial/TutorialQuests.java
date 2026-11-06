package com.github.cerealklla.arryval.tutorial;

import java.util.List;
import java.util.Optional;

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

    /** The real item the tutorial's one starter-supplies chest is stocked with (see {@code WelcomeTutorial.json}'s own `common_loot`) -- the optional "grab the pickaxe" milestone's target. */
    public static final Identifier STARTER_PICKAXE_ID = Identifier.fromNamespaceAndPath("minecraft", "stone_pickaxe");

    public static void registerAll() {
        QuestMilestone setActive = QuestMilestone.simple("set_active", "Set this quest as your active quest");
        // Hand-built, not QuestMilestone.gather(...) -- that factory always hardcodes optional=false;
        // this is deliberately an OPTIONAL gather with a real bonus-gold-per-unit reward (see
        // QuestEngine#addGatherReward), so a player who skips the chest entirely still finishes the
        // quest, just without the bonus.
        QuestMilestone grabPickaxe = new QuestMilestone("grab_pickaxe", "Grab the stone pickaxe from the chest (optional bonus)",
                true, 1, Optional.empty(), Optional.of(STARTER_PICKAXE_ID.toString()), true, false, false,
                25, false, false, true, false, Optional.empty());
        QuestMilestone reachPortal = QuestMilestone.simple("reach_portal", "Step through the exit portal when you're ready");

        QuestDefinition welcome = new QuestDefinition(
                WELCOME_QUEST_ID, "Welcome to Kyngdoms",
                "Your first real task -- get settled in before you head out into the world.",
                "arryval", List.of(setActive, grabPickaxe, reachPortal),
                List.of(QuestReward.gold(75)), QuestTimeLimit.NONE)
                .withUnlocksQuestId(FOOTING_QUEST_ID);
        Myssions.registerQuestDefinition(welcome);

        QuestMilestone settleIn = QuestMilestone.simple("settle_in", "Choose and settle into your new home town");
        QuestDefinition footing = new QuestDefinition(
                FOOTING_QUEST_ID, "Finding Your Footing",
                "You picked a town -- now it's really home. Your Recallcinite Totem is bound there, so you'll always be able to find your way back.",
                "arryval", List.of(settleIn),
                List.of(QuestReward.gold(50), QuestReward.item(Identifier.fromNamespaceAndPath("minecraft", "bread"), 16)),
                QuestTimeLimit.NONE);
        Myssions.registerQuestDefinition(footing);
    }
}
