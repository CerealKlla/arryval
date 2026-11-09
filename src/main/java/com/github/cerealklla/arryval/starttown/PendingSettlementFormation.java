package com.github.cerealklla.arryval.starttown;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.github.cerealklla.lyfe.api.Lyfe;
import com.github.cerealklla.myssions.api.Myssions;
import com.github.cerealklla.settlemynts.api.Settlemynts;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Finishes binding a picked-but-not-yet-founded settlement candidate's Totem once the real
 * settlement actually forms -- explicit follow-up to {@link StartingSettlementResolver}'s own "a
 * never-visited village the world seed guarantees will generate is a real, pickable candidate too"
 * correction, 2026-11-06. Teleporting the player to {@link StartingSettlementResolver.Candidate
 * #predictedPos} is what actually kicks off the real, organic chain -- their own render-distance
 * chunk loading triggers Cartographyr's {@code settlement.SettlementListener} promoting the planned
 * entity to REALIZED, then Settlemynts' {@code zone.NaturalVillagePlotListener} generating real
 * plots a short deferred delay after (needs the village's *other* pieces' chunks to have loaded too,
 * see that listener's own doc) -- nothing here force-generates anything itself. Never claims a plot
 * or any ownership (see {@code api.Settlemynts#findStartingBindLocation}'s own doc) -- this is only
 * ever waiting for a real location to exist, not a vacancy.
 *
 * <p>A short, bounded poll (never a standing ticker, and self-removing on success/timeout/logout) --
 * purely because no event exists anywhere for "a natural village's plots just finished generating"
 * to react to instead, matching this suite's own standing "avoid tick-polling... unless no real
 * event exists" rule. Re-resolves fresh every check rather than trusting anything cached, since the
 * whole point is waiting for state that's actively changing underneath it.
 */
public final class PendingSettlementFormation {

    private record Waiting(String name, BlockPos geoPos, long startedAtGameTime, long lastCheckedGameTime) {
    }

    /** Generous radius around the predicted/known position to search for a real bind location -- same value {@link StartingSettlementResolver} itself uses for the identical purpose. */
    private static final double BIND_SEARCH_RADIUS_BLOCKS = 128.0;
    private static final long POLL_INTERVAL_TICKS = 100L; // 5 real seconds.
    private static final long TIMEOUT_TICKS = 20L * 60 * 5; // 5 real minutes.

    private static final Map<UUID, Waiting> WAITING = new ConcurrentHashMap<>();

    public PendingSettlementFormation() {
    }

    /** Called once, right after teleporting a player to a not-yet-founded candidate's predicted position. */
    public static void beginWaiting(ServerPlayer player, String name, BlockPos geoPos) {
        long now = player.level().getGameTime();
        WAITING.put(player.getUUID(), new Waiting(name, geoPos, now, now));
        player.sendSystemMessage(Component.literal(
                "This town hasn't been founded yet -- stick around, it'll form shortly and you'll be settled in automatically."));
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (WAITING.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        ServerLevel overworld = server.overworld();
        long now = overworld.getGameTime();
        for (UUID playerId : List.copyOf(WAITING.keySet())) {
            Waiting waiting = WAITING.get(playerId);
            if (waiting == null) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                WAITING.remove(playerId); // Logged out -- give up; they'll get a fresh resolve next time they reach the portal.
                continue;
            }
            if (now - waiting.startedAtGameTime() >= TIMEOUT_TICKS) {
                WAITING.remove(playerId);
                player.sendSystemMessage(Component.literal(
                        "That town is taking longer than expected to form -- please step through the portal again to choose another."));
                continue;
            }
            if (now - waiting.lastCheckedGameTime() < POLL_INTERVAL_TICKS) {
                continue;
            }
            WAITING.put(playerId, new Waiting(waiting.name(), waiting.geoPos(), waiting.startedAtGameTime(), now));

            BlockPos bindPos = Settlemynts.findStartingBindLocation(overworld, waiting.geoPos(), BIND_SEARCH_RADIUS_BLOCKS).orElse(null);
            if (bindPos == null) {
                continue; // Still not founded, or founded with no plot yet -- keep waiting.
            }

            WAITING.remove(playerId);
            finishBinding(player, overworld, waiting.name(), bindPos);
        }
    }

    /**
     * Never claims a plot/owner -- just binds the Totem to a location, see this class's own doc.
     * Teleports to a real resolved surface Y ({@code Heightmap.Types.MOTION_BLOCKING_NO_LEAVES}), not
     * {@code bindPos}'s own raw Y -- real bug found live, 2026-11-06: a plot's exact box position can
     * land the player inside solid terrain (see {@code ArryvalMod#confirmStartingSettlement}'s own
     * doc for the full account, including why the Totem itself still binds to the raw position).
     */
    private static void finishBinding(ServerPlayer player, ServerLevel overworld, String name, BlockPos bindPos) {
        GlobalPos globalPos = GlobalPos.of(overworld.dimension(), bindPos);
        Lyfe.bindRecallciniteTotem(player, globalPos);
        StartingSettlementCoreState.recordFor(player, overworld, bindPos);
        int safeY = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bindPos.getX(), bindPos.getZ());
        player.teleportTo(overworld, bindPos.getX() + 0.5, safeY, bindPos.getZ() + 0.5,
                java.util.Set.of(), player.getYRot(), player.getXRot(), true);
        player.sendSystemMessage(Component.literal("Welcome to " + name + "! Your Recallcinite Totem is bound here."));
    }
}
