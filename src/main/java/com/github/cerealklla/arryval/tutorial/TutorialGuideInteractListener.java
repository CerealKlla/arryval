package com.github.cerealklla.arryval.tutorial;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Right-clicking the tutorial guide advances its script -- same shape as Myssions' own {@code quest.QuestNpcTalkListener}. */
public final class TutorialGuideInteractListener {

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (!(event.getTarget() instanceof TutorialGuideEntity guide)) {
            return;
        }
        if (guide.talkTo(player)) {
            event.setCanceled(true);
        }
    }
}
