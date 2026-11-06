package com.github.cerealklla.arryval;

import com.github.cerealklla.arryval.onboarding.FirstLoginListener;
import com.github.cerealklla.arryval.registration.ModAttachments;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(ArryvalMod.MODID)
public class ArryvalMod {

    public static final String MODID = "arryval";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ArryvalMod(IEventBus modEventBus, ModContainer modContainer) {
        ModAttachments.ATTACHMENT_TYPES.register(modEventBus);

        modEventBus.addListener(this::commonSetup);

        NeoForge.EVENT_BUS.register(new FirstLoginListener());
    }

    private void commonSetup(net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) {
        LOGGER.info("Arryval common setup");
    }
}
