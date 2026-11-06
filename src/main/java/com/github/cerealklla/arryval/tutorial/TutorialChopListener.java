package com.github.cerealklla.arryval.tutorial;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Advances a nearby {@link TutorialGuideEntity} waiting on {@code AWAITING_CHOP_TREE} -- see that
 * class's own doc for the full staged-intro design.
 *
 * <p><b>Deliberately a right-click interaction, not a real block break</b> -- real bug caught before
 * ever live-testing: TinyWyrlds enforces "no solid block create/destroy" inside every Wyrld ({@code
 * voxel.VoxelLockListener}, scoped to the whole {@code wyrldprint.space.WyrldSpaceDimension}, no
 * exception for this tutorial specifically), so a genuine log break would simply never happen there
 * at all -- the event fires, but the block never actually breaks. Right-clicking a log with an axe
 * in hand needs no voxel change whatsoever to serve as the same "chop the tree" moment, so this
 * cancels the event outright (consuming the interaction) before vanilla's own axe-on-log stripping
 * behavior gets a chance to run -- that's a block STATE change, not a break, but still a voxel
 * mutation this avoids touching at all, on the same side of caution.
 */
public final class TutorialChopListener {

    private static final double SEARCH_RADIUS_BLOCKS = 32.0;

    @SubscribeEvent
    public void onRightClickLog(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!level.getBlockState(event.getPos()).is(BlockTags.LOGS) || !event.getItemStack().is(ItemTags.AXES)) {
            return;
        }
        BlockPos pos = event.getPos();
        AABB searchBox = new AABB(pos).inflate(SEARCH_RADIUS_BLOCKS);
        boolean claimed = false;
        for (TutorialGuideEntity guide : level.getEntitiesOfClass(TutorialGuideEntity.class, searchBox)) {
            claimed |= guide.onPlayerChoppedLog(player);
        }
        if (claimed) {
            event.setCanceled(true);
        }
    }
}
