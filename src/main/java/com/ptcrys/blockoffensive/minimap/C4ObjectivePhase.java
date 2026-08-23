package com.ptcrys.blockoffensive.minimap;

/**
 * Authoritative C4 lifecycle phases for minimap objective tracking.
 * DEFUSING is a planted sub-state with an active demolisher.
 */
public enum C4ObjectivePhase {
    NONE,
    CARRIED,
    DROPPED,
    PLANTED,
    DEFUSING,
    DEFUSED,
    EXPLODED,
    REMOVED
}