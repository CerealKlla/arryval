package com.github.cerealklla.arryval.starttown.client;

import com.github.cerealklla.arryval.starttown.OpenStartingSettlementPickerPayload;
import com.github.cerealklla.arryval.starttown.SetStartingSettlementPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;

/**
 * Pick 1 of the (up to 8) resolved starting settlements -- same paged Prev/Next cycle shape as
 * Settlemynts' own {@code guardhouse.client.SelectKytScreen} (this suite's established convention:
 * hand-copy this shape per use case, no shared/generic picker widget exists anywhere). No Cancel/
 * Clear -- the choice is mandatory, this screen only ever opens because the player tried to leave
 * the tutorial Wyrld and hasn't picked yet.
 */
public final class SelectStartingSettlementScreen extends Screen {

    private final OpenStartingSettlementPickerPayload data;
    private int index;

    public SelectStartingSettlementScreen(OpenStartingSettlementPickerPayload data) {
        super(Component.literal("Choose Your Starting Town"));
        this.data = data;
    }

    @Override
    protected void init() {
        boolean hasEntries = !data.entries().isEmpty();
        int centerX = width / 2;
        int y = 70;

        addRenderableWidget(Button.builder(Component.literal("<"), b -> page(-1))
                .bounds(centerX - 110, y, 20, 20).build()).active = hasEntries;
        addRenderableWidget(Button.builder(Component.literal(">"), b -> page(1))
                .bounds(centerX + 90, y, 20, 20).build()).active = hasEntries;

        Button selectButton = addRenderableWidget(Button.builder(Component.literal("Select"), b -> choose())
                .bounds(centerX - 75, y + 30, 150, 20).build());
        selectButton.active = hasEntries;
    }

    private void page(int delta) {
        int count = data.entries().size();
        if (count == 0) {
            return;
        }
        index = Math.floorMod(index + delta, count);
    }

    private void choose() {
        Minecraft.getInstance().getConnection().send(new ServerboundCustomPayloadPacket(new SetStartingSettlementPayload(index)));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int titleWidth = font.width(title);
        graphics.text(font, title, width / 2 - titleWidth / 2, 30, 0xFFFFFFFF);

        int centerX = width / 2;
        int y = 70;
        if (data.entries().isEmpty()) {
            String empty = "No starting towns are available right now.";
            graphics.text(font, empty, centerX - font.width(empty) / 2, y + 4, 0xFFAAAAAA);
            return;
        }
        OpenStartingSettlementPickerPayload.Entry entry = data.entries().get(index);
        String name = entry.name();
        String distance = Math.round(entry.distanceBlocks()) + " blocks from the center of the world";
        String position = "(" + (index + 1) + " / " + data.entries().size() + ")";
        graphics.text(font, name, centerX - font.width(name) / 2, y + 4, 0xFFFFFFFF);
        graphics.text(font, distance, centerX - font.width(distance) / 2, y + 16, 0xFFAAAAAA);
        graphics.text(font, position, centerX - font.width(position) / 2, y + 28, 0xFFAAAAAA);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
