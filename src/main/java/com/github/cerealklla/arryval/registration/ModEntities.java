package com.github.cerealklla.arryval.registration;

import com.github.cerealklla.arryval.ArryvalMod;
import com.github.cerealklla.arryval.tutorial.TutorialGuideEntity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {

    private ModEntities() {
    }

    public static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(ArryvalMod.MODID);

    /** Never naturally spawned -- only ever born via the WelcomeTutorial recipe's "friendly pool." Same sizing/tracking as Myssions' own QuestNpcEntity. */
    public static final DeferredHolder<EntityType<?>, EntityType<TutorialGuideEntity>> TUTORIAL_GUIDE = ENTITIES.registerEntityType(
            "tutorial_guide",
            TutorialGuideEntity::new,
            MobCategory.MISC,
            builder -> builder.sized(0.6f, 1.95f).clientTrackingRange(10));
}
