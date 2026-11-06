package com.github.cerealklla.arryval.starttown;

import java.util.List;

import com.github.cerealklla.myssions.api.Myssions;
import com.github.cerealklla.tinywyrlds.instance.WyrldExitHandler;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Claims a Wyrld exit only for a player currently mid-Arryval-tutorial (see {@link
 * PendingOnboarding}) -- never intercepts an unrelated Wyrld exit (a Myssions quest dungeon, a
 * settlement Guardhouse's own weekly entrance), since those players were never marked as awaiting a
 * pick. Resolving against the real overworld -- settlements only ever exist there, never inside
 * {@code wyrldprint.space.WyrldSpaceDimension} itself, regardless of which level the player happened
 * to be leaving from.
 */
public final class OnboardingExitHandler implements WyrldExitHandler {

    @Override
    public boolean tryHandle(ServerPlayer player) {
        if (!PendingOnboarding.isAwaitingPick(player.getUUID())) {
            return false;
        }
        PendingOnboarding.getWelcomeQuestInstanceId(player.getUUID())
                .ifPresent(instanceId -> Myssions.completeMilestone(player, instanceId, "reach_portal"));
        ServerLevel overworld = ((ServerLevel) player.level()).getServer().overworld();
        List<StartingSettlementResolver.Candidate> candidates = StartingSettlementResolver.findStartingCandidates(overworld, 8);
        PendingOnboarding.cacheCandidates(player.getUUID(), candidates);

        List<OpenStartingSettlementPickerPayload.Entry> entries = candidates.stream()
                .map(c -> new OpenStartingSettlementPickerPayload.Entry(c.name(), c.distanceBlocks()))
                .toList();
        PacketDistributor.sendToPlayer(player, new OpenStartingSettlementPickerPayload(entries));
        return true;
    }
}
