package com.github.cerealklla.arryval.starttown.client;

import com.github.cerealklla.arryval.starttown.OpenStartingSettlementPickerPayload;

import net.minecraft.client.Minecraft;

/** Client-only bridge for the common {@code ArryvalMod#registerPayloads} handler to call into -- same split as Kyt's own {@code ClientKytRequests} (that method runs on both sides, so it never references {@code Screen}/{@code Minecraft} itself). */
public final class ClientStartTownRequests {

    private ClientStartTownRequests() {
    }

    public static void requestPicker(OpenStartingSettlementPickerPayload payload) {
        Minecraft.getInstance().setScreen(new SelectStartingSettlementScreen(payload));
    }
}
