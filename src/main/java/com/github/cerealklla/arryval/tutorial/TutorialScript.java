package com.github.cerealklla.arryval.tutorial;

import java.util.List;

/**
 * The tutorial guide's hand-authored dialogue, in order -- advanced one stage per interaction (see
 * {@code TutorialGuideInteractListener}). Deliberately plain, static text, never LLM-generated --
 * a tutorial's instructions must be guaranteed, not improvised (see Audyble's own
 * {@code NpcConversationEngine}, which has no scripted/static bypass at all, hence this mod not
 * depending on it or on Ollama/Whisper).
 *
 * <p>Each stage's {@code dx}/{@code dz} are a world-space offset *relative to the guide's own spawn
 * position* (not absolute coordinates -- the tutorial Wyrld is generated fresh per player at an
 * arbitrary world location). These are real, deliberately-chosen offsets, not arbitrary: derived
 * directly from the captured {@code WelcomeTutorial1} Entrance's own marker positions (read from the
 * real WyrldPrint JSON) -- the friendly-spawn marker sits at (forward=11, lateral=7), the
 * loot-common marker at (forward=13, lateral=4), both at y=0 -- and {@code
 * WyrldPrintArea#FRONT_DIRECTION} is {@code EAST} with the Entrance piece always placed at {@code
 * Rotation.NONE} (confirmed directly in {@code generate.OneOffGenerator}'s own source), so forward
 * maps 1:1 onto world {@code +X} and lateral onto world {@code +Z} with no rotation to account for.
 * Stage 5's (dx=2, dz=-3) therefore lands the guide exactly on the real loot-common marker's
 * position relative to its own spawn.
 *
 * <p>The final stage has no literal "walk to the exit portal" waypoint -- the portal is a plain
 * player-placed block (per TinyWyrlds' own design, no auto-placed frame), not a captured marker, so
 * its position isn't known at all until the user places it in-world. The guide just moves generally
 * toward the open center of the room and delivers its closing line in place.
 */
public final class TutorialScript {

    private TutorialScript() {
    }

    public record Stage(int dx, int dz, List<String> lines) {
    }

    public static final List<Stage> STAGES = List.of(
            new Stage(0, 0, List.of(
                    "Welcome to Kyngdoms! I'm here to help you get started.")),
            new Stage(0, 0, List.of(
                    "First things first: open your Voice Chat settings with the default key, V, "
                            + "and make sure your microphone's enabled.")),
            new Stage(1, -1, List.of(
                    "This world is built from several interlocking systems.",
                    "Settlemynts handles towns and plots. Lyfe tracks your skills, and holds your "
                            + "Recallcinite Totem -- that's your real respawn point.")),
            new Stage(1, -2, List.of(
                    "Yconomics runs the economy. Blueprynts lets you build real structures from "
                            + "blueprints. Myssions tracks quests.",
                    "And Audyble means the NPCs you meet can actually hear you, and talk back.")),
            new Stage(2, -3, List.of(
                    "Grab what's in that chest there -- it'll help you get started.")),
            new Stage(4, 0, List.of(
                    "When you're ready, step through the portal ahead. You'll choose your first "
                            + "town there.")));
}
