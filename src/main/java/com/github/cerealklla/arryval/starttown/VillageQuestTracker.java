package com.github.cerealklla.arryval.starttown;

import com.github.cerealklla.arryval.registration.ModAttachments;
import com.github.cerealklla.arryval.tutorial.TutorialQuests;
import com.github.cerealklla.myssions.api.Myssions;
import com.github.cerealklla.myssions.quest.QuestEndHooks;
import com.github.cerealklla.settlemynts.api.Settlemynts;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Tracks the "Lending a Hand" quest's "complete 10 quests for your new home" milestone -- explicit
 * user spec, 2026-11-08: after Finding Your Footing, help out around the village the player actually
 * started in (any quest, from any mod, not just Arryval's own) rather than inventing a bespoke quest
 * type. Registers against Myssions' new {@link QuestEndHooks#registerGlobal} (added the same day
 * specifically for this and Terrytories' per-settlement reputation) since neither this mod nor
 * Myssions itself can enumerate every quest id that will ever exist.
 *
 * <p>Only counts a genuine success ({@link QuestEndHooks.EndReason#COMPLETED}) whose issuing plot
 * (see {@code api.Myssions#getIssuingPlotId}) resolves to the <em>same</em> settlement the player's
 * own Totem is bound to (see {@link StartingSettlementCoreState}) -- a quest completed for a
 * different village doesn't count, and a quest with no issuing plot at all (an off-site/wilderness
 * quest with no real settlement tie) doesn't either.
 */
public final class VillageQuestTracker {

    public static final int REQUIRED_COUNT = 10;

    public VillageQuestTracker() {
        QuestEndHooks.registerGlobal(this::onQuestEnded);
    }

    private void onQuestEnded(ServerPlayer player, java.util.UUID instanceId, net.minecraft.resources.Identifier questId, QuestEndHooks.EndReason reason) {
        if (reason != QuestEndHooks.EndReason.COMPLETED || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        var home = player.getData(ModAttachments.STARTING_SETTLEMENT_CORE_STATE).settlementCoreId();
        if (home.isEmpty()) {
            return;
        }
        Myssions.getIssuingPlotId(instanceId)
                .flatMap(plotId -> Settlemynts.findSettlementCoreIdForPlot(level, plotId))
                .filter(home.get()::equals)
                .ifPresent(ignored -> Myssions.findInProgressInstanceId(player, TutorialQuests.LENDING_A_HAND_QUEST_ID)
                        .ifPresent(lendingInstanceId -> {
                            Myssions.reportMilestoneProgress(player, lendingInstanceId, "help_your_village", 1);
                            // Same gap as Finding Your Footing (see VillagerTalkTracker's own doc,
                            // 2026-11-09 report) -- this quest is chain-unlocked automatically too,
                            // no real NPC to turn it in to. Safe to call unconditionally.
                            Myssions.tryCompleteQuest(player, lendingInstanceId);
                        }));
    }
}
