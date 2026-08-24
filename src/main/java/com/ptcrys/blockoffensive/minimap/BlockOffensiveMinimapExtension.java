package com.ptcrys.blockoffensive.minimap;

import com.ptcrys.fpsmatch.core.minimap.extension.MinimapExtensionRegistry;
import com.ptcrys.fpsmatch.core.minimap.extension.MarkerPresentation;
import com.ptcrys.fpsmatch.core.minimap.extension.MinimapGameplayExtension;
import com.ptcrys.fpsmatch.core.minimap.marker.MinimapMarkerProvider;
import com.ptcrys.fpsmatch.core.minimap.region.MinimapRegionProvider;
import com.ptcrys.fpsmatch.core.minimap.region.RegionPresentation;
import com.ptcrys.fpsmatch.core.minimap.marker.MinimapVisibilityPolicy;
import com.ptcrys.fpsmatch.core.minimap.model.MapKey;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * BlockOffensive gameplay extension for CS / CSDM minimap providers.
 * Providers are map-bound at runtime; this extension declares CS/CSDM support without loading client classes.
 */
public final class BlockOffensiveMinimapExtension implements MinimapGameplayExtension {
    public static final String ID = "blockoffensive:cs_csdm";
    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);

    private BlockOffensiveMinimapExtension() {
    }

    public static void register() {
        if (REGISTERED.compareAndSet(false, true)) {
            MinimapExtensionRegistry.register(new BlockOffensiveMinimapExtension());
        }
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean supports(MapKey mapKey) {
        return BlockOffensiveMinimapRuntime.supports(mapKey);
    }

    @Override
    public Optional<com.ptcrys.fpsmatch.core.minimap.marker.MinimapViewerContext> viewerContext(
            MapKey mapKey,
            java.util.UUID actorId
    ) {
        return supports(mapKey)
                ? BlockOffensiveMinimapRuntime.viewerContext(mapKey, actorId)
                : Optional.empty();
    }

    @Override
    public List<MinimapMarkerProvider> markerProviders(MapKey mapKey) {
        return supports(mapKey)
                ? BlockOffensiveMinimapRuntime.markerProviders(mapKey)
                : List.of();
    }

    @Override
    public List<MarkerPresentation> markerPresentations(MapKey mapKey) {
        return supports(mapKey) ? CSMinimapAssetCatalog.markerPresentations() : List.of();
    }

    @Override
    public Optional<MinimapVisibilityPolicy> visibilityPolicy(MapKey mapKey) {
        return supports(mapKey)
                ? BlockOffensiveMinimapRuntime.visibilityPolicy(mapKey)
                : Optional.empty();
    }

    @Override
    public List<MinimapRegionProvider> regionProviders(MapKey mapKey) {
        return supports(mapKey)
                ? BlockOffensiveMinimapRuntime.regionProviders(mapKey)
                : List.of();
    }

    @Override
    public List<RegionPresentation> regionPresentations(MapKey mapKey) {
        return supports(mapKey)
                ? CSMinimapAssetCatalog.regionPresentations()
                : List.of();
    }
}
