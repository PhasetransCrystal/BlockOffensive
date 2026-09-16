package com.ptcrys.blockoffensive.intro;

import com.mojang.logging.LogUtils;
import com.ptcrys.blockoffensive.map.CSMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import com.ptcrys.blockoffensive.BlockOffensive;
import com.ptcrys.blockoffensive.intro.net.IntroClientDoneC2SPacket;
import com.ptcrys.blockoffensive.intro.net.IntroSequenceS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;

@Mod.EventBusSubscriber(modid = BlockOffensive.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class IntroRuntimeController {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int READY_GRACE_TICKS = 10;
    private static final Map<String, RunningSequence> ACTIVE = new HashMap<>();
    private static final Map<UUID, String> PLAYER_TO_SEQUENCE = new HashMap<>();
    private static final Set<CSMap> PENDING_SWITCH_MAPS = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<CSMap> START_REENTRY = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<CSMap> START_DELAY_PENDING = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final List<DelayedMapAction> DELAYED_ACTIONS = new ArrayList<>();
    private static boolean configLoaded;

    private IntroRuntimeController() {
    }

    public static void ensureConfigLoaded(MinecraftServer server) {
        if (!configLoaded) {
            IntroConfigStore.load(server);
            configLoaded = true;
        }
    }

    public static void onServerStopping(MinecraftServer server) {
        new ArrayList<>(ACTIVE.values()).forEach(seq -> finish(seq, true));
        ACTIVE.clear();
        PLAYER_TO_SEQUENCE.clear();
        PENDING_SWITCH_MAPS.clear();
        START_REENTRY.clear();
        START_DELAY_PENDING.clear();
        DELAYED_ACTIONS.clear();
        configLoaded = false;
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        onServerStopping(event.getServer());
    }

    public static void triggerStart(CSMap map) {
        trigger(map, IntroPhase.START);
    }

    public static boolean shouldDelayStart(CSMap map) {
        if (START_REENTRY.remove(map)) {
            START_DELAY_PENDING.remove(map);
            return false;
        }
        if (START_DELAY_PENDING.contains(map)) {
            return true;
        }
        ServerLevel level = ((com.ptcrys.fpsmatch.core.map.BaseMap) map).getServerLevel();
        ensureConfigLoaded(level.getServer());
        boolean hasSequence = IntroSequencePlanner.plan(map, IntroPhase.START, IntroTeamSide.CT).isPresent()
                || IntroSequencePlanner.plan(map, IntroPhase.START, IntroTeamSide.T).isPresent();
        if (!hasSequence) {
            return false;
        }
        START_DELAY_PENDING.add(map);
        prearm(map, IntroPhase.START);
        DELAYED_ACTIONS.add(new DelayedMapAction(map, IntroSequence.DEFAULT_PRE_ROLL_TICKS, () -> {
            START_REENTRY.add(map);
            return map.start();
        }, "start"));
        LOGGER.info("[BlockOffensive Halftime] Delaying BO start for {} ticks so clients enter cinematic pre-roll before FPSM/BO spawn teleport", IntroSequence.DEFAULT_PRE_ROLL_TICKS);
        return true;
    }

    public static void markSwitchPending(CSMap map) {
        PENDING_SWITCH_MAPS.add(map);
        prearmSwitchIfConfigured(map);
    }

    public static void clearSwitchPending(CSMap map) {
        PENDING_SWITCH_MAPS.remove(map);
    }

    public static void triggerPendingSwitch(CSMap map) {
        if (PENDING_SWITCH_MAPS.remove(map)) {
            trigger(map, IntroPhase.SWITCH);
        }
    }

    public static void prearmSwitchIfConfigured(CSMap map) {
        ServerLevel level = ((com.ptcrys.fpsmatch.core.map.BaseMap) map).getServerLevel();
        ensureConfigLoaded(level.getServer());
        if (IntroSequencePlanner.plan(map, IntroPhase.SWITCH, IntroTeamSide.CT).isPresent()
                || IntroSequencePlanner.plan(map, IntroPhase.SWITCH, IntroTeamSide.T).isPresent()) {
            prearm(map, IntroPhase.SWITCH);
        }
    }

    public static boolean triggerPreview(CSMap map, IntroTeamSide side) {
        ensureConfigLoaded(((com.ptcrys.fpsmatch.core.map.BaseMap) map).getServerLevel().getServer());
        Optional<IntroSequence> sequence = IntroSequencePlanner.plan(map, IntroPhase.SWITCH, side);
        if (sequence.isEmpty()) {
            return false;
        }
        startSequence(map, sequence.get());
        return true;
    }

    public static boolean triggerPreview5(CSMap map, IntroTeamSide side, ServerPlayer viewer, String itemId) {
        ensureConfigLoaded(((com.ptcrys.fpsmatch.core.map.BaseMap) map).getServerLevel().getServer());
        Optional<IntroSequence> sequence = IntroSequencePlanner.plan(map, IntroPhase.PREVIEW5, side, itemId);
        if (sequence.isEmpty()) {
            return false;
        }
        IntroSequence preview = sequence.get();
        UUID sequenceId = UUID.randomUUID();
        IntroSequence prearm = toPrearmSequence(preview);
        BlockOffensive.INSTANCE.send(PacketDistributor.PLAYER.with(() -> viewer), new IntroSequenceS2CPacket(sequenceId, prearm).startingAt(viewer.serverLevel().getGameTime()));
        UUID viewerId = viewer.getUUID();
        MinecraftServer server = viewer.getServer();
        DELAYED_ACTIONS.add(new DelayedMapAction(map, preview.preRollTicks(), () -> {
            ServerPlayer currentViewer = server == null ? null : server.getPlayerList().getPlayer(viewerId);
            if (currentViewer == null || currentViewer.connection == null) {
                LOGGER.info("[BlockOffensive Halftime] Skipped preview5 {}:{} {} because viewer disconnected before cinematic start", preview.gameType(), preview.mapName(), preview.side().id());
                return false;
            }
            IntroDisplayWeaponResolver.Result displayWeapons = IntroDisplayWeaponResolver.items(preview);
            BlockOffensive.INSTANCE.send(PacketDistributor.PLAYER.with(() -> currentViewer), new IntroSequenceS2CPacket(sequenceId, preview, displayWeapons.items()).startingAt(currentViewer.serverLevel().getGameTime()));
            LOGGER.info("[BlockOffensive Halftime] Started preview5 {}:{} {} for {} with {} actors item={} introDisplayWeaponRule={} displayWeaponIds={} visualItems={} displayWeaponFallbacks={} after preRollTicks={}",
                    preview.gameType(), preview.mapName(), preview.side().id(), currentViewer.getGameProfile().getName(),
                    preview.players().size(), preview.safePreviewItemId(), IntroDisplayWeaponResolver.RULE_ID, displayWeapons.itemIds(),
                    displayWeapons.nonEmptyCount(), displayWeapons.fallbackCount(), preview.preRollTicks());
            return true;
        }, "preview5"));
        LOGGER.info("[BlockOffensive Halftime] Prearmed preview5 {}:{} {} for {} with {} actors item={} preRollTicks={}",
                preview.gameType(), preview.mapName(), preview.side().id(), viewer.getGameProfile().getName(),
                preview.players().size(), preview.safePreviewItemId(), preview.preRollTicks());
        return true;
    }

    private static void trigger(CSMap map, IntroPhase phase) {
        ServerLevel level = ((com.ptcrys.fpsmatch.core.map.BaseMap) map).getServerLevel();
        ensureConfigLoaded(level.getServer());
        IntroSequencePlanner.plan(map, phase, IntroTeamSide.CT).ifPresent(sequence -> startSequence(map, sequence));
        IntroSequencePlanner.plan(map, phase, IntroTeamSide.T).ifPresent(sequence -> startSequence(map, sequence));
    }

    private static void startSequence(CSMap map, IntroSequence sequence) {
        ServerLevel level = ((com.ptcrys.fpsmatch.core.map.BaseMap) map).getServerLevel();
        String key = key(sequence);
        RunningSequence old = ACTIVE.remove(key);
        if (old != null) {
            finish(old, true);
        }

        ArrayList<PlayerState> players = new ArrayList<>();
        for (IntroSequence.PlayerPath path : sequence.players()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(path.playerId());
            if (player == null || !player.level().dimension().equals(level.dimension())) {
                continue;
            }
            releaseFromExistingSequence(player.getUUID());
            players.add(PlayerState.capture(player, path));
        }
        if (players.isEmpty()) {
            return;
        }

        RunningSequence running = new RunningSequence(UUID.randomUUID(), sequence, level, players);
        IntroDisplayWeaponResolver.Result displayWeapons = IntroDisplayWeaponResolver.items(sequence);
        running.visualHeldItems.addAll(displayWeapons.items());
        running.displayWeaponFallbacks = displayWeapons.fallbackCount();
        running.displayWeaponIds.addAll(displayWeapons.itemIds());
        ACTIVE.put(key, running);
        players.forEach(state -> PLAYER_TO_SEQUENCE.put(state.playerId, key));
        players.forEach(IntroRuntimeController::holdDuringPreRoll);
        broadcast(running);
        LOGGER.info("[BlockOffensive Halftime] Started {} intro for {}:{} {} with {} players introDisplayWeaponRule={} displayWeaponIds={} visualItems={} displayWeaponFallbacks={} preRollTicks={} cinematicReadyAtTick={} movementStartTick={}",
                sequence.phase(), sequence.gameType(), sequence.mapName(), sequence.side().id(), players.size(),
                IntroDisplayWeaponResolver.RULE_ID, running.displayWeaponIds, countNonEmpty(running.visualHeldItems), running.displayWeaponFallbacks,
                sequence.preRollTicks(), sequence.cinematicReadyAtTick(), sequence.movementStartTick());
    }

    private static void broadcast(RunningSequence running) {
        IntroSequenceS2CPacket packet = new IntroSequenceS2CPacket(running.id, running.sequence, running.visualHeldItems).startingAt(running.startGameTime);
        for (PlayerState state : running.players) {
            BlockOffensive.INSTANCE.send(PacketDistributor.PLAYER.with(() -> state.player), packet);
        }
    }

    public static void onClientDone(ServerPlayer sender, IntroClientDoneC2SPacket packet) {
        if (sender != null) {
            LOGGER.debug("[BlockOffensive Halftime] Client {} reported intro {}: {}", sender.getGameProfile().getName(), packet.sequenceId(), packet.reason());
            if ("camera-ready".equals(packet.reason())) {
                ACTIVE.values().stream()
                        .filter(running -> running.id.equals(packet.sequenceId()))
                        .findFirst()
                        .ifPresent(running -> {
                            boolean participant = running.players.stream().anyMatch(state -> state.playerId.equals(sender.getUUID()));
                            if (participant) {
                                running.readyPlayers.add(sender.getUUID());
                                LOGGER.info("[BlockOffensive Halftime] Client camera-ready intro {} ready={}/{} player={}", running.id, readyCount(running), running.players.size(), sender.getGameProfile().getName());
                            }
                        });
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side != LogicalSide.SERVER) {
            return;
        }
        if (!ACTIVE.isEmpty()) {
            Iterator<RunningSequence> iterator = ACTIVE.values().iterator();
            while (iterator.hasNext()) {
                RunningSequence running = iterator.next();
                boolean expired = running.tick >= running.sequence.totalClientTicks() + 10;
                if (expired || running.players.isEmpty()) {
                    finish(running, false);
                    iterator.remove();
                    continue;
                }
                tickSequence(running);
                running.tick++;
            }
        }

        Iterator<DelayedMapAction> delayed = DELAYED_ACTIONS.iterator();
        while (delayed.hasNext()) {
            DelayedMapAction action = delayed.next();
            if (--action.ticksLeft > 0) {
                continue;
            }
            delayed.remove();
            boolean result = action.run.getAsBoolean();
            LOGGER.info("[BlockOffensive Halftime] Delayed BO {} resumed result={}", action.reason, result);
        }
    }

    private static void prearm(CSMap map, IntroPhase targetPhase) {
        com.ptcrys.fpsmatch.core.map.BaseMap baseMap = (com.ptcrys.fpsmatch.core.map.BaseMap) map;
        int totalPlayers = 0;
        totalPlayers += prearmSide(map, targetPhase, IntroTeamSide.CT);
        totalPlayers += prearmSide(map, targetPhase, IntroTeamSide.T);
        if (totalPlayers > 0) {
            LOGGER.info("[BlockOffensive Halftime] Sent PREARM cinematic cover for {}:{} targetPhase={} players={}",
                    baseMap.getGameType(), baseMap.getMapName(), targetPhase, totalPlayers);
        }
    }

    private static int prearmSide(CSMap map, IntroPhase targetPhase, IntroTeamSide side) {
        Optional<IntroSequence> sequenceOpt = IntroSequencePlanner.plan(map, targetPhase, side);
        if (sequenceOpt.isEmpty()) {
            return 0;
        }
        IntroSequence sequence = sequenceOpt.get();
        ServerLevel level = ((com.ptcrys.fpsmatch.core.map.BaseMap) map).getServerLevel();
        ArrayList<ServerPlayer> players = new ArrayList<>();
        for (IntroSequence.PlayerPath path : sequence.players()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(path.playerId());
            if (player != null && player.level().dimension().equals(level.dimension())) {
                players.add(player);
            }
        }
        if (players.isEmpty()) {
            return 0;
        }
        IntroSequence prearm = toPrearmSequence(sequence);
        IntroSequenceS2CPacket packet = new IntroSequenceS2CPacket(UUID.randomUUID(), prearm).startingAt(level.getGameTime());
        for (ServerPlayer player : players) {
            BlockOffensive.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet);
        }
        return players.size();
    }

    private static IntroSequence toPrearmSequence(IntroSequence sequence) {
        // The prearm packet carries the final cinematic camera. Clients attach the ghost camera while still black,
        // so BO/FPSM spawn teleports or preview actor creation cannot leak a first-person/HUD frame.
        return new IntroSequence(
                sequence.gameType(),
                sequence.mapName(),
                IntroPhase.PREARM,
                sequence.side(),
                sequence.preRollTicks(),
                sequence.preRollTicks(),
                0,
                sequence.previewItemId(),
                sequence.cameraStart(),
                sequence.cameraStart(),
                sequence.cameraStartYaw(),
                sequence.cameraStartPitch(),
                sequence.cameraStartYaw(),
                sequence.cameraStartPitch(),
                sequence.players()
        );
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

    private static void tickSequence(RunningSequence running) {
        int preRoll = Math.max(0, running.sequence.preRollTicks());
        int movementStartTick = running.sequence.movementStartTick();
        if (!running.placedAtStart && running.tick < movementStartTick) {
            Iterator<PlayerState> iterator = running.players.iterator();
            while (iterator.hasNext()) {
                PlayerState state = iterator.next();
                if (!isPlayerStillInSequence(state, running)) {
                    restoreState(running, state, false, true);
                    iterator.remove();
                    continue;
                }
                holdDuringPreRoll(state);
            }
            return;
        }

        if (!running.placedAtStart && !allPlayersReady(running) && running.tick < movementStartTick + READY_GRACE_TICKS) {
            for (PlayerState state : running.players) {
                holdDuringPreRoll(state);
            }
            return;
        }

        if (!running.placedAtStart) {
            Iterator<PlayerState> iterator = running.players.iterator();
            while (iterator.hasNext()) {
                PlayerState state = iterator.next();
                if (!running.readyPlayers.contains(state.playerId)) {
                    LOGGER.warn("[BlockOffensive Halftime] Client {} did not report camera-ready for intro {}; keeping player out of cinematic movement", state.player.getGameProfile().getName(), running.id);
                    restoreState(running, state, false, true);
                    iterator.remove();
                    continue;
                }
                placeAtStart(running, state);
            }
            if (running.players.isEmpty()) {
                return;
            }
            running.placedAtStart = true;
            LOGGER.info("[BlockOffensive Halftime] Sequence {} moved players to intro start after cinematic ready ready={}/{} tick={} preRollTicks={} cinematicReadyAtTick={} movementStartTick={}",
                    running.id, readyCount(running), running.players.size(), running.tick,
                    preRoll, running.sequence.cinematicReadyAtTick(), movementStartTick);
        }

        int movementTick = Math.max(0, running.tick - movementStartTick);
        IntroCameraProfile.Pose cameraPose = IntroCameraProfile.pose(
                running.sequence.players(),
                running.sequence.phase(),
                movementTick,
                running.sequence.durationTicks(),
                running.sequence.cameraStart(),
                running.sequence.cameraEnd());
        Iterator<PlayerState> iterator = running.players.iterator();
        while (iterator.hasNext()) {
            PlayerState state = iterator.next();
            ServerPlayer player = state.player;
            if (!isPlayerStillInSequence(state, running)) {
                restoreState(running, state, false, true);
                iterator.remove();
                continue;
            }
            Vec3 rawPos = IntroMotionProfile.rootPosition(state.path, running.sequence.phase(), movementTick, running.sequence.durationTicks());
            Vec3 pos = groundedPosition(running, rawPos);
            float yaw = IntroCameraProfile.playerYaw(pos, cameraPose.position());
            float pitch = IntroCameraProfile.playerPitch(pos, cameraPose.position());
            player.setDeltaMovement(Vec3.ZERO);
            player.teleportTo(running.level, pos.x, pos.y, pos.z, yaw, pitch);
            player.setYHeadRot(yaw);
            player.setYBodyRot(yaw);
            running.recordLookAtCamera();
            player.setInvulnerable(true);
            player.getAbilities().flying = false;
            player.getAbilities().mayfly = false;
            player.onUpdateAbilities();
        }
    }

    private static void finish(RunningSequence running, boolean sendStop) {
        logMotionSummary(running, sendStop ? "stop" : "complete");
        for (PlayerState state : running.players) {
            restoreState(running, state, running.placedAtStart, sendStop);
        }
    }

    private static void restoreState(RunningSequence running, PlayerState state, boolean teleportToEnd, boolean sendStop) {
        PLAYER_TO_SEQUENCE.remove(state.playerId);
        if (state.player.connection == null) {
            return;
        }
        state.player.setInvulnerable(state.wasInvulnerable);
        state.player.setDeltaMovement(Vec3.ZERO);
        state.player.setGameMode(state.gameType);
        state.restoreAbilities();
        if (teleportToEnd && state.player.level().dimension().equals(running.level.dimension())) {
            state.player.teleportTo(running.level, state.path.finalSpawn().x, state.path.finalSpawn().y, state.path.finalSpawn().z, state.path.yaw(), state.path.pitch());
            state.player.setYHeadRot(state.path.yaw());
            state.player.setYBodyRot(state.path.yaw());
        }
        state.player.onUpdateAbilities();
        if (sendStop) {
            IntroSequence stop = new IntroSequence(
                    running.sequence.gameType(),
                    running.sequence.mapName(),
                    IntroPhase.STOP,
                    running.sequence.side(),
                    1,
                    0,
                    0,
                    "",
                    running.sequence.cameraEnd(),
                    running.sequence.cameraEnd(),
                    running.sequence.cameraEndYaw(),
                    running.sequence.cameraEndPitch(),
                    running.sequence.cameraEndYaw(),
                    running.sequence.cameraEndPitch(),
                    List.of()
            );
            BlockOffensive.INSTANCE.send(PacketDistributor.PLAYER.with(() -> state.player), new IntroSequenceS2CPacket(running.id, stop));
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        String key = PLAYER_TO_SEQUENCE.remove(player.getUUID());
        if (key != null) {
            RunningSequence running = ACTIVE.get(key);
            if (running != null) {
                running.players.removeIf(state -> {
                    if (!state.playerId.equals(player.getUUID())) {
                        return false;
                    }
                    restoreState(running, state, false, false);
                    return true;
                });
                if (running.players.isEmpty()) {
                    ACTIVE.remove(key);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            forceRelease(player);
        }
    }

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        if (isLocked(event.getEntity()) || isLocked(event.getSource().getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (isLocked(event.getEntity()) || isLocked(event.getSource().getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (isLocked(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && PLAYER_TO_SEQUENCE.containsKey(player.getUUID())) {
            event.setCanceled(true);
        }
    }

    public static boolean isLocked(ServerPlayer player) {
        return player != null && PLAYER_TO_SEQUENCE.containsKey(player.getUUID());
    }

    private static boolean isLocked(net.minecraft.world.entity.Entity entity) {
        return entity instanceof ServerPlayer player && isLocked(player);
    }

    private static void forceRelease(ServerPlayer player) {
        String key = PLAYER_TO_SEQUENCE.remove(player.getUUID());
        RunningSequence running = key == null ? null : ACTIVE.get(key);
        if (running == null) {
            return;
        }
        running.players.removeIf(state -> {
            if (!state.playerId.equals(player.getUUID())) {
                return false;
            }
            restoreState(running, state, false, true);
            return true;
        });
        if (running.players.isEmpty()) {
            ACTIVE.remove(key);
        }
    }

    private static void releaseFromExistingSequence(UUID playerId) {
        String oldKey = PLAYER_TO_SEQUENCE.get(playerId);
        if (oldKey == null) {
            return;
        }
        RunningSequence running = ACTIVE.get(oldKey);
        if (running == null) {
            PLAYER_TO_SEQUENCE.remove(playerId);
            return;
        }
        running.players.removeIf(state -> {
            if (!state.playerId.equals(playerId)) {
                return false;
            }
            restoreState(running, state, false, true);
            return true;
        });
        if (running.players.isEmpty()) {
            ACTIVE.remove(oldKey);
        }
    }

    private static boolean isPlayerStillInSequence(PlayerState state, RunningSequence running) {
        return state.player.isAlive()
                && state.player.connection != null
                && state.player.level().dimension().equals(running.level.dimension());
    }

    private static boolean allPlayersReady(RunningSequence running) {
        for (PlayerState state : running.players) {
            if (!running.readyPlayers.contains(state.playerId)) {
                return false;
            }
        }
        return true;
    }

    private static int readyCount(RunningSequence running) {
        int ready = 0;
        for (PlayerState state : running.players) {
            if (running.readyPlayers.contains(state.playerId)) {
                ready++;
            }
        }
        return ready;
    }

    private static void holdDuringPreRoll(PlayerState state) {
        ServerPlayer player = state.player;
        player.setInvulnerable(true);
        player.setDeltaMovement(Vec3.ZERO);
        player.teleportTo((ServerLevel) player.level(), state.preRollPosition.x, state.preRollPosition.y, state.preRollPosition.z, state.preRollYaw, state.preRollPitch);
        player.setYHeadRot(state.preRollYaw);
        player.setYBodyRot(state.preRollYaw);
        player.getAbilities().flying = false;
        player.getAbilities().mayfly = false;
        player.getAbilities().setWalkingSpeed(0.0f);
        player.onUpdateAbilities();
    }

    private static void placeAtStart(RunningSequence running, PlayerState state) {
        ServerPlayer player = state.player;
        Vec3 pos = groundedPosition(running, IntroMotionProfile.rootPosition(state.path, running.sequence.phase(), 0, running.sequence.durationTicks()));
        IntroCameraProfile.Pose cameraPose = IntroCameraProfile.pose(
                running.sequence.players(),
                running.sequence.phase(),
                0,
                running.sequence.durationTicks(),
                running.sequence.cameraStart(),
                running.sequence.cameraEnd());
        float yaw = IntroCameraProfile.playerYaw(pos, cameraPose.position());
        float pitch = IntroCameraProfile.playerPitch(pos, cameraPose.position());
        player.setInvulnerable(true);
        player.setDeltaMovement(Vec3.ZERO);
        player.teleportTo((ServerLevel) player.level(), pos.x, pos.y, pos.z, yaw, pitch);
        player.setYHeadRot(yaw);
        player.setYBodyRot(yaw);
        running.recordLookAtCamera();
        player.onUpdateAbilities();
    }

    private static Vec3 groundedPosition(RunningSequence running, Vec3 rawPos) {
        IntroGroundResolver.Result result = IntroGroundResolver.resolve(running.level, rawPos);
        running.recordGroundSample(result);
        return result.position();
    }

    private static void logMotionSummary(RunningSequence running, String reason) {
        if (running.motionSummaryLogged) {
            return;
        }
        running.motionSummaryLogged = true;
            LOGGER.info("[BlockOffensive Halftime] Ground motion summary sequence={} reason={} phase={} side={} players={} groundSamples={} groundFallback={} groundFallbacks={} unsafeSamples={} maxGroundDelta={} motionProfileSynced=true cameraProfile={} lookAtCameraSamples={} introDisplayWeaponRule={} displayWeaponIds={} displayWeaponFallbacks={}",
                running.id, reason, running.sequence.phase(), running.sequence.side().id(), running.players.size(),
                running.groundSamples, running.groundFallbacks > 0, running.groundFallbacks, running.unsafeSamples,
                String.format(java.util.Locale.ROOT, "%.4f", running.maxGroundDelta), IntroCameraProfile.PROFILE_ID, running.lookAtCameraSamples,
                IntroDisplayWeaponResolver.RULE_ID, running.displayWeaponIds, running.displayWeaponFallbacks);
    }

    private static String key(IntroSequence sequence) {
        return sequence.gameType() + ":" + sequence.mapName() + ":" + sequence.side().id();
    }

    private static final class RunningSequence {
        final UUID id;
        final IntroSequence sequence;
        final ServerLevel level;
        final long startGameTime;
        final ArrayList<PlayerState> players;
        final ArrayList<ItemStack> visualHeldItems = new ArrayList<>();
        final ArrayList<String> displayWeaponIds = new ArrayList<>();
        final Set<UUID> readyPlayers = new HashSet<>();
        int tick;
        boolean placedAtStart;
        boolean motionSummaryLogged;
        int groundSamples;
        int groundFallbacks;
        int unsafeSamples;
        double maxGroundDelta;
        int lookAtCameraSamples;
        int displayWeaponFallbacks;

        RunningSequence(UUID id, IntroSequence sequence, ServerLevel level, ArrayList<PlayerState> players) {
            this.id = id;
            this.sequence = sequence;
            this.level = level;
            this.startGameTime = level.getGameTime();
            this.players = players;
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

        void recordLookAtCamera() {
            lookAtCameraSamples++;
        }
    }

    private static final class DelayedMapAction {
        final CSMap map;
        int ticksLeft;
        final BooleanSupplier run;
        final String reason;

        DelayedMapAction(CSMap map, int ticksLeft, BooleanSupplier run, String reason) {
            this.map = map;
            this.ticksLeft = Math.max(1, ticksLeft);
            this.run = run;
            this.reason = reason;
        }
    }

    private static final class PlayerState {
        final UUID playerId;
        final ServerPlayer player;
        final IntroSequence.PlayerPath path;
        final GameType gameType;
        final Vec3 preRollPosition;
        final float preRollYaw;
        final float preRollPitch;
        final boolean wasInvulnerable;
        final boolean mayfly;
        final boolean flying;
        final boolean invulnerableAbility;
        final boolean instabuild;
        final boolean mayBuild;
        final float flyingSpeed;
        final float walkingSpeed;

        private PlayerState(ServerPlayer player, IntroSequence.PlayerPath path) {
            this.playerId = player.getUUID();
            this.player = player;
            this.path = path;
            this.gameType = player.gameMode.getGameModeForPlayer();
            this.preRollPosition = player.position();
            this.preRollYaw = player.getYRot();
            this.preRollPitch = player.getXRot();
            this.wasInvulnerable = player.isInvulnerable();
            Abilities abilities = player.getAbilities();
            this.mayfly = abilities.mayfly;
            this.flying = abilities.flying;
            this.invulnerableAbility = abilities.invulnerable;
            this.instabuild = abilities.instabuild;
            this.mayBuild = abilities.mayBuild;
            this.flyingSpeed = abilities.getFlyingSpeed();
            this.walkingSpeed = abilities.getWalkingSpeed();
        }

        static PlayerState capture(ServerPlayer player, IntroSequence.PlayerPath path) {
            return new PlayerState(player, path);
        }

        void restoreAbilities() {
            Abilities abilities = player.getAbilities();
            abilities.mayfly = mayfly;
            abilities.flying = flying;
            abilities.invulnerable = invulnerableAbility;
            abilities.instabuild = instabuild;
            abilities.mayBuild = mayBuild;
            abilities.setFlyingSpeed(flyingSpeed);
            abilities.setWalkingSpeed(walkingSpeed);
        }
    }
}
