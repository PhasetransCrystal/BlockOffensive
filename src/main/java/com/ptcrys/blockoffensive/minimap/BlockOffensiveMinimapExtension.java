package com.ptcrys.blockoffensive.minimap;

import com.ptcrys.fpsmatch.core.minimap.extension.MinimapExtensionRegistry;
import com.ptcrys.fpsmatch.core.minimap.extension.MinimapGameplayExtension;
import com.ptcrys.fpsmatch.core.minimap.marker.MinimapMarkerProvider;
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
        String gameType = mapKey.gameType();
        return "cs".equals(gameType) || "csdm".equals(gameType);
    }

    @Override
    public List<MinimapMarkerProvider> markerProviders(MapKey mapKey) {
        // Map-scoped providers attach via map mount/runtime; extension remains common-safe.
        return List.of();
    }

    @Override
    public Optional<MinimapVisibilityPolicy> visibilityPolicy(MapKey mapKey) {
        return Optional.empty();
    }
}