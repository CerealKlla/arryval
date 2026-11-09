package com.github.cerealklla.arryval.tutorial;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Real bug, found live 2026-11-06: right-clicking the Guide fell straight through to vanilla's own
 * default {@code Villager#mobInteract} (trading) since nothing claimed the interaction -- opening a
 * blank vanilla trade screen titled "Villager" (its generic fallback display name) and covering the
 * Guide's own floating nameplate while open. The Guide has no click-driven interaction at all (its
 * whole conversation happens ambiently via Audyble's voice/text pipeline, see {@code
 * TutorialGuideEntity#customServerAiStep}) -- this unconditionally cancels the vanilla interaction
 * instead, same claim-and-cancel shape as Myssions' own {@code quest.QuestNpcTalkListener}.
 */
public final class TutorialGuideInteractListener {

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer) || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (event.getTarget() instanceof TutorialGuideEntity) {
            event.setCanceled(true);
        }
    }
}
