package com.github.cerealklla.arryval;

import com.github.cerealklla.arryval.config.ArryvalConfig;
import com.github.cerealklla.arryval.onboarding.FirstLoginListener;
import com.github.cerealklla.arryval.registration.ModAttachments;
import com.github.cerealklla.arryval.registration.ModEntities;
import com.github.cerealklla.arryval.tts.TutorialVoicePayload;
import com.github.cerealklla.arryval.tutorial.TutorialGuideEntity;
import com.github.cerealklla.arryval.tutorial.TutorialGuideInteractListener;

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
    }
}
