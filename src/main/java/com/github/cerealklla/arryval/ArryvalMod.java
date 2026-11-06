package com.github.cerealklla.arryval;

import com.github.cerealklla.arryval.config.ArryvalConfig;
import com.github.cerealklla.arryval.onboarding.FirstLoginListener;
import com.github.cerealklla.arryval.registration.ModAttachments;
import com.github.cerealklla.arryval.registration.ModEntities;
import com.github.cerealklla.arryval.starttown.OnboardingExitHandler;
import com.github.cerealklla.arryval.starttown.OpenStartingSettlementPickerPayload;
import com.github.cerealklla.arryval.starttown.PendingOnboarding;
import com.github.cerealklla.arryval.starttown.SetStartingSettlementPayload;
import com.github.cerealklla.arryval.starttown.StartingSettlementResolver;
import com.github.cerealklla.arryval.tts.TutorialVoicePayload;
import com.github.cerealklla.arryval.tutorial.TutorialGuideEntity;
import com.github.cerealklla.arryval.tutorial.TutorialGuideInteractListener;
import com.github.cerealklla.lyfe.api.Lyfe;
import com.github.cerealklla.settlemynts.api.Settlemynts;
import com.github.cerealklla.tinywyrlds.api.TinyWyrlds;

import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(ArryvalMod.MODID)
public class ArryvalMod {

    public static final String MODID = "arryval";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ArryvalMod(IEventBus modEventBus, ModContainer modContainer) {
        ModAttachments.ATTACHMENT_TYPES.register(modEventBus);
        ModEntities.ENTITIES.register(modEventBus);

        modContainer.registerConfig(ModConfig.Type.SERVER, ArryvalConfig.SPEC);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerAttributes);
        modEventBus.addListener(this::registerPayloads);

        NeoForge.EVENT_BUS.register(new FirstLoginListener());
        NeoForge.EVENT_BUS.register(new TutorialGuideInteractListener());
        TinyWyrlds.registerExitHandler(new OnboardingExitHandler());
    }

    private void commonSetup(net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) {
        LOGGER.info("Arryval common setup");
    }

    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.TUTORIAL_GUIDE.get(), TutorialGuideEntity.createAttributes().build());
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(TutorialVoicePayload.TYPE, TutorialVoicePayload.STREAM_CODEC,
                (payload, context) -> com.github.cerealklla.arryval.tts.client.ClientTutorialVoicePlayer.play(payload));
        registrar.playToClient(OpenStartingSettlementPickerPayload.TYPE, OpenStartingSettlementPickerPayload.STREAM_CODEC,
                (payload, context) -> com.github.cerealklla.arryval.starttown.client.ClientStartTownRequests.requestPicker(payload));
        registrar.playToServer(SetStartingSettlementPayload.TYPE, SetStartingSettlementPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        confirmStartingSettlement(player, payload.index());
                    }
                });
    }

    /** Re-validates {@code index} against this player's own cached candidate list (never trusts the client's index blindly), assigns the plot, binds the Totem, and teleports them there for real. */
    private static void confirmStartingSettlement(ServerPlayer player, int index) {
        var candidates = PendingOnboarding.getCachedCandidates(player.getUUID());
        if (index < 0 || index >= candidates.size()) {
            return;
        }
        StartingSettlementResolver.Candidate chosen = candidates.get(index);
        ServerLevel overworld = ((ServerLevel) player.level()).getServer().overworld();

        if (!Settlemynts.assignPlotOwner(overworld, chosen.settlementCoreId(), chosen.plotId(), player.getUUID())) {
            player.sendSystemMessage(Component.literal("That town's plot is no longer available -- please step through the portal again to choose another."));
            return;
        }
        GlobalPos plotPos = GlobalPos.of(overworld.dimension(), chosen.anchorPos());
        Lyfe.bindRecallciniteTotem(player, plotPos);
        PendingOnboarding.clear(player.getUUID());

        player.teleportTo(overworld, chosen.anchorPos().getX() + 0.5, chosen.anchorPos().getY(), chosen.anchorPos().getZ() + 0.5,
                java.util.Set.of(), player.getYRot(), player.getXRot(), true);
        player.sendSystemMessage(Component.literal("Welcome to " + chosen.name() + "! Your Recallcinite Totem is bound here."));
    }
}
