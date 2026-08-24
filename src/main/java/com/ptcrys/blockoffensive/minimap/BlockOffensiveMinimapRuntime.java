package com.ptcrys.blockoffensive.minimap;

import com.ptcrys.blockoffensive.map.CSDeathMatchMap;
import com.ptcrys.blockoffensive.map.CSGameMap;
import com.ptcrys.fpsmatch.config.FPSMConfig;
import com.ptcrys.fpsmatch.core.FPSMCore;
import com.ptcrys.fpsmatch.core.data.PlayerData;
import com.ptcrys.fpsmatch.core.map.BaseMap;
import com.ptcrys.fpsmatch.core.minimap.marker.DeathMarkerLedger;
import com.ptcrys.fpsmatch.core.minimap.marker.MinimapMarkerProvider;
import com.ptcrys.fpsmatch.core.minimap.marker.MinimapViewerContext;
import com.ptcrys.fpsmatch.core.minimap.marker.PlayerPoseSnapshot;
import com.ptcrys.fpsmatch.core.minimap.model.MapKey;
import com.ptcrys.fpsmatch.core.minimap.region.MinimapRegionProvider;
import com.ptcrys.fpsmatch.core.team.ServerTeam;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BO's one map-scoped minimap composition root. FPSMatch owns the stream and
 * wire lifecycle; this class owns only CS/CSDM gameplay state and adapters.
 */
public final class BlockOffensiveMinimapRuntime {
    private static final Map<MapKey, MapScope> SCOPES = new ConcurrentHashMap<>();

    private BlockOffensiveMinimapRuntime() {
    }

    public static boolean supports(MapKey mapKey) {
        Objects.requireNonNull(mapKey, "mapKey");
        return "cs".equals(mapKey.gameType()) || "csdm".equals(mapKey.gameType());
    }

    public static Optional<MinimapViewerContext> viewerContext(MapKey key, UUID actorId) {
        Objects.requireNonNull(actorId, "actorId");
        return scope(key).flatMap(scope -> scope.viewer(actorId));
    }

    public static List<MinimapMarkerProvider> markerProviders(MapKey key) {
        return scope(key).map(MapScope::markerProviders).orElseGet(List::of);
    }

    public static Optional<com.ptcrys.fpsmatch.core.minimap.marker.MinimapVisibilityPolicy> visibilityPolicy(
            MapKey key
    ) {
        return scope(key).map(MapScope::visibilityPolicy);
    }

    public static List<MinimapRegionProvider> regionProviders(MapKey key) {
        return scope(key).map(MapScope::regionProviders).orElseGet(List::of);
    }

    public static void reset(BaseMap map) {
        if (map == null) {
            return;
        }
        MapKey key = keyOf(map);
        SCOPES.computeIfPresent(key, (ignored, scope) -> {
            if (scope.map() == map) {
                scope.clear();
                return null;
            }
            return scope;
        });
    }

    public static void clear() {
        SCOPES.values().forEach(MapScope::clear);
        SCOPES.clear();
    }

    public static void tick() {
        if (!FPSMCore.initialized()) {
            return;
        }
        for (Map.Entry<MapKey, MapScope> entry : List.copyOf(SCOPES.entrySet())) {
            Optional<BaseMap> current = resolve(entry.getKey());
            if (current.isEmpty() || current.get() != entry.getValue().map()) {
                if (SCOPES.remove(entry.getKey(), entry.getValue())) {
                    entry.getValue().clear();
                }
                continue;
            }
            entry.getValue().tick();
        }
    }

    public static void noteFire(ServerPlayer shooter) {
        if (shooter == null || !FPSMCore.initialized()) {
            return;
        }
        FPSMCore.getInstance().getMapByPlayer(shooter)
                .flatMap(map -> scope(new MapKey(map.getGameType(), map.getMapName())))
                .ifPresent(scope -> scope.noteFire(shooter));
    }

    public static void targetDied(BaseMap map, UUID targetId) {
        if (map == null || targetId == null) {
            return;
        }
        scope(keyOf(map)).ifPresent(scope -> scope.targetDied(targetId));
    }

    public static void playerLeft(BaseMap map, UUID playerId) {
        if (map == null || playerId == null) {
            return;
        }
        scope(keyOf(map)).ifPresent(scope -> scope.playerLeft(playerId));
    }

    private static Optional<MapScope> scope(MapKey key) {
        Objects.requireNonNull(key, "key");
        if (!supports(key)) {
            return Optional.empty();
        }
        Optional<BaseMap> map = resolve(key);
        if (map.isEmpty() || !(map.get() instanceof CSGameMap || map.get() instanceof CSDeathMatchMap)) {
            return Optional.empty();
        }
        MapScope result = SCOPES.compute(key, (ignored, previous) ->
                previous == null || previous.map() != map.get()
                        ? new MapScope(map.get())
                        : previous
        );
        return Optional.of(result);
    }

    private static Optional<BaseMap> resolve(MapKey key) {
        if (!FPSMCore.initialized()) {
            return Optional.empty();
        }
        return FPSMCore.getInstance().getMapByTypeWithName(key.gameType(), key.mapName());
    }

    private static MapKey keyOf(BaseMap map) {
        return new MapKey(map.getGameType(), map.getMapName());
    }

    private static boolean observerOmniscient() {
        if (FPSMConfig.Server.minimapObserverOmniscient == null) {
            return CSMinimapVisibilityConfig.DEFAULTS.observerOmniscientDefault();
        }
        return FPSMConfig.Server.minimapObserverOmniscient.get();
    }

    private static final class MapScope {
        private final BaseMap map;
        private final CSMinimapIntelLedger intelLedger =
                new CSMinimapIntelLedger(CSMinimapVisibilityConfig.DEFAULTS);
        private final CSMinimapVisibilityEventBridge visibilityEvents =
                new CSMinimapVisibilityEventBridge(intelLedger);
        private final CSMinimapVisibilityPolicy visibilityPolicy =
                new CSMinimapVisibilityPolicy(intelLedger);
        private final DeathMarkerLedger deathLedger;
        private final CSMapMinimapMarkerProvider playerProvider;
        private final List<MinimapMarkerProvider> markerProviders;
        private final List<MinimapRegionProvider> regionProviders;
        private final CSGameObjectiveTracker objectiveTracker;
        private final String tTeamId;
        private final String ctTeamId;

        private MapScope(BaseMap map) {
            this.map = Objects.requireNonNull(map, "map");
            this.deathLedger = deathLedger(map);
            this.playerProvider = new CSMapMinimapMarkerProvider(
                    () -> livingPlayers(map),
                    deathLedger,
                    () -> map.getServerLevel().getGameTime()
            );
            List<MinimapMarkerProvider> providers = new ArrayList<>();
            providers.add(playerProvider);
            if (map instanceof CSGameMap gameMap) {
                this.objectiveTracker = gameMap.objectiveTracker();
                this.tTeamId = gameMap.getT().getFixedName();
                this.ctTeamId = gameMap.getCT().getFixedName();
                providers.add(new CSGameObjectiveMarkerProvider(
                        objectiveTracker::snapshot,
                        () -> map.getServerLevel().getGameTime(),
                        () -> objectiveTracker.snapshot().carrierId().flatMap(carrier ->
                                CSGameObjectiveMarkerProvider.activeIntelFlag(
                                        intelLedger, ctTeamId, carrier,
                                        map.getServerLevel().getGameTime()
                                )),
                        tTeamId,
                        ctTeamId
                ));
            } else {
                this.objectiveTracker = null;
                this.tTeamId = "";
                this.ctTeamId = "";
            }
            this.markerProviders = List.copyOf(providers);
            this.regionProviders = List.of(CSGameMinimapRegionSources.fromMap(map));
        }

        private BaseMap map() {
            return map;
        }

        private Optional<MinimapViewerContext> viewer(UUID actorId) {
            Optional<ServerTeam> team = map.getMapTeams().getTeamByPlayer(actorId);
            if (team.isEmpty()) {
                return Optional.empty();
            }
            boolean spectator = !team.get().isNormal();
            boolean living = team.get().getPlayerData(actorId)
                    .map(PlayerData::isLivingOnServer)
                    .orElse(false);
            boolean tdm = map instanceof CSDeathMatchMap deathMatch && deathMatch.isTDM();
            boolean shared = !(map instanceof CSDeathMatchMap) || tdm;
            return Optional.of(CSMinimapIdentityResolver.resolve(
                    actorId,
                    team.get().getFixedName(),
                    living,
                    spectator,
                    shared,
                    spectator && observerOmniscient()
            ));
        }

        private List<MinimapMarkerProvider> markerProviders() {
            return markerProviders;
        }

        private CSMinimapVisibilityPolicy visibilityPolicy() {
            return visibilityPolicy;
        }

        private List<MinimapRegionProvider> regionProviders() {
            return regionProviders;
        }

        private void tick() {
            long now = map.getServerLevel().getGameTime();
            visibilityEvents.tick(now);
            refreshObjectivePose(now);
            refreshLineOfSight(now);
            if (objectiveTracker != null) {
                objectiveTracker.tick(now);
            }
        }

        private void noteFire(ServerPlayer shooter) {
            Optional<ServerTeam> sourceTeam = map.getMapTeams().getTeamByPlayer(shooter.getUUID());
            if (sourceTeam.isEmpty()) {
                return;
            }
            if (map instanceof CSDeathMatchMap deathMatch && !deathMatch.isTDM()) {
                return;
            }
            long now = map.getServerLevel().getGameTime();
            for (ServerTeam team : map.getMapTeams().getNormalTeams()) {
                if (team.equals(sourceTeam.get())) {
                    continue;
                }
                visibilityEvents.onFireExposure(
                        team.getFixedName(), shooter.getUUID(), now,
                        shooter.getX(), shooter.getY(), shooter.getZ(), shooter.getYRot(),
                        Optional.empty()
                );
            }
        }

        private void refreshLineOfSight(long now) {
            List<ServerPlayer> players = onlineLivingPlayers();
            for (ServerPlayer viewer : players) {
                Optional<ServerTeam> viewerTeam = map.getMapTeams().getTeamByPlayer(viewer.getUUID());
                if (viewerTeam.isEmpty()) {
                    continue;
                }
                for (ServerPlayer target : players) {
                    Optional<ServerTeam> targetTeam = map.getMapTeams().getTeamByPlayer(target.getUUID());
                    if (targetTeam.isEmpty() || targetTeam.get().equals(viewerTeam.get())) {
                        continue;
                    }
                    if (viewer.hasLineOfSight(target)) {
                        visibilityEvents.onLineOfSight(
                                viewerTeam.get().getFixedName(), target.getUUID(), now,
                                target.getX(), target.getY(), target.getZ(), target.getYRot(),
                                Optional.empty()
                        );
                    }
                }
            }
        }

        private void refreshObjectivePose(long now) {
            if (objectiveTracker == null) {
                return;
            }
            C4ObjectiveSnapshot snapshot = objectiveTracker.snapshot();
            switch (snapshot.phase()) {
                case CARRIED -> snapshot.carrierId().flatMap(map::getPlayerByUUID)
                        .ifPresent(player -> objectiveTracker.noteCarrierPose(
                                player.getUUID(), now, player.getX(), player.getY(), player.getZ(),
                                player.getYRot(), Optional.empty()
                        ));
                case DROPPED -> snapshot.entityId().ifPresent(id -> {
                    Entity entity = map.getServerLevel().getEntity(id);
                    if (entity != null && !entity.isRemoved()) {
                            noteDroppedPose(entity, now);
                            if (hasCtLineOfSight(entity)) {
                                objectiveTracker.noteDroppedDiscoveredByCt(
                                        now, entity.getX(), entity.getY(), entity.getZ(),
                                        entity.getYRot(), Optional.empty()
                                );
                            }
                    }
                });
                case PLANTED, DEFUSING -> snapshot.entityId().ifPresent(id -> {
                    Entity entity = map.getServerLevel().getEntity(id);
                    if (entity != null && !entity.isRemoved()) {
                        objectiveTracker.notePlantedPose(
                                id, now, entity.getX(), entity.getY(), entity.getZ(),
                                entity.getYRot(), Optional.empty()
                        );
                    }
                });
                default -> {
                }
            }
        }

        private void noteDroppedPose(Entity entity, long now) {
            OptionalInt id = objectiveTracker.stableEntityId();
            if (id.isPresent()) {
                objectiveTracker.noteDroppedPose(
                        id.getAsInt(), now, entity.getX(), entity.getY(), entity.getZ(),
                        entity.getYRot(), Optional.empty()
                );
            }
        }

        private boolean hasCtLineOfSight(Entity entity) {
            if (!(map instanceof CSGameMap gameMap)) {
                return false;
            }
            return gameMap.getCT().getLivingPlayers().stream()
                    .map(uuid -> map.getPlayerByUUID(uuid).orElse(null))
                    .filter(Objects::nonNull)
                    .anyMatch(player -> player.hasLineOfSight(entity));
        }

        private void targetDied(UUID targetId) {
            visibilityEvents.onTargetDeathOrLeave(targetId);
        }

        private void playerLeft(UUID playerId) {
            visibilityEvents.onTargetDeathOrLeave(playerId);
            deathLedger.clearPlayer(playerId);
        }

        private void clear() {
            visibilityEvents.onRoundOrMapReset();
            deathLedger.clearAll();
            if (objectiveTracker != null) {
                objectiveTracker.roundReset();
            }
        }

        private List<ServerPlayer> onlineLivingPlayers() {
            return livingPlayers(map).stream()
                    .map(pose -> map.getPlayerByUUID(pose.playerId()).orElse(null))
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(player -> player.getUUID().toString()))
                    .toList();
        }

        private static DeathMarkerLedger deathLedger(BaseMap map) {
            if (map instanceof CSGameMap gameMap) {
                return gameMap.deathMarkerLedger();
            }
            if (map instanceof CSDeathMatchMap deathMatch) {
                return deathMatch.deathMarkerLedger();
            }
            throw new IllegalArgumentException("unsupported BO minimap map: " + map.getGameType());
        }

        private static List<PlayerPoseSnapshot> livingPlayers(BaseMap map) {
            List<PlayerPoseSnapshot> out = new ArrayList<>();
            long tick = map.getServerLevel().getGameTime();
            for (ServerTeam team : map.getMapTeams().getNormalTeams()) {
                String teamId = team.getFixedName();
                for (PlayerData data : team.getPlayersData()) {
                    Optional<ServerPlayer> player = data.getPlayer();
                    if (player.isEmpty() || !data.isLivingOnServer()
                            || !player.get().isAlive() || player.get().isSpectator()) {
                        continue;
                    }
                    ServerPlayer pose = player.get();
                    out.add(new PlayerPoseSnapshot(
                            pose.getUUID(), teamId,
                            pose.getX(), pose.getY(), pose.getZ(), pose.getYRot(),
                            tick, Optional.empty(), true
                    ));
                }
            }
            out.sort(Comparator.comparing(pose -> pose.playerId().toString()));
            return List.copyOf(out);
        }
    }
}
