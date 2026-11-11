package com.github.cerealklla.arryval.starttown;

import com.github.cerealklla.arryval.tutorial.TutorialQuests;
import com.github.cerealklla.myssions.api.Myssions;
import com.github.cerealklla.settlemynts.api.Settlemynts;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * Tracks the "Finding Your Footing" quest's real, achievable objective -- explicit user spec,
 * 2026-11-07, replacing the old "Choose and settle into your new home town" milestone ("this quest
 * makes no sense at all," and worse, nothing could ever complete it -- see {@code
 * tutorial.TutorialQuests}' own doc): "if the quest was to talk to N villagers, that would make
 * sense. That's an actually achievable objective." Counts any {@link Villager} interaction (plain
 * natural-village NPCs and every one of Settlemynts'/Myssions' own {@code Villager} subclasses
 * alike -- every one of them, generically, with no special-casing needed), deduped per distinct NPC
 * via {@link PendingOnboarding#recordTalkedToNpc}, up to {@link #REQUIRED_COUNT}.
 *
 * <p><b>Reworked 2026-11-09, real live report: "pressing 'G' doesn't count as 'talking to a
 * villager.'"</b> The original version listened to vanilla's {@code PlayerInteractEvent
 * .EntityInteract} (with the 2026-11-07 fix below still correct for what it covered) -- but that
 * event only ever fires for a plain right-click. Settlemynts' "G" key (its NPC Menu shortcut) sends
 * its own custom network payload straight to the server and never touches that event at all, so a
 * player who always used "G" (a perfectly normal way to talk to an NPC, not a workaround) could
 * never make progress no matter how many different villagers they pressed it on. Now registered
 * against {@code Settlemynts#registerNpcTalkedToHook} instead, which fires from {@code
 * SettlemyntsMod#openNpcMenu} itself -- the one real choke point both the "G" key and a right-click
 * already funnel through (confirmed directly in Settlemynts' own source), so this single
 * registration covers both triggers identically and can't drift out of sync with either one again.
 */
public final class VillagerTalkTracker {

    public static final int REQUIRED_COUNT = 5;

    public VillagerTalkTracker() {
        Settlemynts.registerNpcTalkedToHook(this::onNpcTalkedTo);
    }

    private void onNpcTalkedTo(ServerPlayer player, net.minecraft.world.entity.LivingEntity npc) {
        if (!(npc instanceof Villager villager)) {
            return;
        }
        Myssions.findInProgressInstanceId(player, TutorialQuests.FOOTING_QUEST_ID).ifPresent(instanceId -> {
            if (PendingOnboarding.recordTalkedToNpc(player.getUUID(), villager.getId())) {
                Myssions.reportMilestoneProgress(player, instanceId, "talk_to_villagers", 1);
                // Real live report, 2026-11-09: "this was granted upon spawning in a town, i didn't
                // get it from an NPC, so how am I supposed to be able to turn it in?" -- same root
                // cause already found and fixed for the Welcome quest (see starttown
                // .OnboardingExitHandler#resolveAndSendPicker's own doc): this quest has no real
                // quest-giver NPC to walk up to, since it's chain-unlocked automatically, not handed
                // out by anyone. A no-op unless this call just pushed the milestone to its required
                // count (QuestEngine#tryCompleteQuest itself checks the instance's real status, so
                // calling it unconditionally here is safe).
                Myssions.tryCompleteQuest(player, instanceId);
            }
        });
    }
}
