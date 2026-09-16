package com.ptcrys.blockoffensive.intro;

import com.ptcrys.blockoffensive.map.CSMap;
import com.ptcrys.fpsmatch.common.capability.team.SpawnPointCapability;
import com.ptcrys.fpsmatch.core.data.PlayerData;
import com.ptcrys.fpsmatch.core.data.SpawnPointData;
import com.ptcrys.fpsmatch.core.map.BaseMap;
import com.ptcrys.fpsmatch.core.team.ServerTeam;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class IntroSequencePlanner {
    private static final int MAX_PLAYERS_PER_SIDE = 5;
    private static final int PREVIEW5_PRE_ROLL_TICKS = 12;
    private static final double PREVIEW5_WORLD_OFFSET = 58.0;
    private static final int CINEMATIC_READY_TICK = 8;
    private static final UUID PREVIEW5_UUID_BASE = UUID.fromString("00000000-0000-0000-0000-00000000a500");

    private IntroSequencePlanner() {
    }

    public static Optional<IntroSequence> plan(CSMap map, IntroPhase phase, IntroTeamSide side) {
        return plan(map, phase, side, null);
    }

    public static Optional<IntroSequence> plan(CSMap map, IntroPhase phase, IntroTeamSide side, String previewItemId) {
        BaseMap baseMap = (BaseMap) map;
        Level level = baseMap.getServerLevel();
        Optional<IntroConfigStore.SideConfig> configOpt = IntroConfigStore.get(baseMap.getGameType(), baseMap.getMapName(), side);
        if (configOpt.isEmpty()) {
            return Optional.empty();
        }
        IntroConfigStore.SideConfig config = configOpt.get();
        if (!config.isEnabled(phase) || !config.hasArea()) {
            return Optional.empty();
        }

        boolean preview5 = phase == IntroPhase.PREVIEW5;
        ServerTeam team = side == IntroTeamSide.CT ? map.getCT() : map.getT();
        List<PlayerData> ordered = preview5 ? List.of() : team.getPlayersData().stream()
                .filter(PlayerData::isOnline)
                .sorted(Comparator.comparing(data -> data.getOwner().toString()))
                .limit(MAX_PLAYERS_PER_SIDE)
                .toList();
        if (!preview5 && ordered.isEmpty()) {
            return Optional.empty();
        }

        AABB area = config.area.toAabb();
        Vec3 forward = yawToForward(config.yaw);
        Vec3 right = new Vec3(-forward.z, 0.0, forward.x).normalize();
        if (preview5) {
            Vec3 previewOffset = preview5WorldOffset(side, forward, right);
            area = area.move(previewOffset);
        }
        Vec3 center = area.getCenter();
        Vec3 groundedCenter = groundPathPoint(level, center);
        double lateralCapacity = Math.max(2.8, Math.min(area.getXsize(), area.getZsize()) * 0.5 - 0.45);
        double spacing = Math.max(1.18, Math.min(1.78, lateralCapacity / 1.66));
        boolean switchPhase = phase == IntroPhase.SWITCH;
        double entryBack = Math.max(1.55, Math.min(switchPhase ? 3.05 : 3.95, Math.max(area.getXsize(), area.getZsize()) * (switchPhase ? 0.18 : 0.25)));
        double walkForward = Math.max(1.2, Math.min(switchPhase ? 2.2 : 3.08, Math.max(area.getXsize(), area.getZsize()) * (switchPhase ? 0.15 : 0.2)));
        List<SpawnPointData> configuredSpawns = team.getCapabilityMap().get(SpawnPointCapability.class)
                .map(SpawnPointCapability::getSpawnPointsData)
                .stream()
                .flatMap(List::stream)
                .filter(spawn -> isUsableSpawn(spawn, baseMap))
                .toList();

        double sideSign = side == IntroTeamSide.CT ? 1.0 : -1.0;
        double cameraSideSign = switchPhase ? -sideSign : sideSign;
        ArrayList<IntroSequence.PlayerPath> paths = new ArrayList<>();
        int plannedCount = preview5 ? MAX_PLAYERS_PER_SIDE : ordered.size();
        int durationTicks = config.clampedDurationTicks();
        for (int i = 0; i < plannedCount; i++) {
            UUID playerId;
            Vec3 finalSpawn;
            if (preview5) {
                playerId = preview5Uuid(side, i);
                finalSpawn = groundedCenter;
            } else {
                PlayerData data = ordered.get(i);
                Optional<ServerPlayer> playerOpt = data.getPlayer();
                if (playerOpt.isEmpty()) {
                    continue;
                }
                SpawnPointData spawn = resolveSpawn(data, configuredSpawns, i, baseMap);
                if (spawn == null) {
                    continue;
                }
                playerId = playerOpt.get().getUUID();
                finalSpawn = spawn.getPosition();
            }
            FormationSlot slot = formationSlot(i, plannedCount, spacing);
            double startLateral = slot.lateral() + (switchPhase ? slot.switchTurnOffset() * spacing : 0.0);
            double endLateral = slot.lateral() * (switchPhase ? 0.5 : 0.66);
            Vec3 start = groundPathPoint(level, clampToArea(center.add(right.scale(startLateral)).subtract(forward.scale(entryBack + slot.depth())), area));
            Vec3 cinematicEnd = groundPathPoint(level, clampToArea(center.add(right.scale(endLateral)).add(forward.scale(walkForward - slot.depth() * (switchPhase ? 0.2 : 0.34))), area));
            float playerYaw = addYaw(lookYaw(start, cinematicEnd), slot.toeInDegrees() * (switchPhase ? 0.35f : 0.28f));
            float playerPitch = Math.max(-8.0f, Math.min(8.0f, config.pitch));
            paths.add(new IntroSequence.PlayerPath(playerId, start, cinematicEnd, finalSpawn, playerYaw, playerPitch));
        }
        if (paths.isEmpty()) {
            return Optional.empty();
        }
        AABB cameraArea = area.inflate(8.5);
        Vec3 cameraStart = clampToArea(IntroCameraProfile.plannedStart(paths, phase, durationTicks, cameraSideSign), cameraArea);
        Vec3 cameraEnd = clampToArea(IntroCameraProfile.plannedEnd(paths, phase, durationTicks, cameraSideSign), cameraArea);
        IntroCameraProfile.Pose startCamera = IntroCameraProfile.pose(paths, phase, 0, durationTicks, cameraStart, cameraEnd);
        IntroCameraProfile.Pose endCamera = IntroCameraProfile.pose(paths, phase, durationTicks, durationTicks, cameraStart, cameraEnd);
        float cameraYaw = startCamera.yaw();
        float cameraPitch = startCamera.pitch();
        float endYaw = endCamera.yaw();
        float endPitch = endCamera.pitch();

        return Optional.of(new IntroSequence(
                baseMap.getGameType(),
                baseMap.getMapName(),
                phase,
                side,
                durationTicks,
                preview5 ? PREVIEW5_PRE_ROLL_TICKS : IntroSequence.DEFAULT_PRE_ROLL_TICKS,
                CINEMATIC_READY_TICK,
                previewItemId,
                cameraStart,
                cameraEnd,
                cameraYaw,
                cameraPitch,
                endYaw,
                endPitch,
                List.copyOf(paths)
        ));
    }

    private static SpawnPointData resolveSpawn(PlayerData data, List<SpawnPointData> configuredSpawns, int playerIndex, BaseMap baseMap) {
        SpawnPointData assigned = data.getSpawnPointsData();
        if (isUsableSpawn(assigned, baseMap)) {
            return assigned;
        }
        // Preview can run before BO/FPSM has assigned PlayerData spawnpoints for a round.
        return playerIndex < configuredSpawns.size() ? configuredSpawns.get(playerIndex) : null;
    }

    private static boolean isUsableSpawn(SpawnPointData spawn, BaseMap baseMap) {
        return spawn != null && spawn.getDimension().equals(currentDimension(baseMap));
    }

    private static net.minecraft.resources.ResourceKey<Level> currentDimension(BaseMap baseMap) {
        return baseMap.getServerLevel().dimension();
    }

    private static Vec3 yawToForward(float yaw) {
        double radians = Math.toRadians(yaw);
        return new Vec3(-Math.sin(radians), 0.0, Math.cos(radians)).normalize();
    }

    private static UUID preview5Uuid(IntroTeamSide side, int index) {
        long least = PREVIEW5_UUID_BASE.getLeastSignificantBits() + side.ordinal() * 16L + index;
        return new UUID(PREVIEW5_UUID_BASE.getMostSignificantBits(), least);
    }

    private static Vec3 preview5WorldOffset(IntroTeamSide side, Vec3 forward, Vec3 right) {
        double sideOffset = side == IntroTeamSide.CT ? -PREVIEW5_WORLD_OFFSET : PREVIEW5_WORLD_OFFSET;
        // Preview actors are client-only evidence helpers. Keep them well away from the real player/spawn platform
        // so the cinematic camera cannot pull the local body into late fade-out frames.
        return right.scale(sideOffset).add(forward.scale(PREVIEW5_WORLD_OFFSET * 0.85));
    }

    private static FormationSlot formationSlot(int index, int count, double spacing) {
        if (count >= 5) {
            return switch (index) {
                case 0 -> new FormationSlot(0.0, -0.42, 0.0f, 0.0);
                case 1 -> new FormationSlot(-spacing * 0.78, 0.02, 10.0f, -0.16);
                case 2 -> new FormationSlot(spacing * 0.8, 0.1, -10.0f, 0.16);
                case 3 -> new FormationSlot(-spacing * 1.42, 0.62, 17.0f, 0.24);
                default -> new FormationSlot(spacing * 1.46, 0.7, -17.0f, -0.24);
            };
        }
        double lateral = (index - (count - 1) * 0.5) * spacing;
        float toeIn = (float) Mth.clamp(-lateral * 10.0, -22.0, 22.0);
        return new FormationSlot(lateral, Math.abs(lateral) * 0.16, toeIn, lateral < 0.0 ? -0.25 : 0.25);
    }

    private static float addYaw(float yaw, float delta) {
        float value = yaw + delta;
        while (value <= -180.0f) {
            value += 360.0f;
        }
        while (value > 180.0f) {
            value -= 360.0f;
        }
        return value;
    }

    private static Vec3 clampToArea(Vec3 pos, AABB area) {
        return new Vec3(
                clamp(pos.x, area.minX + 0.35, area.maxX - 0.35),
                clamp(pos.y, area.minY + 0.05, area.maxY - 0.05),
                clamp(pos.z, area.minZ + 0.35, area.maxZ - 0.35)
        );
    }

    private static double clamp(double value, double min, double max) {
        if (max < min) {
            return (min + max) * 0.5;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static Vec3 groundPathPoint(Level level, Vec3 planned) {
        return IntroGroundResolver.resolve(level, planned).position();
    }

    private static float lookYaw(Vec3 from, Vec3 to) {
        Vec3 delta = to.subtract(from);
        return (float) (Math.toDegrees(Math.atan2(-delta.x, delta.z)));
    }

    private static float lookPitch(Vec3 from, Vec3 to) {
        Vec3 delta = to.subtract(from);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        return (float) (-Math.toDegrees(Math.atan2(delta.y, horizontal)));
    }

    private record FormationSlot(double lateral, double depth, float toeInDegrees, double switchTurnOffset) {
    }
}
