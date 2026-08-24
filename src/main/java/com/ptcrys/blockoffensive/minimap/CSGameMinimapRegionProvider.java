package com.ptcrys.blockoffensive.minimap;

import com.ptcrys.fpsmatch.core.minimap.model.MapKey;
import com.ptcrys.fpsmatch.core.minimap.region.BombSiteDefinition;
import com.ptcrys.fpsmatch.core.minimap.region.MinimapRegionProvider;
import com.ptcrys.fpsmatch.core.minimap.region.RuntimeRegionDescriptor;
import com.ptcrys.fpsmatch.core.minimap.region.WorldAxisAlignedBounds;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * CS demolition-mode region provider. Pure over injected sources; does not expose AreaData.
 * Avoids Codec-backed types so unit tests can run without Mojang DFU on the BO test classpath.
 */
public final class CSGameMinimapRegionProvider implements MinimapRegionProvider {
    public static final String SEMANTIC_MAP_BOUNDARY = "fpsmatch:region/map_boundary";
    public static final String SEMANTIC_BOMB_SITE = "fpsmatch:region/bomb_site";
    public static final String SEMANTIC_SPAWN = "fpsmatch:region/spawn";
    public static final String SEMANTIC_SHOP = "fpsmatch:region/shop";

    private final Supplier<Optional<WorldAxisAlignedBounds>> mapBoundary;
    private final Supplier<List<BombSiteDefinition>> bombSites;
    private final Supplier<List<TeamRegionSource>> spawnRegions;
    private final Supplier<List<TeamRegionSource>> shopRegions;

    public CSGameMinimapRegionProvider(
            Supplier<Optional<WorldAxisAlignedBounds>> mapBoundary,
            Supplier<List<BombSiteDefinition>> bombSites,
            Supplier<List<TeamRegionSource>> spawnRegions,
            Supplier<List<TeamRegionSource>> shopRegions
    ) {
        this.mapBoundary = Objects.requireNonNull(mapBoundary, "mapBoundary");
        this.bombSites = Objects.requireNonNull(bombSites, "bombSites");
        this.spawnRegions = Objects.requireNonNull(spawnRegions, "spawnRegions");
        this.shopRegions = Objects.requireNonNull(shopRegions, "shopRegions");
    }

    @Override
    public List<RuntimeRegionDescriptor> collect(MapKey mapKey, String defaultFloorId) {
        Objects.requireNonNull(mapKey, "mapKey");
        Objects.requireNonNull(defaultFloorId, "defaultFloorId");
        boolean demolition = "cs".equals(mapKey.gameType());
        if (!demolition && !"csdm".equals(mapKey.gameType())) {
            return List.of();
        }
        List<RuntimeRegionDescriptor> out = new ArrayList<>();
        mapBoundary.get().ifPresent(bounds -> out.add(new RuntimeRegionDescriptor(
                "map_boundary",
                defaultFloorId,
                "Map",
                SEMANTIC_MAP_BOUNDARY,
                List.of(),
                Optional.of("fpsmatch:map/boundary"),
                bounds,
                10
        )));
        if (demolition) {
            for (BombSiteDefinition site : bombSites.get()) {
                out.add(new RuntimeRegionDescriptor(
                        site.id(),
                        defaultFloorId,
                        site.displayName().orElse(site.id()),
                        SEMANTIC_BOMB_SITE,
                        List.of("fpsmatch:tag/objective"),
                        Optional.of("fpsmatch:gameplay/" + site.id()),
                        site.bounds(),
                        100
                ));
            }
        }
        for (TeamRegionSource spawn : spawnRegions.get()) {
            out.add(new RuntimeRegionDescriptor(
                    "spawn_" + spawn.teamId(),
                    defaultFloorId,
                    "Spawn " + spawn.teamId(),
                    SEMANTIC_SPAWN,
                    List.of("fpsmatch:tag/spawn"),
                    Optional.of("fpsmatch:spawn/team/" + spawn.teamId()),
                    spawn.bounds(),
                    50
            ));
        }
        for (TeamRegionSource shop : shopRegions.get()) {
            out.add(new RuntimeRegionDescriptor(
                    "shop_" + shop.teamId(),
                    defaultFloorId,
                    "Shop " + shop.teamId(),
                    SEMANTIC_SHOP,
                    List.of("fpsmatch:tag/shop"),
                    Optional.of("fpsmatch:shop/team/" + shop.teamId()),
                    shop.bounds(),
                    40
            ));
        }
        return List.copyOf(out);
    }

    public record TeamRegionSource(String teamId, WorldAxisAlignedBounds bounds) {
        public TeamRegionSource {
            Objects.requireNonNull(teamId, "teamId");
            Objects.requireNonNull(bounds, "bounds");
            if (teamId.isBlank()) {
                throw new IllegalArgumentException("teamId cannot be blank");
            }
        }
    }
}
