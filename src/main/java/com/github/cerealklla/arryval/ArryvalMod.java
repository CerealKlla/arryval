package com.github.cerealklla.arryval;

import com.github.cerealklla.arryval.onboarding.FirstLoginListener;
import com.github.cerealklla.arryval.registration.ModAttachments;
import com.github.cerealklla.arryval.registration.ModEntities;
import com.github.cerealklla.arryval.starttown.OnboardingExitHandler;
import com.github.cerealklla.arryval.starttown.OpenStartingSettlementPickerPayload;
import com.github.cerealklla.arryval.starttown.PendingOnboarding;
import com.github.cerealklla.arryval.starttown.PendingSettlementFormation;
import com.github.cerealklla.arryval.starttown.SetStartingSettlementPayload;
import com.github.cerealklla.arryval.starttown.StartingSettlementBootstrap;
import com.github.cerealklla.arryval.starttown.StartingSettlementCoreState;
import com.github.cerealklla.arryval.starttown.StartingSettlementResolver;
import com.github.cerealklla.arryval.tutorial.TutorialGuideEntity;
import com.github.cerealklla.arryval.tutorial.TutorialQuests;
import com.github.cerealklla.audyble.api.Audyble;
import com.github.cerealklla.lyfe.api.Lyfe;
import com.github.cerealklla.myssions.api.Myssions;
import com.github.cerealklla.tinywyrlds.api.TinyWyrlds;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(ArryvalMod.MODID)
public class ArryvalMod {

    public static final String MODID = "arryval";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ArryvalMod(IEventBus modEventBus, ModContainer modContainer) {
        ModAttachments.ATTACHMENT_TYPES.register(modEventBus);
        ModEntities.ENTITIES.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerAttributes);
        modEventBus.addListener(this::registerPayloads);

        NeoForge.EVENT_BUS.register(new FirstLoginListener());
        NeoForge.EVENT_BUS.register(new PendingSettlementFormation());
        NeoForge.EVENT_BUS.register(new StartingSettlementBootstrap());
        NeoForge.EVENT_BUS.register(new com.github.cerealklla.arryval.onboarding.TutorialRecipeBootstrap());
        NeoForge.EVENT_BUS.register(new com.github.cerealklla.arryval.tutorial.TutorialGuideInteractListener());
        NeoForge.EVENT_BUS.register(new com.github.cerealklla.arryval.starttown.VillagerTalkTracker());
        new com.github.cerealklla.arryval.starttown.VillageQuestTracker();
        new com.github.cerealklla.arryval.starttown.FoundingLetterListener();
        OnboardingExitHandler onboardingExitHandler = new OnboardingExitHandler();
        TinyWyrlds.registerExitHandler(onboardingExitHandler);
        NeoForge.EVENT_BUS.register(onboardingExitHandler);
        Audyble.registerReplyInterceptor((player, npc, transcript) ->
                npc instanceof TutorialGuideEntity guide ? guide.handleIntercept(player, transcript) : java.util.Optional.empty());
    }

    private void commonSetup(net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) {
        LOGGER.info("Arryval common setup");
        TutorialQuests.registerAll();
    }

    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.TUTORIAL_GUIDE.get(), TutorialGuideEntity.createAttributes().build());
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(OpenStartingSettlementPickerPayload.TYPE, OpenStartingSettlementPickerPayload.STREAM_CODEC,
                (payload, context) -> com.github.cerealklla.arryval.starttown.client.ClientStartTownRequests.requestPicker(payload));
        registrar.playToServer(SetStartingSettlementPayload.TYPE, SetStartingSettlementPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        confirmStartingSettlement(player, payload.index());
                    }
                });
    }

    /**
     * Re-validates {@code index} against this player's own cached candidate list (never trusts the
     * client's index blindly). Never claims a plot or any ownership -- confirmed with the user
     * 2026-11-06 after a prior unconfirmed assumption ("at what point did I ever say anything about
     * 'assigning the player a plot'?"); this only ever binds the Totem to a location (see {@code
     * api.Settlemynts#findStartingBindLocation}'s own doc: a Recallcinite Stone-zoned plot if the
     * settlement has one, otherwise any plot). A {@link StartingSettlementResolver.Candidate
     * #isReady()} candidate (an already-founded settlement) binds immediately. A not-yet-ready one (a
     * seed-predicted village nobody's founded yet -- see {@code StartingSettlementResolver}'s own
     * doc) teleports the player to its predicted position instead, which is what actually kicks off
     * its real formation, and hands off to {@link PendingSettlementFormation} to finish the binding
     * once it's real.
     *
     * <p><b>Two real bugs fixed, 2026-11-06, found live</b>: (1) teleporting straight to {@code
     * bindPos}/{@code predictedPos}'s own raw Y (a plot's exact box position, or sea level for a
     * not-yet-founded village) could land the player inside solid terrain -- fixed by resolving a
     * real surface Y via {@code Heightmap.Types.MOTION_BLOCKING_NO_LEAVES} at teleport time instead,
     * same technique this suite already uses everywhere else a ground position is needed (see the
     * root project's own CLAUDE.md). (2) This direct teleport bypasses the real exit-portal mechanism
     * entirely, which normally also clears the player's own "I'm inside a Wyrld" tracking state --
     * skipping that left it permanently stuck, so the player's *next* death anywhere (not just inside
     * a Wyrld) incorrectly respawned them back inside the tutorial. Fixed by calling {@link
     * TinyWyrlds#markLeftWyrld} alongside every such teleport -- see that method's own doc for the
     * full account.
     */
    private static void confirmStartingSettlement(ServerPlayer player, int index) {
        var candidates = PendingOnboarding.getCachedCandidates(player.getUUID());
        if (index < 0 || index >= candidates.size()) {
            return;
        }
        StartingSettlementResolver.Candidate chosen = candidates.get(index);
        ServerLevel overworld = ((ServerLevel) player.level()).getServer().overworld();

        PendingOnboarding.clear(player.getUUID());
        player.setData(ModAttachments.SETTLEMENT_CHOSEN_STATE, new com.github.cerealklla.arryval.onboarding.SettlementChosenState(true));
        TinyWyrlds.markLeftWyrld(player);

        if (chosen.bindPos().isEmpty()) {
            // Not yet founded for real, or founded with no plot to bind to yet -- teleport there
            // anyway (that's what actually kicks off its real formation) and wait for it. This one
            // specific destination chunk MUST be force-generated before computing a safe landing
            // height -- real bug, found live 2026-11-06: a never-before-touched predicted location's
            // chunk genuinely has no real heightmap data yet, so skipping this produced a garbage Y
            // and a straight fall to the void. This is NOT the same cost StartingSettlementResolver's
            // own "don't force-load" fix (the 4-second popup delay) was about -- that was scanning
            // all 8 candidates just to show a menu; this is the one real destination the player is
            // actually about to stand in, which has no way around generating for real regardless.
            BlockPos pos = chosen.predictedPos();
            overworld.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
            int safeY = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
            BlockPos landingPos = new BlockPos(pos.getX(), safeY, pos.getZ());
            // A provisional Totem bind at the predicted location -- real bug found live, 2026-11-06:
            // the real bind only happens once PendingSettlementFormation confirms the real plot, so a
            // player who died anywhere during that wait (a real window they spend real time standing
            // in) had no anchor at all yet and respawned at vanilla's own default instead. Rebinding
            // again with the real, precise plot position once formation completes (see
            // PendingSettlementFormation#finishBinding) simply overwrites this one -- safe to bind
            // twice in succession, same as any other rebind.
            Lyfe.bindRecallciniteTotem(player, GlobalPos.of(overworld.dimension(), landingPos));
            player.teleportTo(overworld, pos.getX() + 0.5, safeY, pos.getZ() + 0.5,
                    java.util.Set.of(), player.getYRot(), player.getXRot(), true);
            PendingSettlementFormation.beginWaiting(player, chosen.name(), pos);
            return;
        }

        BlockPos bindPos = chosen.bindPos().get();
        GlobalPos globalPos = GlobalPos.of(overworld.dimension(), bindPos);
        Lyfe.bindRecallciniteTotem(player, globalPos);
        StartingSettlementCoreState.recordFor(player, overworld, bindPos);

        overworld.getChunk(bindPos.getX() >> 4, bindPos.getZ() >> 4); // see the not-ready branch above's own doc.
        int safeY = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bindPos.getX(), bindPos.getZ());
        player.teleportTo(overworld, bindPos.getX() + 0.5, safeY, bindPos.getZ() + 0.5,
                java.util.Set.of(), player.getYRot(), player.getXRot(), true);
        player.sendSystemMessage(Component.literal("Welcome to " + chosen.name() + "! Your Recallcinite Totem is bound here."));
    }
}
