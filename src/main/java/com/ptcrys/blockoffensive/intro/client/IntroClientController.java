package com.ptcrys.blockoffensive.intro.client;

import com.mojang.authlib.GameProfile;
import com.ptcrys.fpsmatch.common.camera.SequenceClock;
import com.ptcrys.fpsmatch.common.client.camera.*;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.ptcrys.blockoffensive.BlockOffensive;
import com.ptcrys.blockoffensive.intro.IntroCameraProfile;
import com.ptcrys.blockoffensive.intro.IntroGroundResolver;
import com.ptcrys.blockoffensive.intro.IntroMotionProfile;
import com.ptcrys.blockoffensive.intro.IntroPhase;
import com.ptcrys.blockoffensive.intro.IntroSequence;
import com.ptcrys.blockoffensive.intro.IntroDisplayWeaponResolver;
import com.ptcrys.blockoffensive.mixin.client.ClientPacketListenerAccessor;
import com.ptcrys.blockoffensive.mixin.client.PlayerInfoInvoker;
import com.ptcrys.blockoffensive.intro.net.IntroClientDoneC2SPacket;
import com.ptcrys.blockoffensive.intro.net.IntroSequenceS2CPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;

@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = BlockOffensive.MODID, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class IntroClientController {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int PREVIEW_ENTITY_ID_BASE = -2_100_500_000;
    private static RunningIntro active;

    private IntroClientController() {
    }

    public static void accept(IntroSequenceS2CPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (packet.phase == IntroPhase.STOP) {
            if (active != null && active.packet.sequenceId.equals(packet.sequenceId)) reset(minecraft, false);
            return;
        }
        if (minecraft.level == null || minecraft.player == null || (packet.players.isEmpty() && packet.phase != IntroPhase.PREARM)) {
            return;
        }

        if (active != null && active.packet.sequenceId.equals(packet.sequenceId)
                && active.packet.phase == packet.phase) return;
        if (!CameraDirector.accepts(CameraDirector.CINEMATIC_PRIORITY)) return;
        if (active != null && active.packet.startGameTime >= 0 && packet.startGameTime >= 0
                && packet.startGameTime < active.packet.startGameTime) return;
        reset(minecraft, false, true);
        int elapsed = packet.startGameTime < 0 ? 0 : (int) Math.min(Integer.MAX_VALUE,
                Math.max(0, minecraft.level.getGameTime() - packet.startGameTime));
        RunningIntro intro = new RunningIntro(packet, elapsed);
        active = intro;
        intro.cameraSession = CameraDirector.play("blockoffensive:intro/" + packet.sequenceId,
                CameraDirector.CINEMATIC_PRIORITY, time -> sampleCamera(intro, time),
                packet.phase == IntroPhase.PREVIEW5 ? CameraPolicy.PREVIEW : CameraPolicy.CINEMATIC,
                intro.clock, () -> active == intro && minecraft.player != null, CameraLifetime.SCENE,
                reason -> { if (active == intro) reset(minecraft, false); });
        if (intro.cameraSession == null) {
            reset(minecraft, false);
            return;
        }
        if (packet.phase == IntroPhase.PREARM) {
            active.introSoundSkippedPrearm = true;
            boolean cameraReady = prepareCamera(minecraft, packet);
            LOGGER.info("[BlockOffensive Halftime] Client prearmed cinematic cover {} map={}:{} preRollTicks={}",
                    packet.sequenceId, packet.gameType, packet.mapName, packet.preRollTicks);
            BlockOffensive.INSTANCE.sendToServer(new IntroClientDoneC2SPacket(packet.sequenceId, cameraReady ? "prearm-camera-ready" : "prearm-active"));
            return;
        }
        if (packet.phase == IntroPhase.PREVIEW5) {
            createPreviewActors(minecraft, active);
        } else {
            applyHeldItemOverrides(minecraft, active);
        }
        boolean cameraReady = prepareCamera(minecraft, packet);
        if (!cameraReady) return;
        if (packet.phase != IntroPhase.PREVIEW5) {
            for (int i = 0; i < packet.players.size(); i++) {
                IntroSequence.PlayerPath path = packet.players.get(i);
                Player player = minecraft.level.getPlayerByUUID(path.playerId());
                if (player != null) {
                    boolean animated = IntroPlayerAnimatorBridge.play(player, packet.phase, i);
                    LOGGER.debug("[BlockOffensive Halftime] Client animation {} for player {}", animated ? "started" : "fallback", path.playerId());
                    active.animatedPlayers.add(path.playerId());
                }
            }
        }
        ResourceLocation animationId = IntroPlayerAnimatorBridge.animationIdForPhase(packet.phase);
        boolean bendConsumed = IntroPlayerAnimatorBridge.verifyRuntimeBendConsumption(packet.phase);
        LOGGER.info("[BlockOffensive Halftime] Client accepted intro {} phase={} animation={} cameraProfile={} map={}:{} side={} players={} heldItems={}/{} previewActorMainHands={}/{} overrides={} preRollTicks={} cinematicReadyAtTick={} cameraReady={} playerAnimatorLoaded={} bendyLibLoaded={} bendConsumed={} introDisplayWeaponRule={} cameraStart={} cameraEnd={}",
                packet.sequenceId, packet.phase, animationId, IntroCameraProfile.PROFILE_ID, packet.gameType, packet.mapName, packet.side.id(), packet.players.size(),
                countNonEmpty(packet.heldItems), packet.heldItems.size(), previewActorMainHandCount(active), active.previewActors.size(),
                active.heldItemOverrides.size(), packet.preRollTicks,
                packet.cinematicReadyAtTick(), cameraReady, IntroPlayerAnimatorBridge.isPlayerAnimatorLoaded(), IntroPlayerAnimatorBridge.isBendyLibLoaded(),
                bendConsumed, IntroDisplayWeaponResolver.RULE_ID,
                packet.cameraStart, packet.cameraEnd);
        if (cameraReady) {
            BlockOffensive.INSTANCE.sendToServer(new IntroClientDoneC2SPacket(packet.sequenceId, "camera-ready"));
        }
    }

    public static void registerClient() {
        IntroPlayerAnimatorBridge.registerFactoryIfPresent();
    }

    public static boolean isCinematicActive() {
        return active != null;
    }

    public static void recordExternalAnimationBlocked(String source) {
        RunningIntro intro = active;
        if (intro == null) {
            return;
        }
        intro.externalAnimationBlocks++;
        if (!intro.externalAnimationBlockLogged) {
            intro.externalAnimationBlockLogged = true;
            LOGGER.info("[BlockOffensive Halftime] External animation isolated sequence={} phase={} source={}",
                    intro.packet.sequenceId, intro.packet.phase, source);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || active == null || Minecraft.getInstance().isPaused()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            reset(minecraft, false);
            return;
        }

        if (active.packet.phase == IntroPhase.PREARM) {
            prepareCamera(minecraft, active.packet);
            if (active.clock.ticks() > active.packet.preRollTicks + 60) {
                reset(minecraft, false);
                return;
            }
            return;
        }

        int duration = Math.max(1, active.packet.durationTicks);
        int movementStartTick = active.packet.movementStartTick();
        int totalTicks = movementStartTick + duration;
        updateIntroSound(minecraft, active, movementStartTick, totalTicks);
        if (active.clock.ticks() > totalTicks + 8) {
            reset(minecraft, true);
            return;
        }
        int movementTick = Math.max(0, active.clock.ticks() - movementStartTick);
        IntroCameraProfile.Pose cameraPose = cameraPose(active.packet, movementTick, duration);
        Vec3 cameraPos = cameraPose.position();
        maintainHeldItemOverrides(minecraft, active);
        maintainAnimationIsolation(minecraft, active);
        syncIntroAnimations(minecraft, active, movementTick, duration);
        updatePreviewActors(active, movementTick, duration, cameraPose);
        faceCinematicPlayers(minecraft, active, cameraPose);
        recordCameraFraming(minecraft, active, cameraPos);
    }

    @SubscribeEvent
    public static void onCameraOverlay(CameraOverlayEvent event) {
        if (!isCinematicActive()) return;
        renderCinematicOverlay(event.graphics(), event.graphics().guiWidth(), event.graphics().guiHeight(), "camera-system");
    }

    public static void renderCinematicOverlay(GuiGraphics graphics, int width, int height) {
        renderCinematicOverlay(graphics, width, height, "gui-mixin");
    }

    private static void renderCinematicOverlay(GuiGraphics graphics, int width, int height, String source) {
        if (active == null) {
            return;
        }
        if (active.skipDuplicateOverlaySource(source)) {
            return;
        }
        IntroCinematicOverlay.Result result = IntroCinematicOverlay.render(graphics, width, height, active.packet, active.clock.ticks());
        active.recordOverlay(result);
    }

    private static void reset(Minecraft minecraft, boolean sendDone) {
        reset(minecraft, sendDone, false);
    }

    private static void reset(Minecraft minecraft, boolean sendDone, boolean keepCameraEntity) {
        RunningIntro previous = active;
        active = null;
        if (previous != null) {
            stopIntroSound(minecraft, previous, "reset");
            LOGGER.info("[BlockOffensive Halftime] Client motion summary sequence={} phase={} side={} sendDone={} tick={} groundSamples={} groundFallback={} groundFallbacks={} unsafeSamples={} maxGroundDelta={} motionProfileSynced={} cameraProfile={} lookAtCameraSamples={} animationSyncSamples={} lastTargetTick={} lastCurrentTick={} maxDrift={} rebuilds={} catchupTicks={} cameraFramingSamples={} minCameraPlayerDistance={} maxCameraPlayerDistance={} externalAnimationBlocks={} externalLayerSuppressions={} introSoundPlayed={} introSoundId={} introSoundPlayCount={} introSoundSkippedPrearm={} introSoundStoppedAtEnd={} introSoundStoppedOnReset={} introSoundStopCount={} introSoundStartTick={} introSoundEndTick={} introSoundLastStopReason={} overlaySamples={} overlayKey={} overlayDuplicateDraws={}",
                    previous.packet.sequenceId, previous.packet.phase, previous.packet.side.id(), sendDone, previous.clock.ticks(),
                    previous.groundSamples, previous.groundFallbacks > 0, previous.groundFallbacks, previous.unsafeSamples,
                    String.format(java.util.Locale.ROOT, "%.4f", previous.maxGroundDelta),
                    previous.animationSyncSamples > 0, IntroCameraProfile.PROFILE_ID, previous.lookAtCameraSamples,
                    previous.animationSyncSamples, previous.lastTargetTick, previous.lastCurrentTick,
                    previous.maxAnimationDrift, previous.animationRebuilds, previous.animationCatchupTicks,
                    previous.cameraFramingSamples,
                    String.format(java.util.Locale.ROOT, "%.3f", previous.minCameraPlayerDistance()),
                    String.format(java.util.Locale.ROOT, "%.3f", previous.maxCameraPlayerDistance),
                    previous.externalAnimationBlocks, previous.externalLayerSuppressions,
                    previous.introSoundPlayed, previous.introSoundId, previous.introSoundPlayCount,
                    previous.introSoundSkippedPrearm, previous.introSoundStoppedAtEnd, previous.introSoundStoppedOnReset,
                    previous.introSoundStopCount, previous.introSoundStartTick, previous.introSoundEndTick,
                    previous.introSoundLastStopReason, previous.overlaySamples, previous.overlayKey,
                    previous.overlayDuplicateDraws);
        }
        if (previous != null && previous.level == minecraft.level && minecraft.level != null) {
            for (UUID id : previous.animatedPlayers) {
                Player player = minecraft.level.getPlayerByUUID(id);
                if (player != null) {
                    IntroPlayerAnimatorBridge.stop(player);
                }
            }
            restoreHeldItemOverrides(minecraft, previous);
            clearPreviewActors(minecraft, previous);
        }
        if (!keepCameraEntity && previous != null && previous.cameraSession != null) {
            previous.cameraSession.close();
        }
        if (sendDone && previous != null) {
            BlockOffensive.INSTANCE.sendToServer(new IntroClientDoneC2SPacket(previous.packet.sequenceId, "client-timeout"));
        }
    }

    private static boolean prepareCamera(Minecraft minecraft, IntroSequenceS2CPacket packet) {
        return active != null && active.cameraSession != null && active.cameraSession.isActive()
                && CameraDirector.prepareFrame(0);
    }

    private static CameraFrame sampleCamera(RunningIntro intro, double time) {
        IntroSequenceS2CPacket packet = intro.packet;
        double movementTick = packet.phase == IntroPhase.PREARM ? 0 : Math.max(0, time - packet.movementStartTick());
        int before = (int) Math.floor(movementTick);
        float fraction = (float) (movementTick - before);
        int duration = Math.max(1, packet.durationTicks);
        IntroCameraProfile.Pose from = cameraPose(packet, before, duration);
        IntroCameraProfile.Pose to = cameraPose(packet, before + 1, duration);
        return CameraFrame.independent(new CameraPose(from.position(), from.yaw(), from.pitch())
                .blend(new CameraPose(to.position(), to.yaw(), to.pitch()), fraction));
    }

    private static void updateIntroSound(Minecraft minecraft, RunningIntro intro, int movementStartTick, int soundEndTick) {
        if (!supportsIntroSound(intro.packet.phase)) {
            if (intro.packet.phase == IntroPhase.PREARM) {
                intro.introSoundSkippedPrearm = true;
            }
            return;
        }
        intro.introSoundEndTick = soundEndTick;
        if (intro.clock.ticks() < movementStartTick) {
            return;
        }
        if (intro.clock.ticks() >= soundEndTick) {
            stopIntroSound(minecraft, intro, "duration-end");
            return;
        }
        if (intro.introSoundPlayed) {
            return;
        }
        ResourceLocation soundId = IntroCinematicAudio.idForSide(intro.packet.side);
        intro.introSoundId = soundId.toString();
        intro.introSoundInstance = IntroCinematicAudio.play(minecraft, intro.packet.side);
        intro.introSoundPlayed = true;
        intro.introSoundPlayCount++;
        intro.introSoundStartTick = intro.clock.ticks();
        LOGGER.info("[BlockOffensive Halftime] Client intro sound played sequence={} phase={} side={} introSoundId={} introSoundStartTick={} introSoundEndTick={} durationTicks={}",
                intro.packet.sequenceId, intro.packet.phase, intro.packet.side.id(), intro.introSoundId,
                intro.introSoundStartTick, intro.introSoundEndTick, intro.packet.durationTicks);
    }

    private static boolean supportsIntroSound(IntroPhase phase) {
        return phase == IntroPhase.START || phase == IntroPhase.SWITCH || phase == IntroPhase.PREVIEW || phase == IntroPhase.PREVIEW5;
    }

    private static void stopIntroSound(Minecraft minecraft, RunningIntro intro, String reason) {
        if (intro == null) {
            return;
        }
        if ("reset".equals(reason) && intro.introSoundPlayed) {
            intro.introSoundStoppedOnReset = true;
            if (intro.introSoundInstance == null) {
                intro.introSoundLastStopReason = intro.introSoundLastStopReason.isEmpty()
                        ? "reset-clear"
                        : intro.introSoundLastStopReason + "+reset-clear";
                return;
            }
        }
        if (intro.introSoundInstance == null) {
            return;
        }
        IntroCinematicAudio.stop(minecraft, intro.introSoundInstance);
        intro.introSoundInstance = null;
        intro.introSoundStopCount++;
        intro.introSoundLastStopReason = reason;
        if ("duration-end".equals(reason)) {
            intro.introSoundStoppedAtEnd = true;
        }
        if ("reset".equals(reason)) {
            intro.introSoundStoppedOnReset = true;
        }
    }

    private static void createPreviewActors(Minecraft minecraft, RunningIntro intro) {
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.getConnection() == null) {
            return;
        }
        ClientPacketListener connection = minecraft.getConnection();
        Map<UUID, PlayerInfo> playerInfoMap = ((ClientPacketListenerAccessor) connection).mcs2$getPlayerInfoMap();
        Set<PlayerInfo> listedPlayers = ((ClientPacketListenerAccessor) connection).mcs2$getListedPlayers();
        for (int i = 0; i < intro.packet.players.size(); i++) {
            IntroSequence.PlayerPath path = intro.packet.players.get(i);
            ItemStack heldItem = previewActorItem(intro.packet, i);
            GameProfile profile = new GameProfile(path.playerId(), "BOHalftime" + (i + 1));
            PlayerInfo info = new PlayerInfo(profile, false);
            PlayerInfoInvoker invoker = (PlayerInfoInvoker) info;
            invoker.mcs2$setGameMode(GameType.SURVIVAL);
            invoker.mcs2$setLatency(0);
            playerInfoMap.put(path.playerId(), info);
            listedPlayers.add(info);

            RemotePlayer player = new RemotePlayer(level, profile);
            level.addPlayer(PREVIEW_ENTITY_ID_BASE - i, player);
            player.noPhysics = true;
            player.setNoGravity(true);
            player.setSilent(true);
            player.getInventory().selected = 0;
            player.getInventory().setItem(0, heldItem.copy());
            player.setItemSlot(EquipmentSlot.MAINHAND, heldItem.copy());
            IntroCameraProfile.Pose cameraPose = cameraPose(intro.packet, 0, Math.max(1, intro.packet.durationTicks));
            Vec3 rawStart = IntroMotionProfile.rootPosition(path, intro.packet.phase, 0, intro.packet.durationTicks);
            Vec3 groundedStart = groundedPreviewPosition(intro, level, rawStart);
            applyPreviewPose(player, groundedStart,
                    IntroCameraProfile.playerYaw(groundedStart, cameraPose.position()),
                    IntroCameraProfile.playerPitch(groundedStart, cameraPose.position()));
            intro.recordLookAtCamera();
            intro.previewActors.add(player);
            if (IntroPlayerAnimatorBridge.play(player, intro.packet.phase, i)) {
                intro.animatedPlayers.add(path.playerId());
            }
        }
        LOGGER.info("[BlockOffensive Halftime] Client spawned {} preview5 actors item={}", intro.previewActors.size(), intro.packet.previewItemId);
    }

    private static void updatePreviewActors(RunningIntro intro, int movementTick, int durationTicks, IntroCameraProfile.Pose cameraPose) {
        if (intro.packet.phase != IntroPhase.PREVIEW5 || intro.previewActors.isEmpty()) {
            return;
        }
        for (int i = 0; i < intro.previewActors.size() && i < intro.packet.players.size(); i++) {
            RemotePlayer player = intro.previewActors.get(i);
            IntroSequence.PlayerPath path = intro.packet.players.get(i);
            Vec3 rawPos = IntroMotionProfile.rootPosition(path, intro.packet.phase, movementTick, durationTicks);
            Vec3 pos = groundedPreviewPosition(intro, player.level(), rawPos);
            applyPreviewPose(player, pos,
                    IntroCameraProfile.playerYaw(pos, cameraPose.position()),
                    IntroCameraProfile.playerPitch(pos, cameraPose.position()));
            intro.recordLookAtCamera();
        }
    }

    private static void faceCinematicPlayers(Minecraft minecraft, RunningIntro intro, IntroCameraProfile.Pose cameraPose) {
        if (minecraft.level == null || intro.packet.phase == IntroPhase.PREARM || intro.packet.phase == IntroPhase.PREVIEW5) {
            return;
        }
        for (IntroSequence.PlayerPath path : intro.packet.players) {
            Player player = minecraft.level.getPlayerByUUID(path.playerId());
            if (player == null) {
                continue;
            }
            applyLook(player,
                    IntroCameraProfile.playerYaw(player.position(), cameraPose.position()),
                    IntroCameraProfile.playerPitch(player.position(), cameraPose.position()));
            intro.recordLookAtCamera();
        }
    }

    private static void recordCameraFraming(Minecraft minecraft, RunningIntro intro, Vec3 cameraPos) {
        if (minecraft.level == null || intro.packet.phase == IntroPhase.PREARM) {
            return;
        }
        if (intro.packet.phase == IntroPhase.PREVIEW5) {
            for (RemotePlayer player : intro.previewActors) {
                intro.recordCameraDistance(cameraPos.distanceTo(player.position().add(0.0, 0.95, 0.0)));
            }
            return;
        }
        for (IntroSequence.PlayerPath path : intro.packet.players) {
            Player player = minecraft.level.getPlayerByUUID(path.playerId());
            if (player != null) {
                intro.recordCameraDistance(cameraPos.distanceTo(player.position().add(0.0, 0.95, 0.0)));
            }
        }
    }

    private static Vec3 groundedPreviewPosition(RunningIntro intro, Level level, Vec3 rawPos) {
        IntroGroundResolver.Result result = IntroGroundResolver.resolve(level, rawPos);
        intro.recordGroundSample(result);
        return result.position();
    }

    private static void syncIntroAnimations(Minecraft minecraft, RunningIntro intro, int movementTick, int durationTicks) {
        if (minecraft.level == null) {
            return;
        }
        for (int i = 0; i < intro.packet.players.size(); i++) {
            IntroSequence.PlayerPath path = intro.packet.players.get(i);
            Player player;
            if (intro.packet.phase == IntroPhase.PREVIEW5) {
                player = i < intro.previewActors.size() ? intro.previewActors.get(i) : null;
            } else {
                player = minecraft.level.getPlayerByUUID(path.playerId());
            }
            if (player == null) {
                continue;
            }
            intro.recordAnimationSync(IntroPlayerAnimatorBridge.sync(player, intro.packet.phase, i, movementTick, durationTicks));
        }
    }

    private static void clearPreviewActors(Minecraft minecraft, RunningIntro intro) {
        if (intro.previewActors.isEmpty() || minecraft.level == null || minecraft.getConnection() == null) {
            return;
        }
        ClientPacketListener connection = minecraft.getConnection();
        Map<UUID, PlayerInfo> playerInfoMap = ((ClientPacketListenerAccessor) connection).mcs2$getPlayerInfoMap();
        Set<PlayerInfo> listedPlayers = ((ClientPacketListenerAccessor) connection).mcs2$getListedPlayers();
        for (RemotePlayer player : intro.previewActors) {
            IntroPlayerAnimatorBridge.stop(player);
            PlayerInfo removed = playerInfoMap.remove(player.getUUID());
            if (removed != null) {
                listedPlayers.remove(removed);
            }
            minecraft.level.removeEntity(player.getId(), Entity.RemovalReason.DISCARDED);
        }
        intro.previewActors.clear();
    }

    private static void applyHeldItemOverrides(Minecraft minecraft, RunningIntro intro) {
        if (minecraft.level == null || intro.packet.phase == IntroPhase.PREARM || intro.packet.phase == IntroPhase.PREVIEW5) {
            return;
        }
        for (int i = 0; i < intro.packet.players.size(); i++) {
            if (i >= intro.packet.heldItems.size()) {
                break;
            }
            IntroSequence.PlayerPath path = intro.packet.players.get(i);
            ItemStack visualStack = intro.packet.heldItems.get(i);
            if (visualStack == null || visualStack.isEmpty()) {
                continue;
            }
            Player player = minecraft.level.getPlayerByUUID(path.playerId());
            if (player == null || intro.heldItemOverrides.containsKey(path.playerId())) {
                continue;
            }
            ItemStack previous = player.getMainHandItem().copy();
            ItemStack applied = visualStack.copy();
            // This is a client-only cinematic snapshot. The server remains authoritative for inventory/economy;
            // reset uses an owner guard so later server sync is not overwritten by stale intro visuals.
            player.setItemSlot(EquipmentSlot.MAINHAND, applied.copy());
            intro.heldItemOverrides.put(path.playerId(), new HeldItemOverride(previous, applied));
        }
    }

    private static void maintainHeldItemOverrides(Minecraft minecraft, RunningIntro intro) {
        if (minecraft.level == null || intro.heldItemOverrides.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, HeldItemOverride> entry : intro.heldItemOverrides.entrySet()) {
            Player player = minecraft.level.getPlayerByUUID(entry.getKey());
            if (player == null) {
                continue;
            }
            HeldItemOverride override = entry.getValue();
            ItemStack current = player.getMainHandItem();
            if (current.isEmpty() || ItemStack.matches(current, override.applied())) {
                player.setItemSlot(EquipmentSlot.MAINHAND, override.applied().copy());
            }
        }
    }

    private static void maintainAnimationIsolation(Minecraft minecraft, RunningIntro intro) {
        if (minecraft.level == null) {
            return;
        }
        for (UUID id : intro.animatedPlayers) {
            Player player = minecraft.level.getPlayerByUUID(id);
            if (player != null) {
                int suppressed = IntroPlayerAnimatorBridge.maintainCinematicIsolation(player);
                if (suppressed > 0) {
                    intro.externalLayerSuppressions += suppressed;
                    recordExternalAnimationBlocked("playerAnimatorLayers:" + suppressed);
                }
            }
        }
        for (RemotePlayer player : intro.previewActors) {
            int suppressed = IntroPlayerAnimatorBridge.maintainCinematicIsolation(player);
            if (suppressed > 0) {
                intro.externalLayerSuppressions += suppressed;
                recordExternalAnimationBlocked("playerAnimatorLayers:" + suppressed);
            }
        }
    }

    private static void restoreHeldItemOverrides(Minecraft minecraft, RunningIntro intro) {
        if (minecraft.level == null || intro.heldItemOverrides.isEmpty()) {
            intro.heldItemOverrides.clear();
            return;
        }
        for (Map.Entry<UUID, HeldItemOverride> entry : intro.heldItemOverrides.entrySet()) {
            Player player = minecraft.level.getPlayerByUUID(entry.getKey());
            if (player == null) {
                continue;
            }
            HeldItemOverride override = entry.getValue();
            if (ItemStack.matches(player.getMainHandItem(), override.applied())) {
                player.setItemSlot(EquipmentSlot.MAINHAND, override.previous().copy());
            }
        }
        intro.heldItemOverrides.clear();
    }

    private static void applyPreviewPose(Player player, Vec3 position, float yaw, float pitch) {
        player.setOldPosAndRot();
        player.moveTo(position.x, position.y, position.z, yaw, pitch);
        player.setDeltaMovement(Vec3.ZERO);
        applyLook(player, yaw, pitch);
    }

    private static void applyLook(Player player, float yaw, float pitch) {
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.yBodyRot = yaw;
        player.yBodyRotO = yaw;
        player.setYHeadRot(yaw);
        player.yHeadRotO = yaw;
        player.xRotO = pitch;
    }

    private static IntroCameraProfile.Pose cameraPose(IntroSequenceS2CPacket packet, int movementTick, int durationTicks) {
        return IntroCameraProfile.pose(packet.players, packet.phase, movementTick, durationTicks, packet.cameraStart, packet.cameraEnd);
    }

    private static ItemStack previewActorItem(IntroSequenceS2CPacket packet, int index) {
        ItemStack packetItem = index < packet.heldItems.size() ? packet.heldItems.get(index) : ItemStack.EMPTY;
        if (packetItem != null && !packetItem.isEmpty()) {
            return packetItem.copy();
        }
        return IntroDisplayWeaponResolver.itemStack(packet.side, packet.previewItemId, index);
    }

    private static int countNonEmpty(List<ItemStack> items) {
        int count = 0;
        for (ItemStack item : items) {
            if (item != null && !item.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private static int previewActorMainHandCount(RunningIntro intro) {
        int count = 0;
        for (RemotePlayer player : intro.previewActors) {
            if (!player.getMainHandItem().isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private static final class RunningIntro {
        final IntroSequenceS2CPacket packet;
        final ClientLevel level = Minecraft.getInstance().level;
        final Set<UUID> animatedPlayers = new HashSet<>();
        final ArrayList<RemotePlayer> previewActors = new ArrayList<>();
        final Map<UUID, HeldItemOverride> heldItemOverrides = new HashMap<>();
        int externalAnimationBlocks;
        int externalLayerSuppressions;
        boolean externalAnimationBlockLogged;
        SoundInstance introSoundInstance;
        boolean introSoundPlayed;
        boolean introSoundSkippedPrearm;
        boolean introSoundStoppedAtEnd;
        boolean introSoundStoppedOnReset;
        int introSoundPlayCount;
        int introSoundStopCount;
        int introSoundStartTick = -1;
        int introSoundEndTick = -1;
        String introSoundId = "none";
        String introSoundLastStopReason = "none";
        int overlaySamples;
        int overlayDuplicateDraws;
        int lastOverlayDrawTick = -1;
        String lastOverlayDrawSource = "none";
        String overlayKey = "none";
        final SequenceClock clock = new SequenceClock();
        CameraSession cameraSession;
        int groundSamples;
        int groundFallbacks;
        int unsafeSamples;
        double maxGroundDelta;
        int animationSyncSamples;
        int lastTargetTick;
        int lastCurrentTick;
        int maxAnimationDrift;
        int animationRebuilds;
        int animationCatchupTicks;
        int lookAtCameraSamples;
        int cameraFramingSamples;
        double minCameraPlayerDistance = Double.POSITIVE_INFINITY;
        double maxCameraPlayerDistance;

        RunningIntro(IntroSequenceS2CPacket packet, int tick) {
            this.packet = packet;
            if (tick > 0) clock.seek(tick);
        }

        void recordGroundSample(IntroGroundResolver.Result result) {
            groundSamples++;
            if (result.fallback()) {
                groundFallbacks++;
            }
            if (result.unsafe()) {
                unsafeSamples++;
            }
            maxGroundDelta = Math.max(maxGroundDelta, Math.abs(result.deltaY()));
        }

        void recordAnimationSync(IntroPlayerAnimatorBridge.SyncResult result) {
            if (result == null || !result.available()) {
                return;
            }
            animationSyncSamples++;
            lastTargetTick = result.targetTick();
            lastCurrentTick = result.currentTick();
            maxAnimationDrift = Math.max(maxAnimationDrift, Math.abs(result.drift()));
            animationRebuilds += result.rebuilds();
            animationCatchupTicks += result.catchupTicks();
        }

        void recordLookAtCamera() {
            lookAtCameraSamples++;
        }

        void recordCameraDistance(double distance) {
            if (!Double.isFinite(distance)) {
                return;
            }
            cameraFramingSamples++;
            minCameraPlayerDistance = Math.min(minCameraPlayerDistance, distance);
            maxCameraPlayerDistance = Math.max(maxCameraPlayerDistance, distance);
        }

        boolean skipDuplicateOverlaySource(String source) {
            if (lastOverlayDrawTick == clock.ticks() && !lastOverlayDrawSource.equals(source)) {
                overlayDuplicateDraws++;
                return true;
            }
            lastOverlayDrawTick = clock.ticks();
            lastOverlayDrawSource = source;
            return false;
        }

        void recordOverlay(IntroCinematicOverlay.Result result) {
            if (result == null) {
                return;
            }
            overlayKey = result.overlayKey();
            if (result.titleDrawn()) {
                overlaySamples++;
            }
        }

        double minCameraPlayerDistance() {
            return cameraFramingSamples == 0 ? 0.0 : minCameraPlayerDistance;
        }
    }

    private record HeldItemOverride(ItemStack previous, ItemStack applied) {
    }
}
