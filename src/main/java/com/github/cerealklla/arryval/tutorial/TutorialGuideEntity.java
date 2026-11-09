package com.github.cerealklla.arryval.tutorial;

import java.util.Optional;
import java.util.UUID;

import com.github.cerealklla.arryval.starttown.PendingOnboarding;
import com.github.cerealklla.audyble.api.Audyble;
import com.github.cerealklla.myssions.api.Myssions;
import com.github.cerealklla.myssions.quest.QuestInstance;
import com.github.cerealklla.settlemynts.api.Settlemynts;
import com.github.cerealklla.tinywyrlds.api.TinyWyrlds;
import com.github.cerealklla.tinywyrlds.wyrldprint.entrance.EntranceEnterResult;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
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
 * telling them to set the tutorial quest active, then hands over a wooden sword once that's actually
 * done, then waits for a real hostile kill before finally opening up into free LLM conversation.
 * Deliberately never a pickaxe/axe or anything else that implies breaking a block -- the Wyrld room
 * itself permits no voxel creation or destruction at all (see TinyWyrlds' own CLAUDE.md), so a tool
 * whose only use is mining/chopping would be a real dead-end item the player can't actually use here;
 * explicit user correction, 2026-11-06, after an earlier draft handed out a stone pickaxe anyway.
 * {@link #handleIntercept} is registered once (see {@code ArryvalMod}'s constructor) as an {@code
 * api.Audyble} reply interceptor -- it only ever claims a turn while {@link #stage} is {@code
 * AWAITING_MIC_CHECK}; every other stage (including the final {@code FREE_CONVERSATION}) falls
 * through to the real conversational pipeline untouched, so nothing here risks the LLM improvising
 * over a guaranteed instruction, but normal conversation can still happen alongside the later
 * staged waits if the player talks in the meantime.
 *
 * <p><b>Fixed identity, added 2026-11-06</b> -- live-test report: the guide audibly started as a man
 * and "immediately turned into a named female," jarring. Root cause (confirmed by reading
 * Settlemynts' {@code npc.NpcIdentityBirth}): the normal "wandering" identity path synchronously
 * claims a throwaway MALE placeholder, then overwrites it moments later once its async LLM-authored
 * name/gender generation finishes -- correct for an ordinary Wandering Trader nobody's mid-
 * conversation with, wrong for a scripted character whose very first lines are spoken in that same
 * window. {@link #customServerAiStep} now calls {@link Settlemynts#seedFixedNpcIdentity} every tick
 * (idempotent, a no-op after the first) before ever binding/speaking, so the real identity (this
 * class's own fixed name + gender, never LLM-generated) is already in the store before the first
 * conversation turn -- the normal path's placeholder is never created at all.
 */
public class TutorialGuideEntity extends Villager {

    private enum Stage {
        NOT_GREETED, AWAITING_MIC_CHECK, AWAITING_SET_ACTIVE, AWAITING_FIRST_KILL, FREE_CONVERSATION
    }

    private static final String FIXED_NAME = "Guide";
    private static final String FIXED_GENDER = "MALE";

    private static final String SYSTEM_PROMPT =
            "You are a friendly tutorial guide welcoming a brand-new player to the Kyngdoms server "
                    + "for the very first time. Have a natural, warm, encouraging back-and-forth "
                    + "conversation with them -- never deliver a long speech, keep each reply short "
                    + "like real spoken dialogue. Never refer to this server's mods by name (not "
                    + "\"Settlemynts,\" \"Lyfe,\" \"Myssions,\" etc.) -- that breaks immersion. Only "
                    + "talk about what the player actually experiences: real actions, real keys to "
                    + "press, real places and things in the world. You've already greeted them, "
                    + "asked about their microphone, told them to set their tutorial quest active, "
                    + "and (once that's done) handed them a wooden sword and had them fight something "
                    + "-- don't repeat any of that unless they ask. From here, just talk naturally: "
                    + "towns have plots of land people call home, a real economy to buy and sell "
                    + "into, and buildings you can construct; doing things in the world levels up "
                    + "your skills, viewable by pressing K; when they're ready, stepping through the "
                    + "portal ahead lets them choose the town they call home, and checking their "
                    + "quest log (J) again afterward will have a new quest waiting to accept.";

    // The captured room is 22x19 (forward/lateral) -- its own diagonal is ~29 blocks, so this needs
    // real margin above that to guarantee covering the whole room regardless of exactly where the
    // player/guide each land within it.
    private static final double GREET_RANGE_BLOCKS = 36.0;
    private static final int POLL_INTERVAL_TICKS = 20;

    /** The real tags typed into the room's own three Tagged Levers (see {@code
     *  tinywyrlds.wyrldprint.interactive.TaggedInteractiveBlockEntity}'s own doc) -- explicit user
     *  spec, 2026-11-06: progressive unlocking, one door per stage of the tutorial ("Door1" opens
     *  the instant the sword is handed over, "Door2" opens after the first kill, and the original
     *  "PreExitDoor" opens once all 4 hostiles are cleared). */
    private static final String DOOR1_SWORD_HANDED_TAG = "Door1";
    private static final String DOOR2_FIRST_KILL_TAG = "Door2";
    private static final String DOOR_LEVER_TAG = "PreExitDoor";

    /** Distances for {@link FollowTrackedPlayerGoal} -- hysteresis (start far, stop close) so the
     *  guide doesn't jitter start/stop right at one fixed threshold. */
    private static final double FOLLOW_START_DIST_BLOCKS = 5.0;
    private static final double FOLLOW_STOP_DIST_BLOCKS = 2.5;
    private static final double FOLLOW_SPEED = 0.6;

    private Stage stage = Stage.NOT_GREETED;
    private UUID trackedPlayerId;
    private boolean boundAsConversational;
    private int pollTicks;

    /** Explicit user spec, 2026-11-06: "The Guide needs to start following you after you open door
     *  1, so that when he keeps talking you can actually still hear him" -- speech (TTS/chat) is
     *  positional/range-based, so once the player moves on past Door1 the guide needs to keep pace
     *  or later lines simply go unheard. Set true the instant Door1 opens ({@link
     *  #checkSetActiveProgress}), never cleared again. */
    private boolean followEnabled;

    // Independent of `stage`/the dialogue flow entirely -- a player could clear every hostile
    // before or after finishing the mic-check/set-active dialogue, so this polls on its own rather
    // than slotting into one particular Stage.
    private boolean doorOpened;
    private int doorPollTicks;

    public TutorialGuideEntity(EntityType<? extends Villager> type, Level level) {
        super(type, level);
        skipDropExperience();
        // Same fix as every other custom ground NPC in this suite (Settlemynts' Guard/Farmer/
        // Lumberjack/Miner/Resident, Myssions' own QuestNpcEntity) -- neither vanilla's Mob nor
        // AbstractVillager avoids tree canopies by default.
        this.setPathfindingMalus(PathType.LEAVES, -1.0F);
        // Explicit user spec, 2026-11-06: "The Guide should be invincible" -- a fresh player
        // accidentally hitting it (or a zombie wandering onto it) shouldn't be able to break the
        // only NPC the whole tutorial depends on.
        this.setInvulnerable(true);
        // Real bug, found live 2026-11-06 (see TutorialGuideInteractListener's own doc): the fixed
        // "Guide" identity was only ever written into Settlemynts' separate NpcIdentityStore, never
        // onto the real entity -- getDisplayName() (and anything else reading vanilla's own name)
        // fell back to the generic "Villager." Set directly here since this identity is a fixed
        // compile-time constant, not async-generated like a wandering NPC's.
        this.setCustomName(net.minecraft.network.chat.Component.literal(FIXED_NAME));
        this.setCustomNameVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Villager.createAttributes();
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new FollowTrackedPlayerGoal());
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
    }

    /** See {@link #followEnabled}'s own doc. A plain "walk to the tracked player once they're far
     *  enough away" goal -- {@code net.minecraft.world.entity.Mob}'s own navigation handles the
     *  actual pathfinding; this just re-issues {@code moveTo} periodically while out of range. */
    private class FollowTrackedPlayerGoal extends Goal {
        private ServerPlayer target;
        private int recalcTicks;

        private FollowTrackedPlayerGoal() {
            setFlags(java.util.EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!followEnabled || trackedPlayerId == null || !(level() instanceof ServerLevel level)) {
                return false;
            }
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(trackedPlayerId);
            if (player == null || !player.isAlive() || player.level() != level) {
                return false;
            }
            target = player;
            return distanceToSqr(player) > FOLLOW_START_DIST_BLOCKS * FOLLOW_START_DIST_BLOCKS;
        }

        @Override
        public boolean canContinueToUse() {
            return target != null && target.isAlive() && target.level() == level()
                    && distanceToSqr(target) > FOLLOW_STOP_DIST_BLOCKS * FOLLOW_STOP_DIST_BLOCKS;
        }

        @Override
        public void start() {
            recalcTicks = 0;
            getNavigation().moveTo(target, FOLLOW_SPEED);
        }

        @Override
        public void stop() {
            target = null;
            getNavigation().stop();
        }

        @Override
        public void tick() {
            getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (--recalcTicks <= 0) {
                recalcTicks = 10;
                getNavigation().moveTo(target, FOLLOW_SPEED);
            }
        }
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        // Deliberately never calls super (Villager's own trading/schedule brain-AI).
        Settlemynts.seedFixedNpcIdentity(this.getUUID(), FIXED_NAME, FIXED_GENDER);
        if (!boundAsConversational) {
            Audyble.bindConversationalNpc(this, FIXED_NAME, SYSTEM_PROMPT);
            boundAsConversational = true;
        }
        if (stage == Stage.NOT_GREETED) {
            greetNearbyPlayerIfAny(level);
            return;
        }
        if (trackedPlayerId == null) {
            return;
        }
        if (stage == Stage.AWAITING_SET_ACTIVE) {
            if (++pollTicks >= POLL_INTERVAL_TICKS) {
                pollTicks = 0;
                checkSetActiveProgress(level);
            }
        } else if (stage == Stage.AWAITING_FIRST_KILL) {
            if (++pollTicks >= POLL_INTERVAL_TICKS) {
                pollTicks = 0;
                checkFirstKillProgress(level);
            }
        }
        if (!doorOpened && ++doorPollTicks >= POLL_INTERVAL_TICKS) {
            doorPollTicks = 0;
            checkDoorProgress(level);
        }
    }

    private void greetNearbyPlayerIfAny(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(this) <= GREET_RANGE_BLOCKS * GREET_RANGE_BLOCKS) {
                trackedPlayerId = player.getUUID();
                stage = Stage.AWAITING_MIC_CHECK;
                Audyble.speakScriptedLine(player, this,
                        "Hey! Come here a second, over here. If you've got a microphone, press V "
                                + "to open your voice chat settings and make sure it's turned on. "
                                + "Once that's set -- or if you don't have one -- just let me know. "
                                + "Go ahead and say so, or type \"no mic\" in chat.");
                return;
            }
        }
    }

    private void checkSetActiveProgress(ServerLevel level) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(trackedPlayerId);
        if (player == null) {
            return;
        }
        Optional<UUID> instanceId = resolveWelcomeQuestInstanceId(player);
        if (instanceId.isEmpty()) {
            return;
        }
        Optional<QuestInstance> instance = Myssions.getQuestInstance(player, instanceId.get());
        boolean setActiveDone = instance.map(i -> i.milestoneProgress().getOrDefault("set_active", 0) > 0).orElse(false);
        if (!setActiveDone) {
            return;
        }
        stage = Stage.AWAITING_FIRST_KILL;
        followEnabled = true;
        ItemStack sword = new ItemStack(Items.WOODEN_SWORD);
        if (!player.getInventory().add(sword)) {
            player.drop(sword, false);
        }
        Audyble.speakScriptedLine(player, this,
                "Nice work. Here, take this sword -- there's something hostile further in. Just "
                        + "look at it and attack like normal, and keep swinging until it goes down.");
        resolveDungeonInstanceId().ifPresent(id -> TinyWyrlds.toggleState(id, DOOR1_SWORD_HANDED_TAG));
    }

    /** Polls the same "Welcome to Kyngdoms" quest instance for the kill milestone's own progress -- Myssions already tracks a real hostile kill automatically (see its own {@code quest.QuestKillListener}), so no custom combat-detection listener is needed here at all. */
    private void checkFirstKillProgress(ServerLevel level) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(trackedPlayerId);
        if (player == null) {
            return;
        }
        Optional<UUID> instanceId = resolveWelcomeQuestInstanceId(player);
        if (instanceId.isEmpty()) {
            return;
        }
        Optional<QuestInstance> instance = Myssions.getQuestInstance(player, instanceId.get());
        boolean killDone = instance.map(i -> i.milestoneProgress().getOrDefault("defeat_first_hostile", 0) > 0).orElse(false);
        if (!killDone) {
            return;
        }
        stage = Stage.FREE_CONVERSATION;
        Audyble.speakScriptedLine(player, this,
                "There you go. Press K again and check your Skills tab -- that's where you'll see "
                        + "the experience you just earned. When you're ready, make your way through "
                        + "the halls -- there's more ahead.");
        resolveDungeonInstanceId().ifPresent(id -> TinyWyrlds.toggleState(id, DOOR2_FIRST_KILL_TAG));
    }

    /** Request #5, 2026-11-06: the Welcome quest is now offered, not auto-accepted at login, so no
     *  instance id exists in {@link PendingOnboarding} until the player actually accepts it via the
     *  new "Press &lt;key&gt; to review quest offer" flow -- lazily resolves and caches the real
     *  instance id the first time one shows up as in-progress, via {@code
     *  Myssions#findInProgressInstanceId}. A no-op re-lookup once already cached. */
    private Optional<UUID> resolveWelcomeQuestInstanceId(ServerPlayer player) {
        Optional<UUID> cached = PendingOnboarding.getWelcomeQuestInstanceId(trackedPlayerId);
        if (cached.isPresent()) {
            return cached;
        }
        Optional<UUID> found = Myssions.findInProgressInstanceId(player, TutorialQuests.WELCOME_QUEST_ID);
        found.ifPresent(id -> PendingOnboarding.setWelcomeQuestInstanceId(trackedPlayerId, id));
        return found;
    }

    /** Shared by every door-trigger check -- the real generated dungeon instance id, resolved via {@code PendingOnboarding}'s tracked entrance id -> {@code TinyWyrlds#getEnterResult}. */
    private Optional<UUID> resolveDungeonInstanceId() {
        return PendingOnboarding.getTutorialEntranceId(trackedPlayerId)
                .flatMap(TinyWyrlds::getEnterResult)
                .map(EntranceEnterResult::wyrldId);
    }

    /**
     * Polls the same quest instance's "clear_all_hostiles" milestone -- once all 4 of the room's
     * real spawned hostiles are dead, resolves the real generated dungeon instance id (via {@code
     * PendingOnboarding}'s tracked entrance id -> {@code TinyWyrlds#getEnterResult}) and flips the
     * room's own Tagged Lever open. Independent of {@link #stage} -- see this field's own doc.
     */
    private void checkDoorProgress(ServerLevel level) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(trackedPlayerId);
        if (player == null) {
            return;
        }
        Optional<UUID> questInstanceId = resolveWelcomeQuestInstanceId(player);
        if (questInstanceId.isEmpty()) {
            return;
        }
        Optional<QuestInstance> instance = Myssions.getQuestInstance(player, questInstanceId.get());
        boolean allClear = instance.map(i -> i.milestoneProgress().getOrDefault("clear_all_hostiles", 0) >= 4).orElse(false);
        if (!allClear) {
            return;
        }
        Optional<UUID> dungeonInstanceId = resolveDungeonInstanceId();
        if (dungeonInstanceId.isEmpty()) {
            return;
        }
        doorOpened = true;
        TinyWyrlds.toggleState(dungeonInstanceId.get(), DOOR_LEVER_TAG);
    }

    /** Registered once as an {@code api.Audyble} reply interceptor -- see this class's own doc for why only {@code AWAITING_MIC_CHECK} is gated. */
    public Optional<String> handleIntercept(ServerPlayer player, String transcript) {
        if (stage != Stage.AWAITING_MIC_CHECK || !player.getUUID().equals(trackedPlayerId)) {
            return Optional.empty();
        }
        stage = Stage.AWAITING_SET_ACTIVE;
        // Request #5, 2026-11-06, corrected same day: offered here, not at login -- explicit user
        // correction, "it should only offer the quest after the mic is configured or confirmed not
        // available." This is that exact moment.
        Myssions.offerQuest(player, TutorialQuests.WELCOME_QUEST_ID);
        return Optional.of("Great -- you've got a quest waiting on you. Press U to review it, hit "
                + "Accept, then open your menu with K, find the Quests tab, and set \"Welcome to "
                + "Kyngdoms\" as your active quest.");
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("Stage", stage.name());
        output.storeNullable("TrackedPlayerId", net.minecraft.core.UUIDUtil.CODEC, trackedPlayerId);
        output.putBoolean("BoundAsConversational", boundAsConversational);
        output.putBoolean("DoorOpened", doorOpened);
        output.putBoolean("FollowEnabled", followEnabled);
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
        doorOpened = input.getBooleanOr("DoorOpened", false);
        followEnabled = input.getBooleanOr("FollowEnabled", false);
    }
}
