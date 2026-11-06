package com.github.cerealklla.arryval.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-administrator-facing tuning knobs. */
public final class ArryvalConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<String> TUTORIAL_GUIDE_VOICE = BUILDER
            .comment("Kokoro voice name (see Nuclyus's own kokoroBaseUrl config) the tutorial guide "
                    + "speaks its lines with, e.g. am_michael, af_heart -- see the Kokoro project's "
                    + "own voice list. The guide has a single fixed voice, not a per-NPC gender pick "
                    + "like Settlemynts' born NPCs -- it's always the same one character.")
            .define("tutorialGuideVoice", "am_michael");

    public static final ModConfigSpec SPEC = BUILDER.build();

    private ArryvalConfig() {
    }
}
