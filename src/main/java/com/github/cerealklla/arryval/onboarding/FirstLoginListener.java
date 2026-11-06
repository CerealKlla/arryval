package com.github.cerealklla.arryval.onboarding;

import java.util.UUID;

import com.github.cerealklla.arryval.registration.ModAttachments;
import com.github.cerealklla.arryval.starttown.PendingOnboarding;
import com.github.cerealklla.tinywyrlds.api.TinyWyrlds;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Places a brand-new player into their own private "WelcomeTutorial" Wyrld on their true first
 * login ever -- never again on a later login or after death (see {@code
 * ModAttachments#TUTORIAL_GRANT_STATE}'s own {@code copyOnDeath()} note for why death specifically
 * needed care, same pattern Kyt's own {@code welcome.WelcomeListener} already established).
 *
 * <p>Spawns a fresh {@code WyrldEntranceEntity} directly on the player's own position with a
 * brand-new random {@code entranceId} -- the existing {@code WyrldEntranceProximityTicker} picks
 * it up and generates/enters on literally the next tick, with zero new TinyWyrlds plumbing needed.
 * A fresh id per player (never cached/reused) guarantees each new player gets their own private
 * generation, consistent with how {@code OneOffGenerator.generate} already always allocates a
 * fresh, non-shared {@code WyrldInstanceRecord} -- see TinyWyrlds' own {@code api.TinyWyrlds}
 * class doc for the full research behind this.
 *
 * <p>Requires a one-time admin setup on the server, not built as code here (see this project's own
 * CLAUDE.md "prefer a one-time fix over permanent code" convention): {@code /tinywyrlds recipe
 * create WelcomeTutorial 0 0}, {@code /tinywyrlds recipe addfriendly WelcomeTutorial
 * arryval:tutorial_guide 1}, whatever {@code /tinywyrlds recipe addloot} commands populate the
 * captured Entrance's one "common loot" marker, and critically {@code /tinywyrlds recipe
 * setentrance WelcomeTutorial WelcomeTutorial1} -- without that last one, generation picks
 * uniformly at random among EVERY saved ENTRANCE WyrldPrint, not just this one (real bug found
 * live, 2026-11-06 -- see TinyWyrlds' own {@code generate.OneOffGenerator#planPlacements} doc).
 */
public final class FirstLoginListener {

    public static final String TUTORIAL_RECIPE_NAME = "WelcomeTutorial";

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        TutorialGrantState state = player.getData(ModAttachments.TUTORIAL_GRANT_STATE);
        if (state.granted()) {
            return;
        }
        player.setData(ModAttachments.TUTORIAL_GRANT_STATE, new TutorialGrantState(true));

        PendingOnboarding.markAwaitingPick(serverPlayer.getUUID());
        ServerLevel level = (ServerLevel) serverPlayer.level();
        TinyWyrlds.spawnEntrance(level, serverPlayer.blockPosition(), UUID.randomUUID(), TUTORIAL_RECIPE_NAME, serverPlayer.getUUID());
    }
}
