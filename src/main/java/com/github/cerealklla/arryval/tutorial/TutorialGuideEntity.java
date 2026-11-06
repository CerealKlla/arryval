package com.github.cerealklla.arryval.tutorial;

import com.github.cerealklla.audyble.api.Audyble;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The single, fixed tutorial guide -- spawned once per tutorial Wyrld generation via the {@code
 * WelcomeTutorial} recipe's "friendly pool" (a weighted {@code EntityType} list, so the generator
 * spawns this type directly at the captured "friendly spawn" marker with no extra spawn-code
 * needed). A real {@link Villager} subclass, same precedent as Myssions' own {@code
 * quest.QuestNpcEntity}.
 *
 * <p><b>Rebuilt 2026-11-06, explicit user direction</b> -- a prior version advanced a hand-scripted
 * dialogue by right-click, deliberately never touching Audyble's LLM conversation engine (to
 * guarantee instructions were never improvised). Live-tested and found unsatisfying: the whole
 * point of a tutorial introducing this server is to actually demonstrate talking to an NPC, which a
 * one-way scripted monologue doesn't do. Now a real conversational NPC, bound via {@link
 * Audyble#bindConversationalNpc} with a system prompt listing the required topics as a checklist
 * rather than verbatim lines -- the LLM has room to respond naturally to whatever the player
 * actually says, while still being steered to cover every topic over the course of the
 * conversation. {@link #registerGoals} is overridden (not left as {@link Villager}'s own default)
 * so it stands and looks at whoever's talking to it instead of wandering/trading/fleeing like an
 * ordinary villager -- same shape {@code QuestNpcEntity} already established for this.
 */
public class TutorialGuideEntity extends Villager {

    private static final String SYSTEM_PROMPT =
            "You are a friendly tutorial guide welcoming a brand-new player to the Kyngdoms server "
                    + "for the very first time. Have a natural, warm, encouraging back-and-forth "
                    + "conversation with them -- never deliver a long speech, keep each reply short "
                    + "like real spoken dialogue. Over the course of the conversation, make sure you "
                    + "genuinely cover all of these topics (not necessarily in this order, and not all "
                    + "at once -- weave them in naturally as the conversation goes, and respond to "
                    + "whatever the player actually says):\n"
                    + "- Voice chat setup: tell them to open their Voice Chat settings with the default "
                    + "key, V, and make sure their microphone is enabled.\n"
                    + "- Settlemynts: towns and plots.\n"
                    + "- Lyfe: tracks their skills, and holds their Recallcinite Totem -- their real "
                    + "respawn point.\n"
                    + "- Yconomics: runs the economy.\n"
                    + "- Blueprynts: lets them build real structures from blueprints.\n"
                    + "- Myssions: tracks quests.\n"
                    + "- Audyble: this is literally what's happening right now -- NPCs can genuinely "
                    + "hear and talk back.\n"
                    + "- There's a chest nearby with some starter supplies -- let them know to grab it.\n"
                    + "- When they're ready, they should step through the portal ahead to choose their "
                    + "first town.";

    private boolean boundAsConversational;

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
    protected void customServerAiStep(net.minecraft.server.level.ServerLevel level) {
        // Deliberately never calls super (Villager's own trading/schedule brain-AI). Binding happens
        // here (once, lazily, on the first real AI tick after spawn) rather than in the constructor
        // -- Audyble's own conversational-NPC attachment just needs to exist on this entity before
        // the first time a player speaks near it, and the constructor runs before the entity is
        // actually a real, addressable part of the world.
        if (!boundAsConversational) {
            Audyble.bindConversationalNpc(this, "Guide", SYSTEM_PROMPT);
            boundAsConversational = true;
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("BoundAsConversational", boundAsConversational);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        boundAsConversational = input.getBooleanOr("BoundAsConversational", false);
    }
}
