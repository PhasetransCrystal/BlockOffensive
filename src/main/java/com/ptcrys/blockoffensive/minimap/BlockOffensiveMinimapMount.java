package com.ptcrys.blockoffensive.minimap;

import com.ptcrys.fpsmatch.common.capability.map.MinimapCapability;
import com.ptcrys.fpsmatch.core.capability.CapabilityMap;
import com.ptcrys.fpsmatch.core.capability.map.MapCapability;
import com.ptcrys.fpsmatch.core.map.BaseMap;

import java.util.List;
import java.util.Objects;

/**
 * Helpers describing which map modes mount minimap capability and how binding activity works.
 */
public final class BlockOffensiveMinimapMount {
    private BlockOffensiveMinimapMount() {
    }

    public static List<Class<? extends MapCapability>> withMinimap(
            List<Class<? extends MapCapability>> base
    ) {
        Objects.requireNonNull(base, "base");
        if (base.contains(MinimapCapability.class)) {
            return List.copyOf(base);
        }
        return java.util.stream.Stream.concat(base.stream(), java.util.stream.Stream.of(MinimapCapability.class))
                .toList();
    }

    public static boolean mountsMinimap(List<Class<? extends MapCapability>> mapCapabilities) {
        return mapCapabilities != null && mapCapabilities.contains(MinimapCapability.class);
    }

    /**
     * Round reset must not clear published static binding when present.
     */
    public static void preserveBindingAcrossRoundReset(MinimapCapability capability) {
        Objects.requireNonNull(capability, "capability");
        // intentional no-op: published binding is independent of round state
    }

    /**
     * Map destroy / unload releases runtime interest; binding may remain for reload if persisted.
     */
    public static void onMapDestroy(
            CapabilityMap<BaseMap, MapCapability> capabilities,
            Runnable releaseRuntimeSubscriptions
    ) {
        Objects.requireNonNull(releaseRuntimeSubscriptions, "releaseRuntimeSubscriptions");
        releaseRuntimeSubscriptions.run();
        // capability instance may be discarded with the map; no static content wipe required
        capabilities.get(MinimapCapability.class).ifPresent(cap -> {
            // keep binding for diagnostics until map object is GC'd; runtime subscriptions are gone
        });
    }

    public static boolean isRuntimeActive(MinimapCapability capability) {
        return capability != null && capability.isPublished();
    }
}