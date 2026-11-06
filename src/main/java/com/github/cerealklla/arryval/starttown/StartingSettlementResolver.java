package com.github.cerealklla.arryval.starttown;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.github.cerealklla.cartographyr.api.Cartography;
import com.github.cerealklla.cartographyr.geo.Classification;
import com.github.cerealklla.cartographyr.geo.GeographicEntity;
import com.github.cerealklla.cartographyr.geo.Layer;
import com.github.cerealklla.settlemynts.api.Settlemynts;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Resolves up to {@code desiredCount} real, startable settlements nearest world origin -- the
 * starting-town picker's candidate list. Deliberately resolves fresh every time it's called (once
 * per new player reaching the tutorial's exit portal, not a recurring cost) rather than caching,
 * since which settlements exist/have a vacant plot genuinely changes over the life of the server.
 *
 * <p>Queries Cartographyr's persistent registry first (chunk-independent -- a player-founded
 * settlement is registered the instant its claim flag is placed, and {@code
 * SettlementPrediction}-predicted natural villages are registered from pure seed math, neither
 * needing a loaded chunk), then, nearest-first, force-loads each candidate's own chunk and tries to
 * resolve a real, currently-reachable Settlemynts settlement with at least one vacant plot --
 * skipping (not failing) any candidate that's still only a placeholder with no real core entity, or
 * has no vacant plot at all. This is the deliberate, graceful handling for "a player may never have
 * actually visited one of the 8 nearest settlements to generate it yet" -- explicitly accepted as a
 * real possibility rather than something to force-generate around.
 */
public final class StartingSettlementResolver {

    private StartingSettlementResolver() {
    }

    public record Candidate(UUID settlementCoreId, String name, UUID plotId, BlockPos anchorPos, double distanceBlocks) {
    }

    /** Generous radius around each Cartographyr-known position to search for the real Settlemynts core -- a settlement's own geo-registered center and its real GhostTownHallCoreEntity are never far apart. */
    private static final double CORE_SEARCH_RADIUS_BLOCKS = 128.0;

    public static List<Candidate> findStartingCandidates(ServerLevel level, int desiredCount) {
        List<GeographicEntity> known = new ArrayList<>(Cartography.findEntities(level, Classification.CONSTRUCTED, Layer.SETTLEMENT_ID));
        known.sort(Comparator.comparingDouble(StartingSettlementResolver::distanceSqFromOrigin));

        List<Candidate> result = new ArrayList<>();
        for (GeographicEntity entity : known) {
            if (result.size() >= desiredCount) {
                break;
            }
            int x = entity.geometry().centerBlockX();
            int z = entity.geometry().centerBlockZ();
            BlockPos geoPos = new BlockPos(x, level.getSeaLevel(), z);
            level.getChunk(x >> 4, z >> 4); // synchronous force-load -- see this class's own doc.

            UUID settlementCoreId = Settlemynts.findNearbySettlements(level, geoPos, CORE_SEARCH_RADIUS_BLOCKS).stream()
                    .min(Comparator.comparingDouble(Settlemynts.SettlementHandle::distanceBlocks))
                    .map(Settlemynts.SettlementHandle::settlementCoreId)
                    .orElse(null);
            if (settlementCoreId == null) {
                continue; // Placeholder-only (never generated/founded for real) -- skip.
            }
            String name = Settlemynts.getSettlementName(level, settlementCoreId).orElse(entity.name().orElse(""));
            Settlemynts.VacantPlotHandle plot = Settlemynts.findVacantStartingPlot(level, settlementCoreId).orElse(null);
            if (plot == null) {
                continue; // No vacant plot -- skip, same graceful handling.
            }
            double distance = Math.sqrt(distanceSqFromOrigin(entity));
            result.add(new Candidate(settlementCoreId, name, plot.plotId(), plot.anchorPos(), distance));
        }
        return result;
    }

    private static double distanceSqFromOrigin(GeographicEntity entity) {
        double x = entity.geometry().centerBlockX();
        double z = entity.geometry().centerBlockZ();
        return x * x + z * z;
    }
}
