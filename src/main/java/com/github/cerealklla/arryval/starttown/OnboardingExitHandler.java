package com.github.cerealklla.arryval.starttown;

import java.util.List;

import com.github.cerealklla.myssions.api.Myssions;
import com.github.cerealklla.myssions.quest.QuestRewardMenu;
import com.github.cerealklla.tinywyrlds.instance.WyrldExitHandler;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Claims a Wyrld exit only for a player currently mid-Arryval-tutorial (see {@link
 * PendingOnboarding}) -- never intercepts an unrelated Wyrld exit (a Myssions quest dungeon, a
 * settlement Guardhouse's own weekly entrance), since those players were never marked as awaiting a
 * pick. Resolving against the real overworld -- settlements only ever exist there, never inside
 * {@code wyrldprint.space.WyrldSpaceDimension} itself, regardless of which level the player happened
 * to be leaving from.
 *
 * <p><b>Proactive popup added 2026-11-06</b>, explicit user design: "we should use the player
 * pingable and have it grant the destination popup just before the player actually reaches the
 * portal." {@link #onApproach} fires repeatedly while a player is merely near a placed {@code
 * ExitPortalBlock} (real {@code ping.Pingable} dispatch, see {@code ExitPortalPingMarkerEntity} for
 * its own 4-block trigger radius) -- a real live bug, found immediately on first retest ("it's
 * spamming me"): an earlier version re-sent the same picker payload on a timed cooldown while the
 * player simply stood nearby, which just reopens the same screen every few seconds instead of once.
 * Fixed to a true one-shot per approach via {@link PendingOnboarding#hasBeenProactivelyPrompted} --
 * see that method's own doc. {@link #tryHandle} (actual portal contact) is unaffected and still
 * fires unconditionally every time, as the real fallback for a player who reaches the portal before
 * ever being pinged.
 */
public final class OnboardingExitHandler implements WyrldExitHandler {

    @Override
    public boolean tryHandle(ServerPlayer player) {
        if (!PendingOnboarding.isAwaitingPick(player.getUUID())) {
            return false;
        }
        resolveAndSendPicker(player);
        return true;
    }

    @Override
    public void onApproach(ServerPlayer player) {
        if (!PendingOnboarding.isAwaitingPick(player.getUUID())
                || PendingOnboarding.hasBeenProactivelyPrompted(player.getUUID())) {
            return;
        }
        PendingOnboarding.markProactivelyPrompted(player.getUUID());
        resolveAndSendPicker(player);
    }

    private void resolveAndSendPicker(ServerPlayer player) {
        // Real bug, found live 2026-11-07: "the Welcome to Kyngdoms quest seems to have no one to
        // turn the quest in to" -- correct, since Arryval never built a real quest-giver NPC
        // interaction for it (its issuerId, "arryval," is just a flavor string, not a real entity to
        // walk up to). Every other quest in the suite needs an explicit turn-in (quest.QuestTurnIn,
        // an NPC interaction), but a scripted tutorial quest has no NPC left to provide that once
        // the player's already leaving the Wyrld -- so this is the one place left to auto-trigger
        // it, the instant its last milestone completes. tryCompleteQuest itself opens the reward GUI
        // (a real gold reward exists here) with no further action needed from the player.
        PendingOnboarding.getWelcomeQuestInstanceId(player.getUUID())
                .ifPresent(instanceId -> {
                    Myssions.completeMilestone(player, instanceId, "reach_portal");
                    Myssions.tryCompleteQuest(player, instanceId);
                });
        // Real bug, found live 2026-11-07: "I got the quest completion menu but before I could click
        // accept the teleport destination menu popped up and closed the quest completion menu" --
        // tryCompleteQuest just above opens a real vanilla container menu (QuestRewardMenu) when a
        // gold reward (as here) exists; sending the picker payload in the very same tick replaces
        // that screen outright, same as any other menu-open does. If the reward GUI actually opened,
        // defer the picker until it closes (see onContainerClose below) instead of sending it now.
        if (player.containerMenu instanceof QuestRewardMenu) {
            PendingOnboarding.markPickerPendingAfterReward(player.getUUID());
            return;
        }
        sendPicker(player);
    }

    /** See {@link #resolveAndSendPicker}'s own doc -- fires once the Welcome quest's reward GUI actually closes, for whatever reason (fully collected, or just closed early; either way nothing left to show the player before the picker). */
    @SubscribeEvent
    public void onContainerClose(PlayerContainerEvent.Close event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getContainer() instanceof QuestRewardMenu)) {
            return;
        }
        if (PendingOnboarding.consumePickerPendingAfterReward(player.getUUID())) {
            sendPicker(player);
        }
    }

    private void sendPicker(ServerPlayer player) {
        ServerLevel overworld = ((ServerLevel) player.level()).getServer().overworld();
        List<StartingSettlementResolver.Candidate> candidates = StartingSettlementResolver.findStartingCandidates(overworld);
        PendingOnboarding.cacheCandidates(player.getUUID(), candidates);

        List<OpenStartingSettlementPickerPayload.Entry> entries = candidates.stream()
                .map(c -> new OpenStartingSettlementPickerPayload.Entry(c.name()))
                .toList();
        PacketDistributor.sendToPlayer(player, new OpenStartingSettlementPickerPayload(entries));
    }
}
