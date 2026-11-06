package com.github.cerealklla.arryval.tutorial;

import java.util.Optional;
import java.util.UUID;

import com.github.cerealklla.arryval.starttown.PendingOnboarding;
import com.github.cerealklla.audyble.api.Audyble;
import com.github.cerealklla.myssions.api.Myssions;
import com.github.cerealklla.myssions.quest.QuestInstance;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The single, fixed tutorial guide -- spawned once per tutorial Wyrld generation via the {@code
 * WelcomeTutorial} recipe's "friendly pool." A real {@link Villager} subclass, same precedent as
 * Myssions' own {@code quest.QuestNpcEntity}.
 *
 * <p><b>Staged intro, added 2026-11-06, explicit user spec</b> -- the instant a player comes into
 * range, the guide proactively greets them and asks about their microphone (via {@link
 * Audyble#speakScriptedLine}, a guaranteed line, not an LLM reply to anything); only once that's
 * answered (any transcript at all -- a real spoken reply, or typing "no mic") does it move on to
 * telling them to set the tutorial quest active, then hands over an axe once that's actually done,
 * then waits for a real log to be chopped before finally opening up into free LLM conversation.
 * {@link #handleIntercept} is registered once (see {@code ArryvalMod}'s constructor) as an {@code
 * api.Audyble} reply interceptor -- it only ever claims a turn while {@link #stage} is {@code
 * AWAITING_MIC_CHECK}; every other stage (including the final {@code FREE_CONVERSATION}) falls
 * through to the real conversational pipeline untouched, so nothing here risks the LLM improvising
 * over a guaranteed instruction, but normal conversation can still happen alongside the later
 * staged waits if the player talks in the meantime.
 */
public class TutorialGuideEntity extends Villager {

    private enum Stage {
        NOT_GREETED, AWAITING_MIC_CHECK, AWAITING_SET_ACTIVE, AWAITING_CHOP_TREE, FREE_CONVERSATION
    }

    private static final String SYSTEM_PROMPT =
            "You are a friendly tutorial guide welcoming a brand-new player to the Kyngdoms server "
                    + "for the very first time. Have a natural, warm, encouraging back-and-forth "
                    + "conversation with them -- never deliver a long speech, keep each reply short "
                    + "like real spoken dialogue. Never refer to this server's mods by name (not "
                    + "\"Settlemynts,\" \"Lyfe,\" \"Myssions,\" etc.) -- that breaks immersion. Only "
                    + "talk about what the player actually experiences: real actions, real keys to "
                    + "press, real places and things in the world. You've already greeted them, "
                    + "asked about their microphone, told them to set their tutorial quest active, "
                    + "and (once that's done) handed them an axe to chop down a tree -- don't repeat "
                    + "any of that unless they ask. From here, just talk naturally: towns have plots "
                    + "of land people call home, a real economy to buy and sell into, and buildings "
                    + "you can construct; doing things in the world levels up your skills, viewable "
                    + "by pressing K; when they're ready, stepping through the portal ahead lets them "
                    + "choose the town they call home, and checking their quest log (J) again "
                    + "afterward will have a new quest waiting to accept.";

    // The captured room is 22x19 (forward/lateral) -- its own diagonal is ~29 blocks, so this needs
    // real margin above that to guarantee covering the whole room regardless of exactly where the
    // player/guide each land within it.
    private static final double GREET_RANGE_BLOCKS = 36.0;
    private static final int POLL_INTERVAL_TICKS = 20;

    private Stage stage = Stage.NOT_GREETED;
    private UUID trackedPlayerId;
    private boolean boundAsConversational;
    private int pollTicks;

    public TutorialGuideEntity(EntityType<? extends Villager> type, Level level) {
        super(type, level);
        skipDropExperience();
        // Same fix as every other custom ground NPC in this suite (Settlemynts' Guard/Farmer/
        // Lumberjack/Miner/Resident, Myssions' own QuestNpcEntity) -- neither vanilla's Mob nor
        // AbstractVillager avoids tree canopies by default.
        this.setPathfindingMalus(PathType.LEAVES, -1.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Villager.createAttributes();
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(3, new RandomLookAroundGoal(this));
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        // Deliberately never calls super (Villager's own trading/schedule brain-AI).
        if (!boundAsConversational) {
            Audyble.bindConversationalNpc(this, "Guide", SYSTEM_PROMPT);
            boundAsConversational = true;
        }
        if (stage == Stage.NOT_GREETED) {
            greetNearbyPlayerIfAny(level);
            return;
        }
        if (stage == Stage.AWAITING_SET_ACTIVE && trackedPlayerId != null) {
            if (++pollTicks >= POLL_INTERVAL_TICKS) {
                pollTicks = 0;
                checkSetActiveProgress(level);
            }
        }
    }

    private void greetNearbyPlayerIfAny(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(this) <= GREET_RANGE_BLOCKS * GREET_RANGE_BLOCKS) {
                trackedPlayerId = player.getUUID();
                stage = Stage.AWAITING_MIC_CHECK;
                Audyble.speakScriptedLine(player, this,
                        "Hey! Come here a second, over here. Before we get going -- do you have a "
                                + "working microphone set up? If not, that's fine, just type \"no mic\" "
                                + "in chat and we'll go from there.");
                return;
            }
        }
    }

    private void checkSetActiveProgress(ServerLevel level) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(trackedPlayerId);
        if (player == null) {
            return;
        }
        Optional<UUID> instanceId = PendingOnboarding.getWelcomeQuestInstanceId(trackedPlayerId);
        if (instanceId.isEmpty()) {
            return;
        }
        Optional<QuestInstance> instance = Myssions.getQuestInstance(player, instanceId.get());
        boolean setActiveDone = instance.map(i -> i.milestoneProgress().getOrDefault("set_active", 0) > 0).orElse(false);
        if (!setActiveDone) {
            return;
        }
        stage = Stage.AWAITING_CHOP_TREE;
        ItemStack axe = new ItemStack(Items.IRON_AXE);
        if (!player.getInventory().add(axe)) {
            player.drop(axe, false);
        }
        Audyble.speakScriptedLine(player, this, "Nice work. Here, take this -- go chop down that tree over there.");
    }

    /** Called by {@code TutorialChopListener} when the tracked player right-clicks a log with an axe nearby -- advances past the last gated stage into free conversation. @return true if this guide actually claimed it (so the listener knows whether to consume the interaction). */
    public boolean onPlayerChoppedLog(ServerPlayer player) {
        if (stage != Stage.AWAITING_CHOP_TREE || !player.getUUID().equals(trackedPlayerId)) {
            return false;
        }
        stage = Stage.FREE_CONVERSATION;
        Audyble.speakScriptedLine(player, this,
                "There you go. Press K again and check your Skills tab -- that's where you'll see the experience you just earned.");
        return true;
    }

    /** Registered once as an {@code api.Audyble} reply interceptor -- see this class's own doc for why only {@code AWAITING_MIC_CHECK} is gated. */
    public Optional<String> handleIntercept(ServerPlayer player, String transcript) {
        if (stage != Stage.AWAITING_MIC_CHECK || !player.getUUID().equals(trackedPlayerId)) {
            return Optional.empty();
        }
        stage = Stage.AWAITING_SET_ACTIVE;
        return Optional.of("Great -- press K to open your menu, find the Quests tab, and set "
                + "\"Welcome to Kyngdoms\" as your active quest.");
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("Stage", stage.name());
        output.storeNullable("TrackedPlayerId", net.minecraft.core.UUIDUtil.CODEC, trackedPlayerId);
        output.putBoolean("BoundAsConversational", boundAsConversational);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        try {
            stage = Stage.valueOf(input.getStringOr("Stage", Stage.NOT_GREETED.name()));
        } catch (IllegalArgumentException e) {
            stage = Stage.NOT_GREETED;
        }
        trackedPlayerId = input.read("TrackedPlayerId", net.minecraft.core.UUIDUtil.CODEC).orElse(null);
        boundAsConversational = input.getBooleanOr("BoundAsConversational", false);
    }
}
