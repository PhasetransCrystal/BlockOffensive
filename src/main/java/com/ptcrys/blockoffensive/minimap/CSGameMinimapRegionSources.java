package com.ptcrys.blockoffensive.minimap;

import com.ptcrys.fpsmatch.common.capability.map.DemolitionModeCapability;
import com.ptcrys.fpsmatch.common.capability.team.ShopCapability;
import com.ptcrys.fpsmatch.common.capability.team.SpawnPointCapability;
import com.ptcrys.fpsmatch.core.map.BaseMap;
import com.ptcrys.fpsmatch.core.minimap.region.WorldAxisAlignedBounds;
import com.ptcrys.fpsmatch.core.team.ServerTeam;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Builds injected CS region sources from live map capabilities without exposing AreaData.
 */
public final class CSGameMinimapRegionSources {
    private CSGameMinimapRegionSources() {
    }

    public static CSGameMinimapRegionProvider fromMap(BaseMap map) {
        Objects.requireNonNull(map, "map");
        return new CSGameMinimapRegionProvider(
                map::minimapMapBoundary,
                () -> map.getCapabilityMap().get(DemolitionModeCapability.class)
                        .map(DemolitionModeCapability::getBombSites)
                        .orElse(List.of()),
                () -> teamRegions(map, true),
                () -> teamRegions(map, false)
        );
    }

    private static List<CSGameMinimapRegionProvider.TeamRegionSource> teamRegions(BaseMap map, boolean spawn) {
        List<CSGameMinimapRegionProvider.TeamRegionSource> out = new ArrayList<>();
        for (ServerTeam team : map.getMapTeams().getNormalTeams()) {
            String teamId = team.getFixedName();
            Optional<WorldAxisAlignedBounds> bounds = spawn
                    ? team.getCapabilityMap().get(SpawnPointCapability.class)
                    .flatMap(SpawnPointCapability::minimapSpawnEnvelope)
                    : team.getCapabilityMap().get(ShopCapability.class)
                    .flatMap(ShopCapability::minimapShopBounds);
            bounds.ifPresent(b -> out.add(new CSGameMinimapRegionProvider.TeamRegionSource(teamId, b)));
        }
        return List.copyOf(out);
    }
}