package com.github.cerealklla.arryval.starttown;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.github.cerealklla.cartographyr.api.Cartography;
import com.github.cerealklla.cartographyr.geo.Classification;
import com.github.cerealklla.cartographyr.geo.EntityId;
import com.github.cerealklla.cartographyr.geo.Geometry;
import com.github.cerealklla.cartographyr.geo.GeographicEntity;
import com.github.cerealklla.cartographyr.geo.Layer;
import com.github.cerealklla.cartographyr.geo.LifecycleState;
import com.github.cerealklla.nuclyus.server.PersistedServerStore;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Persisted, generate-once set of up to {@link #DESIRED_CANDIDATE_COUNT} starting-settlement
 * locations nearest world origin -- explicit user correction, 2026-11-06, after watching the earlier
 * design re-run its own expensive discovery (up to 9 world-seed structure searches) on every single
 * player's approach to the exit portal: "why isn't it only once period, and cache it for all other
 * later players? ... just create a text file with the 8 starter settlement options and populate it
 * if it's blank." Mirrors this suite's own established "generate once on first boot, persist to a
 * JSON file, reuse thereafter" pattern ({@code nuclyus.server.PersistedServerStore}, already used by
 * Lyfe's recipe/Runeword stores, Settlemynts'/Audyble's NPC identity stores) instead of a hand-rolled
 * in-memory cache -- also survives a server restart, which an in-memory cache wouldn't have.
 *
 * <p>Settlement *locations* genuinely never change once discovered, so caching them is safe; whether
 * each location actually has a real bind location yet (a settlement/plot that's finished forming) is
 * still re-checked live, every call, by {@link StartingSettlementResolver} itself -- only the
 * expensive discovery half is persisted here.
 *
 * <p><b>Each location's chunk is also force-loaded exactly once, right here at discovery time</b> --
 * explicit user follow-up, 2026-11-06: "Why are you not force loading all 8 settlements from the
 * moment the first player logs into the server?" This does double duty: it's the one real world-gen
 * cost this whole feature ever pays (generated terrain is saved to disk, so any later {@code
 * getChunk} call for the same position is a cheap disk read, not real world-gen, regardless of
 * whether the chunk is still resident in memory by then), and the force-load's own real {@code
 * ChunkEvent.Load} genuinely kicks off settlement/plot formation immediately (Cartographyr promoting
 * the planned entity to REALIZED, Settlemynts enqueuing real plot generation) for all {@link
 * #DESIRED_CANDIDATE_COUNT} locations at once, well before any player could possibly reach the exit
 * portal -- "there's no way I'm getting through the entire tutorial so fast that you aren't able to
 * render them all at least once."
 *
 * <p>{@link #loadOrGenerate} must run once at server start (see {@code StartingSettlementBootstrap})
 * before anything calls {@link #all()} -- same requirement every other {@code PersistedServerStore}
 * consumer in this suite already has.
 */
public final class StartingSettlementStore {

    public static final int DESIRED_CANDIDATE_COUNT = 8;

    /** How far out from origin each directional search center sits, and how wide its own search radius is -- both flagged-tunable, same "placeholder, revisit by feel" convention every other distance constant in this suite uses. Spread wide enough that 8 compass directions are unlikely to all resolve to the same real village. */
    private static final double SEARCH_CENTER_DISTANCE_BLOCKS = 2500.0;
    private static final double SEARCH_RADIUS_BLOCKS = 2500.0;

    /** Set just before {@link PersistedServerStore#loadOrGenerate} so the anonymous store's own {@code generate()} (which takes no parameters) can still reach the real level -- cleared immediately after, only ever non-null mid-call. */
    private static ServerLevel pendingOverworld;

    // worldScoped=true -- real live bug, found 2026-11-09: this store's own content (settlements
    // nearest THIS world's origin, for THIS world's seed) was being cached at the game-instance
    // level (PersistedServerStore's own original default), so every later singleplayer world
    // created in the same install kept reusing the first world's discovered coordinates -- a
    // completely different, wrong world's settlements, for a world with a different seed entirely.
    // See PersistedServerStore's own doc for the full account.
    private static final PersistedServerStore<Integer, StartingSettlementEntry> STORE =
            new PersistedServerStore<>("arryval_starting_settlements.json", StartingSettlementEntry.CODEC, StartingSettlementEntry::index, true) {
                @Override
                protected Map<Integer, StartingSettlementEntry> generate() {
                    return discover(pendingOverworld, DESIRED_CANDIDATE_COUNT);
                }
            };

    private StartingSettlementStore() {
    }

    public static void loadOrGenerate(MinecraftServer server) {
        pendingOverworld = server.overworld();
        STORE.loadOrGenerate(server);
        pendingOverworld = null;
    }

    public static List<StartingSettlementEntry> all() {
        return List.copyOf(STORE.all().values());
    }

    private static Map<Integer, StartingSettlementEntry> discover(ServerLevel level, int desiredCount) {
        List<GeographicEntity> known = new ArrayList<>(Cartography.findEntities(level, Classification.CONSTRUCTED, Layer.SETTLEMENT_ID));

        // Seed-predict more candidates if the registry alone doesn't already have enough -- origin
        // itself plus an 8-direction compass ring, each a fresh Cartography.predictSettlementAt call
        // so a genuinely different real village is found from each point rather than the same
        // nearest one repeatedly.
        if (known.size() < desiredCount) {
            List<BlockPos> searchCenters = new ArrayList<>();
            searchCenters.add(BlockPos.ZERO);
            for (int i = 0; i < 8; i++) {
                double angle = i * (Math.PI / 4.0);
                searchCenters.add(new BlockPos(
                        (int) Math.round(Math.cos(angle) * SEARCH_CENTER_DISTANCE_BLOCKS), level.getSeaLevel(),
                        (int) Math.round(Math.sin(angle) * SEARCH_CENTER_DISTANCE_BLOCKS)));
            }
            for (BlockPos center : searchCenters) {
                Cartography.predictSettlementAt(level, center, SEARCH_RADIUS_BLOCKS).ifPresent(entity -> {
                    if (known.stream().noneMatch(existing -> existing.id().equals(entity.id()))) {
                        known.add(entity);
                    }
                });
            }
        }

        known.sort(Comparator.comparingDouble(StartingSettlementStore::distanceSqFromOrigin));

        Map<Integer, StartingSettlementEntry> result = new LinkedHashMap<>();
        int index = 0;
        for (GeographicEntity entity : known) {
            if (index >= desiredCount) {
                break;
            }
            int predictedX = entity.geometry().centerBlockX();
            int predictedZ = entity.geometry().centerBlockZ();
            Optional<GeographicEntity> realized = materializeRealSettlement(level, entity.id(), predictedX, predictedZ);
            if (realized.isEmpty()) {
                // Real bug, found live 2026-11-07: "I picked a town called Cadwin but when I arrived
                // there was no town at all" / "dropped in the middle of the ocean... a completely
                // different town [was visible]". A seed-predicted candidate (see
                // settlement.SettlementPrediction) is only ever a GUESS at where vanilla's own
                // structure placement grid WOULD put a village -- it's not confirmed real until the
                // actual chunk generates and SettlementListener promotes it to REALIZED. Forcing just
                // the single chunk at the predicted center was often not enough: a village's
                // structure-start piece can land on an edge/corner of its own footprint (water,
                // empty terrain), and the rest of the real structure genuinely never got force-loaded
                // at all, let alone the case where the grid cell was a false positive with nothing
                // there to begin with. Skip a candidate that never actually materializes into a real
                // settlement instead of blindly trusting the prediction -- see
                // materializeRealSettlement's own doc for the widening-radius retry.
                continue;
            }
            // Second real bug, found live 2026-11-07 (same report, after the above fix alone wasn't
            // enough): this used to store the PREDICTED center (predictedX/predictedZ) even once
            // REALIZED was confirmed. SettlementDiscovery#withGeometry replaces a promoted entity's
            // geometry with the real structure's own footprint, which is frequently off-center from
            // the seed-predicted grid cell -- so the cached entry pointed at the predicted point, not
            // the real village, even on a successful materialization. Re-reading the now-REALIZED
            // entity's own geometry fixes this for good.
            //
            // Third real bug, found live 2026-11-09: even the REALIZED entity's own coarse
            // Geometry#centerBlockX/Z (a bounding-box midpoint, not a true "inside the shape"
            // guarantee -- see that method's own doc) can land in real, genuinely empty space for an
            // irregular/multi-cluster village -- a real report: "dropped in a pond in the middle of
            // the woods, no settlement anywhere nearby," confirmed not a loading delay.
            // Geometry#nearestContainedPoint spirals out from that same coarse center until it finds
            // a point the shape's own contains() check actually accepts.
            Geometry.Point contained = realized.get().geometry().nearestContainedPoint(MAX_CONTAINED_POINT_SEARCH_RADIUS_BLOCKS);
            int x = contained.x();
            int z = contained.z();
            result.put(index, new StartingSettlementEntry(index, entity.name().orElse(""), x, z));
            index++;
        }
        return result;
    }

    /** How many chunks out from center to force-load on the first attempt -- cheap, covers most real village footprints in one pass. */
    private static final int INITIAL_LOAD_RADIUS_CHUNKS = 1;
    /** Widened retry radius if the first pass didn't materialize a real structure -- covers a sprawling village whose start piece sits well off from its own visual center. */
    private static final int WIDENED_LOAD_RADIUS_CHUNKS = 4;

    /** How far {@link Geometry#nearestContainedPoint} is allowed to spiral out from a settlement's own coarse bounding-box center while looking for a point actually inside its footprint -- generous enough for any real settlement's own widest cluster gap. */
    private static final int MAX_CONTAINED_POINT_SEARCH_RADIUS_BLOCKS = 300;

    /**
     * Force-loads a growing neighborhood of chunks around {@code (x, z)} until the entity at {@code
     * id} is confirmed {@link LifecycleState#REALIZED} (a real structure actually generated there) or
     * the widened attempt still comes up empty -- see this class's own doc, and the real live bugs
     * this fixes. An already-{@code REALIZED} entity (a genuinely known, already-discovered
     * settlement from {@code Cartography.findEntities}, not a seed-predicted placeholder) returns
     * true immediately without loading anything extra.
     */
    private static Optional<GeographicEntity> materializeRealSettlement(ServerLevel level, EntityId id, int x, int z) {
        Optional<GeographicEntity> current = Cartography.getEntity(level, id)
                .filter(e -> e.lifecycleState() == LifecycleState.REALIZED);
        if (current.isPresent()) {
            return current;
        }
        Optional<GeographicEntity> afterInitial = forceLoadAndCheck(level, id, x, z, INITIAL_LOAD_RADIUS_CHUNKS);
        if (afterInitial.isPresent()) {
            return afterInitial;
        }
        return forceLoadAndCheck(level, id, x, z, WIDENED_LOAD_RADIUS_CHUNKS);
    }

    private static Optional<GeographicEntity> forceLoadAndCheck(ServerLevel level, EntityId id, int x, int z, int radiusChunks) {
        int centerChunkX = x >> 4;
        int centerChunkZ = z >> 4;
        for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
            for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
                level.getChunk(centerChunkX + dx, centerChunkZ + dz);
            }
        }
        return Cartography.getEntity(level, id).filter(e -> e.lifecycleState() == LifecycleState.REALIZED);
    }

    private static double distanceSqFromOrigin(GeographicEntity entity) {
        double x = entity.geometry().centerBlockX();
        double z = entity.geometry().centerBlockZ();
        return x * x + z * z;
    }
}
