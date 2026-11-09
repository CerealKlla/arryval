package com.github.cerealklla.arryval.starttown;

import java.util.List;
import java.util.UUID;

import com.github.cerealklla.arryval.registration.ModAttachments;
import com.github.cerealklla.coryspondance.api.Coryspondance;
import com.github.cerealklla.settlemynts.api.Settlemynts;
import com.github.cerealklla.terrytories.api.Terrytories;
import com.github.cerealklla.terrytories.reputation.ReputationHooks;
import com.github.cerealklla.terrytories.reputation.Standing;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * "Once Kyngdoms exists, I'd like to automatically send a letter through our mail mod from the NPC
 * mayor of the town the player starts out at, offering them the opportunity to go and found their
 * own settlement. The letter will contain a settlement claim flag... This could happen the first
 * time a player achieves 'Friendly' status with any natural NPC settlement." -- explicit user spec,
 * 2026-11-08.
 *
 * <p>Registers against Terrytories' generic {@code reputation.ReputationHooks} (this mod depends on
 * Terrytories for exactly this -- Terrytories itself never depends on Arryval, see Terrytories' own
 * design-document.md "Dependency direction" section) rather than Terrytories knowing anything about
 * tutorials/onboarding itself. Fires once ever per player, the first time ANY natural settlement
 * (never a player-founded one -- {@code api.Settlemynts#isNaturalSettlement}) crosses into {@link
 * Standing#FRIENDLY}, regardless of which specific natural settlement it was or whether it's the
 * player's own starting village.
 *
 * <p><b>No real mayor NPC exists anywhere in this suite</b> (confirmed, not assumed -- Myssions'
 * own "Mayor" quests are issued via a Plot Config Sign for the identical reason) -- per explicit
 * user instruction, the letter is simply signed "Mayor of &lt;settlement name&gt;" with no NPC
 * identity lookup at all. The settlement claim flag attached is the suite's existing real item
 * ({@code api.Settlemynts#settlementClaimFlagStack}, already granted via Kyt's "Dev" Kyt debug
 * contribution) -- explicit user instruction not to create a new one.
 */
public final class FoundingLetterListener {

    public FoundingLetterListener() {
        ReputationHooks.register(this::onStandingChanged);
    }

    private void onStandingChanged(UUID holderId, UUID factionId, Standing oldStanding, Standing newStanding) {
        if (newStanding != Standing.FRIENDLY) {
            return;
        }
        // Terrytories' holder concept is generic (a player, a settlement, or a Kingdom can all hold
        // reputation with something -- see reputation.HolderReputation's own doc). This letter only
        // ever cares about a real player's own standing; a non-player holder (e.g. a future
        // settlement-held reputation with its controlling Kingdom) resolves to no online player and
        // is correctly ignored here.
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(holderId);
        if (player == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (player.getData(ModAttachments.FOUNDING_LETTER_STATE).sent()) {
            return;
        }
        // This hook now also fires for minted (non-settlement) Factions -- e.g. a Kingdom, which
        // gets its own small reputation trickle from quests done in settlements it controls (see
        // Terrytories' own reputation.QuestReputationListener). Only a real natural SETTLEMENT
        // reaching Friendly matters here -- a Kingdom crossing into Friendly is a different, much
        // bigger reputation value and not what this feature is about.
        if (Terrytories.isMintedFaction(factionId)) {
            return;
        }
        UUID settlementCoreId = factionId;
        if (!Settlemynts.isNaturalSettlement(level, settlementCoreId)) {
            return;
        }
        String settlementName = Settlemynts.getAnySettlementName(level, settlementCoreId).orElse("your town");
        player.setData(ModAttachments.FOUNDING_LETTER_STATE, new FoundingLetterState(true));

        String message = "You've done right by " + settlementName + ", and word of it has reached me. "
                + "If you're ever looking to make a name for yourself, there's always room for one more "
                + "settlement out there. Take this claim flag, find a spot far from anyone else's land, "
                + "and plant it -- the rest will follow.";
        Coryspondance.sendSystemMail(level.getServer(), player.getUUID(), "Mayor of " + settlementName,
                message, List.of(Settlemynts.settlementClaimFlagStack(1)));
    }
}
