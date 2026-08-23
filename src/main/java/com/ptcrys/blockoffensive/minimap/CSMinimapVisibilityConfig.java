package com.ptcrys.blockoffensive.minimap;

/**
 * Defaults from design section 14; server config can override later.
 */
public record CSMinimapVisibilityConfig(
        long losRevealTicks,
        long fireExposureTicks,
        long lastKnownTicks,
        boolean observerOmniscientDefault
) {
    public static final CSMinimapVisibilityConfig DEFAULTS = new CSMinimapVisibilityConfig(
            20L,
            40L,
            60L,
            true
    );

    public CSMinimapVisibilityConfig {
        if (losRevealTicks < 0 || fireExposureTicks < 0 || lastKnownTicks < 0) {
            throw new IllegalArgumentException("visibility durations must be non-negative");
        }
    }
}