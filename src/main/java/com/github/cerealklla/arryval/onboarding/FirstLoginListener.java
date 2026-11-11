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
 * <p><b>Stale as of 2026-11-11, corrected</b>: this used to require a one-time admin setup on the
 * server for the recipe itself ({@code /tinywyrlds recipe create/addfriendly/setentrance}) -- that
 * entire category of "someone has to run an admin command before this works" is now gone.
 * {@code onboarding.TutorialRecipeBootstrap} creates/repairs the {@code WelcomeTutorial} recipe
 * (including pinning it to {@link com.github.cerealklla.tinywyrlds.wyrldprint.recipe.WyrldRecipe
 * #useLatestTutorialPrint()} rather than a fixed entrance name) on every single server boot, no
 * admin action needed, ever -- see its own doc and {@code TinyWyrlds#ensureRecipeExists}. The only
 * two things still outside code: the tutorial room itself needs to be captured and saved as a
 * {@code TUTORIAL}-type WyrldPrint (whatever name) at least once per server -- real level-design
 * content, which genuinely can't be generated from nothing -- and the starter-loot item list for
 * its "common loot" marker is still an open, undecided design question (see Arryval's own
 * decisions.md).
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
            ServerLevel overworld = ((ServerLevel) serverPlayer.level()).getServer().overworld();
            LevelData.RespawnData respawn = overworld.getRespawnData();
            ServerLevel spawnLevel = overworld.getServer().getLevel(respawn.dimension());
            if (spawnLevel == null) {
                spawnLevel = overworld;
            }
            BlockPos spawnPos = respawn.pos();
            serverPlayer.teleportTo(spawnLevel, spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5,
                    Set.of(), respawn.yaw(), respawn.pitch(), true);
            return;
        }

        PendingOnboarding.markAwaitingPick(serverPlayer.getUUID());
        // Request #5, 2026-11-06, corrected same day: the Welcome quest is no longer offered here at
        // all -- explicit user correction, "it should only offer the quest after the mic is
        // configured or confirmed not available." It's now offered from TutorialGuideEntity#
        // handleIntercept, the instant the mic-check exchange resolves (see that method's own doc).
        ServerLevel level = (ServerLevel) serverPlayer.level();
        UUID entranceId = UUID.randomUUID();
        PendingOnboarding.setTutorialEntranceId(serverPlayer.getUUID(), entranceId);
        TinyWyrlds.spawnEntrance(level, serverPlayer.blockPosition(), entranceId, TUTORIAL_RECIPE_NAME, serverPlayer.getUUID());
    }
}
