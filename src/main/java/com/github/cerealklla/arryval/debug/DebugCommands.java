package com.github.cerealklla.arryval.debug;

import com.github.cerealklla.arryval.registration.ModAttachments;
import com.github.cerealklla.arryval.starttown.StartingSettlementCoreState;
import com.github.cerealklla.lyfe.api.Lyfe;
import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** DEBUG ONLY -- deliberately no permission gate, same precedent as every other sibling mod's own {@code debug.DebugCommands}. */
public final class DebugCommands {

    private DebugCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("arryval")
                .requires(source -> true)
                .then(Commands.literal("fixhomevillage")
                        .executes(ctx -> fixHomeVillage(ctx.getSource()))));
    }

    /**
     * One-off remediation for a real live bug -- {@link StartingSettlementCoreState} was added
     * 2026-11-08, well after plenty of players (including this mod's own author) had already bound
     * their Recallcinite Totem; that attachment is only ever written at bind time, so anyone who
     * bound before it existed is permanently stuck with an empty record, silently stalling "Lending
     * a Hand" forever (its tracker, {@code starttown.VillageQuestTracker}, just no-ops on an empty
     * home village with no error at all). Real report, 2026-11-17: "I just completed a quest and
     * still have 0/10 progress on Lending a Hand." Re-derives the record from wherever the player's
     * Totem is *already* bound (via the new {@code api.Lyfe#getRecallciniteBoundLocation}) using the
     * exact same resolution {@link StartingSettlementCoreState#recordFor} itself uses -- not a
     * fabricated or guessed location. Safe to re-run; a player with an already-populated record just
     * gets it refreshed to the same bind position.
     */
    private static int fixHomeVillage(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Only a player can run this."));
            return 0;
        }
        var bound = Lyfe.getRecallciniteBoundLocation(player);
        if (bound.isEmpty()) {
            source.sendFailure(Component.literal("Recallcinite Totem isn't bound anywhere yet."));
            return 0;
        }
        GlobalPos pos = bound.get();
        ServerLevel level = ((ServerLevel) player.level()).getServer().getLevel(pos.dimension());
        if (level == null) {
            source.sendFailure(Component.literal("Bound dimension isn't loaded."));
            return 0;
        }
        StartingSettlementCoreState.recordFor(player, level, pos.pos());
        boolean resolved = player.getData(ModAttachments.STARTING_SETTLEMENT_CORE_STATE).settlementCoreId().isPresent();
        if (resolved) {
            source.sendSuccess(() -> Component.literal("Home village re-bound from your Totem's location."), false);
            return 1;
        }
        source.sendFailure(Component.literal("Totem's bound location doesn't resolve to any known settlement."));
        return 0;
    }
}
