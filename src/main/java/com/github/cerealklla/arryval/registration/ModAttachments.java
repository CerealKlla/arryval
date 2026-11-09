package com.github.cerealklla.arryval.registration;

import java.util.function.Supplier;

import com.github.cerealklla.arryval.ArryvalMod;
import com.github.cerealklla.arryval.onboarding.SettlementChosenState;
import com.github.cerealklla.arryval.onboarding.TutorialGrantState;
import com.github.cerealklla.arryval.starttown.FoundingLetterState;
import com.github.cerealklla.arryval.starttown.StartingSettlementCoreState;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModAttachments {

    private ModAttachments() {
    }

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, ArryvalMod.MODID);

    // Deliberately separate from Kyt's own WELCOME_GRANT_STATE -- a book grant and a tutorial
    // teleport are two different things and shouldn't be coupled; if one mod's flag is somehow
    // wrong for a given player, the other still fires independently. copyOnDeath() is required,
    // not optional: NeoForge attachments do NOT survive a player's death by default (a brand-new
    // Player entity is created on respawn), so skipping this would re-trigger the tutorial on every
    // death instead of just true first login -- same gotcha Kyt's own WELCOME_GRANT_STATE documents.
    public static final Supplier<AttachmentType<TutorialGrantState>> TUTORIAL_GRANT_STATE = ATTACHMENT_TYPES.register(
            "tutorial_grant_state",
            () -> AttachmentType.builder(holder -> TutorialGrantState.DEFAULT)
                    .serialize(TutorialGrantState.CODEC)
                    .copyOnDeath()
                    .build()
    );

    /** See {@link SettlementChosenState}'s own doc. */
    public static final Supplier<AttachmentType<SettlementChosenState>> SETTLEMENT_CHOSEN_STATE = ATTACHMENT_TYPES.register(
            "settlement_chosen_state",
            () -> AttachmentType.builder(holder -> SettlementChosenState.DEFAULT)
                    .serialize(SettlementChosenState.CODEC)
                    .copyOnDeath()
                    .build()
    );

    /** See {@link StartingSettlementCoreState}'s own doc. */
    public static final Supplier<AttachmentType<StartingSettlementCoreState>> STARTING_SETTLEMENT_CORE_STATE = ATTACHMENT_TYPES.register(
            "starting_settlement_core_state",
            () -> AttachmentType.builder(holder -> StartingSettlementCoreState.DEFAULT)
                    .serialize(StartingSettlementCoreState.CODEC)
                    .copyOnDeath()
                    .build()
    );

    /** See {@link FoundingLetterState}'s own doc. */
    public static final Supplier<AttachmentType<FoundingLetterState>> FOUNDING_LETTER_STATE = ATTACHMENT_TYPES.register(
            "founding_letter_state",
            () -> AttachmentType.builder(holder -> FoundingLetterState.DEFAULT)
                    .serialize(FoundingLetterState.CODEC)
                    .copyOnDeath()
                    .build()
    );
}
