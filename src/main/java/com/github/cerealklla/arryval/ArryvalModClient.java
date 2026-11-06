package com.github.cerealklla.arryval;

import com.github.cerealklla.arryval.registration.ModEntities;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@Mod(value = ArryvalMod.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = ArryvalMod.MODID, value = Dist.CLIENT)
public class ArryvalModClient {

    public ArryvalModClient(ModContainer container) {
    }

    @SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // Reuses vanilla's own VillagerRenderer directly -- a real Villager subclass needs no
        // bespoke model, same precedent as Myssions'/Settlemynts' own Villager-based NPCs.
        event.registerEntityRenderer(ModEntities.TUTORIAL_GUIDE.get(),
                net.minecraft.client.renderer.entity.VillagerRenderer::new);
    }
}
