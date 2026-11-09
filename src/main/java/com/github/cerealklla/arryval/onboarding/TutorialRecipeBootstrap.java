package com.github.cerealklla.arryval.onboarding;

import com.github.cerealklla.arryval.registration.ModEntities;
import com.github.cerealklla.tinywyrlds.api.TinyWyrlds;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

/**
 * Ensures the {@code WelcomeTutorial} WyrldRecipe exists -- explicit user instruction, 2026-11-09,
 * after this exact gap bit a real player twice: a {@code WyrldRecipe} is per-world persisted data
 * (same as everything else TinyWyrlds owns), so the one-time manual admin setup this previously
 * required (<code>/tinywyrlds recipe create/addfriendly/setentrance</code>) had to be done by hand
 * on Production, then AGAIN on a brand-new singleplayer world, with no way to "pre-ship" it. {@link
 * TinyWyrlds#ensureRecipeExists} is idempotent (a no-op once the recipe exists), so this can safely
 * run on every boot of every world forever -- it can never go missing again regardless of how the
 * world/server came to exist.
 */
public final class TutorialRecipeBootstrap {

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        TinyWyrlds.ensureRecipeExists(
                FirstLoginListener.TUTORIAL_RECIPE_NAME, 0, 0,
                net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(ModEntities.TUTORIAL_GUIDE.get()),
                1, "WelcomeTutorial1");
    }
}
