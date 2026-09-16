package com.ptcrys.blockoffensive.intro;

import java.util.List;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;

public record IntroSequence(
        String gameType,
        String mapName,
        IntroPhase phase,
        IntroTeamSide side,
        int durationTicks,
        int preRollTicks,
        int cinematicReadyAtTick,
        String previewItemId,
        Vec3 cameraStart,
        Vec3 cameraEnd,
        float cameraStartYaw,
        float cameraStartPitch,
        float cameraEndYaw,
        float cameraEndPitch,
        List<PlayerPath> players
) {
    public static final int DEFAULT_PRE_ROLL_TICKS = 20;

    public int movementStartTick() {
        return Math.max(Math.max(0, preRollTicks), Math.max(0, cinematicReadyAtTick));
    }

    public int totalClientTicks() {
        return movementStartTick() + Math.max(1, durationTicks);
    }

    public String safePreviewItemId() {
        return previewItemId == null || previewItemId.isBlank() ? "minecraft:crossbow" : previewItemId;
    }

    public record PlayerPath(UUID playerId, Vec3 start, Vec3 end, Vec3 finalSpawn, float yaw, float pitch) {
    }
}
