package com.github.cerealklla.arryval.onboarding;

import com.github.cerealklla.arryval.registration.ModEntities;
import com.github.cerealklla.tinywyrlds.api.TinyWyrlds;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

/**
 * Ensures the {@code WelcomeTutorial} WyrldRecipe exists AND carries its full intended content --
 * explicit user instruction, 2026-11-09, after this exact gap bit a real player twice (missing
 * recipe), then a third time (recipe present, but missing the enemy pool the room's own 4
 * {@code enemy_spawn} markers need -- a player's own game instance had already created this recipe
 * during an earlier test, before the enemy pool was added server-side, so it never picked up the
 * fix no matter how many times Production's own copy was corrected). {@code
 * minecraft:zombie} is the explicit, user-chosen enemy for those 4 markers. {@link
 * TinyWyrlds#ensureRecipeExists} both creates-if-missing and tops up either pool if it's still
 * empty on an already-existing recipe, so this can safely run on every boot of every world forever
 * -- neither the recipe nor its content can go missing again regardless of how the world/server
 * came to exist or what it already had saved.
 *
 * <p>Switched from a pinned {@code entranceName} ("WelcomeTutorial1") to {@code
 * useLatestTutorialPrint = true}, 2026-11-11 -- explicit user correction: pinning by name meant
 * re-capturing the tutorial room required a new one-time {@code /tinywyrlds recipe setentrance}
 * admin command every single time, which is exactly the "one-time admin setup" pattern that should
 * never be leveraged for anything the tutorial depends on. Now the room just needs to be saved as
 * a {@code TUTORIAL}-type WyrldPrint (any name) and the next player login automatically uses
 * whichever one was captured most recently -- see {@code tinywyrlds.wyrldprint.WyrldPrintType
 * #TUTORIAL}'s own doc.
 */
public final class TutorialRecipeBootstrap {

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        TinyWyrlds.ensureRecipeExists(
                FirstLoginListener.TUTORIAL_RECIPE_NAME, 0, 0,
                net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(ModEntities.TUTORIAL_GUIDE.get()), 1,
                net.minecraft.resources.Identifier.withDefaultNamespace("zombie"), 1,
                true);
    }
}
