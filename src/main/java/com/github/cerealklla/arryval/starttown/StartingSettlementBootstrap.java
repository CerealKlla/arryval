package com.github.cerealklla.arryval.starttown;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

/**
 * Loads (or, on first boot, generates) this server's starting-settlement location set -- mirrors
 * every other {@code PersistedServerStore} consumer's own identical bootstrap shape (e.g. Lyfe's
 * {@code socket.RunewordBootstrap}), <b>except</b> for when it fires.
 *
 * <p><b>Moved off {@code ServerStartingEvent}, 2026-11-09</b>, after a real live report: a brand
 * new singleplayer world froze for "several minutes" on world creation. {@link
 * StartingSettlementStore#discover} force-loads real terrain/structure generation for up to {@link
 * StartingSettlementStore#DESIRED_CANDIDATE_COUNT} candidate locations (each up to an 81-chunk
 * widened retry) -- real, expensive, and on purpose (see that class's own doc), but {@code
 * ServerStartingEvent} fires as part of the integrated server's own blocking init sequence, which a
 * singleplayer client's "Loading world" screen waits on as a whole before ever letting the player
 * in. On a dedicated server this cost is invisible (nobody can be connected yet during boot); in
 * singleplayer, server boot and the player's own first moment in the world are the same moment, so
 * the exact same cost reads as the client itself being frozen.
 *
 * <p><b>Split by server kind, same day, later</b> -- explicit follow-up request: "run it when the
 * server first starts up ... so there is something for the player to interact with ... immediately."
 * A dedicated server has no such conflict at all (nobody can be connected during its own boot
 * sequence), so there's no real reason to defer there -- only singleplayer actually needs the
 * deferral this class exists for. {@code MinecraftServer#isDedicatedServer()} picks the right one:
 * dedicated runs the real discovery synchronously at {@code ServerStartingEvent} (the literal
 * "when the server first starts up" the request asked for, safe here since no client is waiting on
 * it), singleplayer keeps the {@code PlayerLoggedInEvent} deferral from the fix above.
 */
public final class StartingSettlementBootstrap {

    private boolean loaded = false;

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        if (!event.getServer().isDedicatedServer()) {
            return; // See this class's own doc -- singleplayer waits for PlayerLoggedInEvent instead.
        }
        loaded = true;
        StartingSettlementStore.loadOrGenerate(event.getServer());
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (loaded || !(event.getEntity() instanceof ServerPlayer serverPlayer)) {
            return;
        }
        loaded = true;
        StartingSettlementStore.loadOrGenerate(((net.minecraft.server.level.ServerLevel) serverPlayer.level()).getServer());
    }
}
