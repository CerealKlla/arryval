package com.github.cerealklla.arryval.starttown;

import com.github.cerealklla.arryval.tutorial.TutorialQuests;
import com.github.cerealklla.myssions.api.Myssions;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.villager.Villager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Tracks the "Finding Your Footing" quest's real, achievable objective -- explicit user spec,
 * 2026-11-07, replacing the old "Choose and settle into your new home town" milestone ("this quest
 * makes no sense at all," and worse, nothing could ever complete it -- see {@code
 * tutorial.TutorialQuests}' own doc): "if the quest was to talk to N villagers, that would make
 * sense. That's an actually achievable objective." Counts any {@link Villager} interaction (plain
 * natural-village NPCs and every one of Settlemynts'/Myssions' own {@code Villager} subclasses
 * alike -- every one of them, generically, with no special-casing needed), deduped per distinct NPC
 * via {@link PendingOnboarding#recordTalkedToNpc}, up to {@link #REQUIRED_COUNT}. Deliberately never
 * cancels the event -- this is a pure side effect, riding alongside whatever the interaction
 * actually does (open a Shop/NPC Menu, start a vanilla trade, etc.), never replacing it.
 */
public final class VillagerTalkTracker {

    public static final int REQUIRED_COUNT = 5;

    // Real bug, found live 2026-11-07: "Talking to them does not grant progress on the 'Finding
    // Your Footing' quest." Root cause: Settlemynts' resident.VillagerShopInteractListener always
    // cancels this exact same event for a plot-tagged Villager (to stop vanilla's own trade screen
    // from also opening) before handing off to the NPC Menu. NeoForge never delivers an already-
    // canceled event to a listener that doesn't explicitly opt in -- this listener's own doc already
    // says it's a "pure side effect, riding alongside whatever the interaction actually does," so it
    // must receive the event regardless of whether something else canceled it first.
    @SubscribeEvent(receiveCanceled = true)
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (!(event.getTarget() instanceof Villager villager)) {
            return;
        }
        Myssions.findInProgressInstanceId(player, TutorialQuests.FOOTING_QUEST_ID).ifPresent(instanceId -> {
            if (PendingOnboarding.recordTalkedToNpc(player.getUUID(), villager.getId())) {
                Myssions.reportMilestoneProgress(player, instanceId, "talk_to_villagers", 1);
            }
        });
    }
}
