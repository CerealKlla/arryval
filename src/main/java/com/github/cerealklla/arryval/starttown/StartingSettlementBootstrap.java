package com.github.cerealklla.arryval.starttown;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

/** Loads (or, on first boot, generates) this server's starting-settlement location set -- mirrors every other {@code PersistedServerStore} consumer's own identical bootstrap shape (e.g. Lyfe's {@code socket.RunewordBootstrap}). */
public final class StartingSettlementBootstrap {

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        StartingSettlementStore.loadOrGenerate(event.getServer());
    }
}
