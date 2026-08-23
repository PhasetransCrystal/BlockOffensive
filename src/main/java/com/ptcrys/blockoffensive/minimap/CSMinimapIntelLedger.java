package com.ptcrys.blockoffensive.minimap;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Team-shared enemy intel ledger. Keyed by viewer team id, then target player id.
 */
public final class CSMinimapIntelLedger {
    private final Map<String, Map<UUID, EnemyIntelState>> byTeam = new LinkedHashMap<>();
    private final CSMinimapVisibilityConfig config;

    public CSMinimapIntelLedger(CSMinimapVisibilityConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public void noteLos(String viewerTeamId, UUID targetId, long nowTick, double x, double y, double z, float yaw, Optional<String> floorSlug) {
        state(viewerTeamId, targetId).noteLosOrFire(nowTick, config.losRevealTicks(), x, y, z, yaw, floorSlug);
    }

    public void noteFire(String viewerTeamId, UUID targetId, long nowTick, double x, double y, double z, float yaw, Optional<String> floorSlug) {
        state(viewerTeamId, targetId).noteLosOrFire(nowTick, config.fireExposureTicks(), x, y, z, yaw, floorSlug);
    }

    public void removeTarget(UUID targetId) {
        for (Map<UUID, EnemyIntelState> teamMap : byTeam.values()) {
            teamMap.remove(targetId);
        }
    }

    public void clearTeam(String teamId) {
        byTeam.remove(teamId);
    }

    public void clearAll() {
        byTeam.clear();
    }

    public void tick(long nowTick) {
        for (Map<UUID, EnemyIntelState> teamMap : byTeam.values()) {
            teamMap.entrySet().removeIf(entry -> !entry.getValue().tick(nowTick, config.lastKnownTicks()));
        }
    }

    public List<EnemyIntelState> intelForTeam(String teamId) {
        Map<UUID, EnemyIntelState> teamMap = byTeam.get(teamId);
        if (teamMap == null || teamMap.isEmpty()) {
            return List.of();
        }
        return List.copyOf(new ArrayList<>(teamMap.values()));
    }

    public Optional<EnemyIntelState> get(String teamId, UUID targetId) {
        Map<UUID, EnemyIntelState> teamMap = byTeam.get(teamId);
        if (teamMap == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(teamMap.get(targetId));
    }

    private EnemyIntelState state(String teamId, UUID targetId) {
        Objects.requireNonNull(teamId, "teamId");
        Objects.requireNonNull(targetId, "targetId");
        return byTeam.computeIfAbsent(teamId, ignored -> new LinkedHashMap<>())
                .computeIfAbsent(targetId, EnemyIntelState::new);
    }
}