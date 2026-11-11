package com.github.cerealklla.arryval.onboarding;

import java.util.Set;
import java.util.UUID;

import com.github.cerealklla.arryval.config.ArryvalConfig;
import com.github.cerealklla.arryval.registration.ModAttachments;
import com.github.cerealklla.arryval.starttown.PendingOnboarding;
import com.github.cerealklla.tinywyrlds.api.TinyWyrlds;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.LevelData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Places a brand-new player into their own private "WelcomeTutorial" Wyrld on their true first
 * login ever -- never again on a later login or after death (see {@code
 * ModAttachments#TUTORIAL_GRANT_STATE}'s own {@code copyOnDeath()} note for why death specifically
 * needed care, same pattern Kyt's own now-removed {@code welcome.WelcomeListener} once established).
 *
 * <p>Calls straight into {@code api.TinyWyrlds#enterEntranceDirect} with a fresh, random {@code
 * entranceId} -- generates (or on a later re-login, re-enters) the Wyrld and teleports the player
 * immediately, with nothing ever placed in the world. A fresh id per player (never cached/reused)
 * guarantees each new player gets their own private generation, consistent with how {@code
 * OneOffGenerator.generate} already always allocates a fresh, non-shared {@code
 * WyrldInstanceRecord} -- see TinyWyrlds' own {@code api.TinyWyrlds} class doc for the full
 * research behind this.
 *
 * <p><b>Corrected 2026-11-11, real incident</b>: this used to spawn a permanent, invisible {@code
 * WyrldEntranceEntity} directly on the player's own login position, relying on {@code
 * WyrldEntranceProximityTicker} to pick it up on the next tick. That entity never despawns -- a
 * veteran player's account (never previously flagged {@code granted()} under this newer
 * attachment) got treated as a genuine first login mid-town, got an invisible permanent portal
 * planted on their own plot, and then got trapped: the generated room (TinyWyrlds' own TUTORIAL
 * print was itself mistyped as a stale {@code ENTRANCE} at the time, see TinyWyrlds' own fix) had
 * no exit, and leaving via {@code /tinywyrlds leave} returned them to the exact spot that
 * re-triggered it, forever. Explicit user instruction: "there is no scenario where a permanent
 * tutorial entrance makes sense, especially an invisible one... Manually teleport the player into
 * the tutorial." No entity is ever spawned for this anymore.
 *
 * <p>{@code onboarding.TutorialRecipeBootstrap} creates/repairs the {@code WelcomeTutorial} recipe
 * (including pinning it to {@link com.github.cerealklla.tinywyrlds.wyrldprint.recipe.WyrldRecipe
 * #useLatestTutorialPrint()} rather than a fixed entrance name) on every single server boot, no
 * admin action needed, ever -- see its own doc and {@code TinyWyrlds#ensureRecipeExists}. If no
 * real {@code TUTORIAL}-type WyrldPrint has ever been saved, generation now refuses outright
 * (TinyWyrlds' own fix, same incident) rather than substituting an unrelated, possibly exit-less
 * dungeon -- {@link #onPlayerLoggedIn} treats that refusal exactly like {@code SKIP_ONBOARDING}
 * and places the player at the world's own spawn point instead of leaving them stranded mid-call.
 * The starter-loot item list for the room's "common loot" marker is still an open, undecided
 * design question (see Arryval's own decisions.md).
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
            // Not this player's true first login -- but if they haven't actually picked a starting
            // settlement yet, re-mark them as awaiting a pick every time they log back in. Real bug,
            // found live 2026-11-06: PendingOnboarding's own "awaiting pick" flag is deliberately
            // in-memory (see its own doc), and used to be set ONLY here, in the once-ever first-login
            // branch below -- so any server/client restart after that one moment silently broke the
            // exit-portal picker for the rest of that account's life, even mid-tutorial. This check
            // makes it survive any number of restarts instead.
            if (!player.getData(ModAttachments.SETTLEMENT_CHOSEN_STATE).chosen()) {
                PendingOnboarding.markAwaitingPick(serverPlayer.getUUID());
            }
            return;
        }
        player.setData(ModAttachments.TUTORIAL_GRANT_STATE, new TutorialGrantState(true));

        if (ArryvalConfig.SKIP_ONBOARDING.get()) {
            // Dev convenience (see ArryvalConfig's own doc): skip the private tutorial Wyrld, the
            // guide NPC, and the starting-settlement picker entirely, and just drop the player at the
            // world's own spawn point. Also mark SETTLEMENT_CHOSEN_STATE so the granted()-branch
            // re-mark-every-login fix above (see its own comment) never re-flags this player as
            // awaiting a pick on a later login either -- there is no picker coming for them to await.
            player.setData(ModAttachments.SETTLEMENT_CHOSEN_STATE, new SettlementChosenState(true));
            teleportToWorldSpawn(serverPlayer);
            return;
        }

        PendingOnboarding.markAwaitingPick(serverPlayer.getUUID());
        // Request #5, 2026-11-06, corrected same day: the Welcome quest is no longer offered here at
        // all -- explicit user correction, "it should only offer the quest after the mic is
        // configured or confirmed not available." It's now offered from TutorialGuideEntity#
        // handleIntercept, the instant the mic-check exchange resolves (see that method's own doc).
        UUID entranceId = UUID.randomUUID();
        PendingOnboarding.setTutorialEntranceId(serverPlayer.getUUID(), entranceId);
        if (!TinyWyrlds.enterEntranceDirect(serverPlayer, entranceId, TUTORIAL_RECIPE_NAME)) {
            // No real TUTORIAL WyrldPrint exists yet (or Wyrld Space itself isn't up) -- never leave
            // a player stranded mid-login over missing content. They still go through the normal
            // starttown picker (already marked above), just without the tutorial room itself.
            teleportToWorldSpawn(serverPlayer);
        }
    }

    private void teleportToWorldSpawn(ServerPlayer serverPlayer) {
        ServerLevel overworld = ((ServerLevel) serverPlayer.level()).getServer().overworld();
        LevelData.RespawnData respawn = overworld.getRespawnData();
        ServerLevel spawnLevel = overworld.getServer().getLevel(respawn.dimension());
        if (spawnLevel == null) {
            spawnLevel = overworld;
        }
        BlockPos spawnPos = respawn.pos();
        serverPlayer.teleportTo(spawnLevel, spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5,
                Set.of(), respawn.yaw(), respawn.pitch(), true);
    }
}
