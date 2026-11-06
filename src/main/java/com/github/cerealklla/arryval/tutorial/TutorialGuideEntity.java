package com.github.cerealklla.arryval.tutorial;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.github.cerealklla.arryval.config.ArryvalConfig;
import com.github.cerealklla.arryval.tts.TutorialVoicePayload;
import com.github.cerealklla.nuclyus.api.Nuclyus;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The single, fixed, hand-scripted tutorial guide -- spawned once per tutorial Wyrld generation via
 * the {@code WelcomeTutorial} recipe's "friendly pool" (a weighted {@code EntityType} list, so the
 * generator spawns this type directly at the captured "friendly spawn" marker with no extra
 * spawn-code needed). A real {@link Villager} subclass, same precedent as Myssions' own {@code
 * quest.QuestNpcEntity}, but unlike that class this one deliberately *keeps* real pathfinding
 * navigation (plain vanilla {@code Mob#getNavigation().moveTo}) so it can walk between fixed
 * waypoints as {@link TutorialScript}'s stages advance -- confirmed directly against the decompiled
 * vanilla source that {@code Mob#aiStep} ticks {@code navigation}/{@code goalSelector} *before*
 * calling {@code customServerAiStep}, so overriding that method to skip {@code Villager}'s own
 * trading/schedule brain-AI (same as {@code QuestNpcEntity} does) does not also disable navigation.
 *
 * <p>Never visits Audyble/Ollama/Whisper at all -- speaks each stage's lines via Nuclyus's
 * independent TTS facade ({@link Nuclyus#speak}) directly to the interacting player only (see
 * {@code tts.TutorialVoicePayload}/{@code tts.client.ClientTutorialVoicePlayer}), guaranteed,
 * never-improvised text either way (chat line shown regardless of whether TTS succeeds).
 */
public class TutorialGuideEntity extends Villager {

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "arryval-tutorial-tts");
        t.setDaemon(true);
        return t;
    });

    private int stageIndex;
    private BlockPos spawnPos;

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
    protected void customServerAiStep(net.minecraft.server.level.ServerLevel level) {
        // Deliberately never calls super (Villager's own trading/schedule brain-AI) -- this NPC
        // exists purely to stand in its tutorial room, walk between scripted waypoints, and be
        // talked to. Navigation still ticks regardless (see this class's own doc).
    }

    /** Called by {@code TutorialGuideInteractListener} on a real interaction. Always returns true -- there's always at least a closing line once every stage is spent. */
    public boolean talkTo(ServerPlayer player) {
        if (spawnPos == null) {
            spawnPos = blockPosition();
        }
        if (stageIndex >= TutorialScript.STAGES.size()) {
            String closing = "Good luck out there.";
            player.sendSystemMessage(Component.literal(closing));
            speak(player, closing);
            return true;
        }
        TutorialScript.Stage stage = TutorialScript.STAGES.get(stageIndex);
        stageIndex++;

        if (stage.dx() != 0 || stage.dz() != 0) {
            double targetX = spawnPos.getX() + stage.dx() + 0.5;
            double targetZ = spawnPos.getZ() + stage.dz() + 0.5;
            getNavigation().moveTo(targetX, spawnPos.getY(), targetZ, 0.5);
        }
        for (String line : stage.lines()) {
            player.sendSystemMessage(Component.literal(line));
            speak(player, line);
        }
        return true;
    }

    private void speak(ServerPlayer player, String line) {
        if (!Nuclyus.isTtsConfigured()) {
            return; // Chat text alone is still a complete, guaranteed instruction -- never block the tutorial on Kokoro being unavailable.
        }
        MinecraftServer server = ((net.minecraft.server.level.ServerLevel) player.level()).getServer();
        String voice = ArryvalConfig.TUTORIAL_GUIDE_VOICE.get();
        EXECUTOR.submit(() -> {
            try {
                byte[] wav = Nuclyus.speak(voice, line);
                server.execute(() -> PacketDistributor.sendToPlayer(player, new TutorialVoicePayload(wav)));
            } catch (Exception e) {
                // Best-effort -- the chat line already shown is the guaranteed part; a failed TTS
                // call just means this one line stays silent.
            }
        });
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("StageIndex", stageIndex);
        output.storeNullable("SpawnPos", BlockPos.CODEC, spawnPos);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        stageIndex = input.getIntOr("StageIndex", 0);
        spawnPos = input.read("SpawnPos", BlockPos.CODEC).orElse(null);
    }
}
