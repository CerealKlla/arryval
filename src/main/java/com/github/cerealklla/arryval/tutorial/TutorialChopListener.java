package com.github.cerealklla.arryval.tutorial;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

/** Advances a nearby {@link TutorialGuideEntity} waiting on {@code AWAITING_CHOP_TREE} the instant the tracked player breaks a real log -- see that class's own doc for the full staged-intro design. */
public final class TutorialChopListener {

    private static final double SEARCH_RADIUS_BLOCKS = 32.0;

    @SubscribeEvent
    public void onBreak(BreakBlockEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        if (!event.getState().is(BlockTags.LOGS)) {
            return;
        }
        BlockPos pos = event.getPos();
        AABB searchBox = new AABB(pos).inflate(SEARCH_RADIUS_BLOCKS);
        for (TutorialGuideEntity guide : level.getEntitiesOfClass(TutorialGuideEntity.class, searchBox)) {
            guide.onPlayerChoppedLog(player);
        }
    }
}
