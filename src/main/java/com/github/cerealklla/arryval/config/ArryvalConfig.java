package com.github.cerealklla.arryval.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ArryvalConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue SKIP_ONBOARDING = BUILDER
            .comment(
                    "Dev convenience: when true, a brand-new player's first login skips the entire",
                    "onboarding flow (the private WelcomeTutorial Wyrld, the guide NPC conversation, and",
                    "the starting-settlement picker) and is placed at the world's own spawn point",
                    "instead. Intended for repeated dev/integration test sessions, not Production.")
            .define("skipOnboarding", false);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private ArryvalConfig() {
    }
}
