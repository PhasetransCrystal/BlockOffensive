package com.ptcrys.blockoffensive.minimap.acceptance;

/** Keeps acceptance-only behavior fail-closed without changing packet ids. */
final class BOMinimapAcceptanceEnvironment {
    private BOMinimapAcceptanceEnvironment() {
    }

    static void requireAvailable(boolean production) {
        if (production) {
            throw new IllegalStateException(
                    "Minimap acceptance fixtures are disabled in production."
            );
        }
    }

    static void runIfAvailable(boolean production, Runnable action) {
        if (!production) {
            action.run();
        }
    }
}
