package com.ptcrys.blockoffensive.minimap;

import com.ptcrys.fpsmatch.core.minimap.hud.HudSafeAreaRegistry;
import com.ptcrys.fpsmatch.core.minimap.hud.ScreenRect;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Contributes the exact geometry snapshot rendered by BO in the current frame. */
public final class CSHudSafeAreaContributors {
    public record RosterSource(BooleanSupplier visible, IntSupplier screenWidth, IntSupplier rowCount) {
    }

    public record KillFeedSource(
            BooleanSupplier visible,
            IntSupplier screenWidth,
            IntSupplier screenHeight,
            IntSupplier position,
            IntSupplier rows,
            IntSupplier maxRowWidth
    ) {
    }

    public record SpectatorCardSource(
            BooleanSupplier visible,
            IntSupplier screenWidth,
            IntSupplier screenHeight,
            Supplier<Float> slideYPixels
    ) {
    }

    private final Supplier<CSHudSafeAreaLayouts.HudGeometry> geometry;
    private final RosterSource roster;
    private final KillFeedSource killFeed;
    private final SpectatorCardSource spectatorCard;

    public CSHudSafeAreaContributors(Supplier<CSHudSafeAreaLayouts.HudGeometry> geometry) {
        this(geometry, null, null, null);
    }

    public CSHudSafeAreaContributors(
            Supplier<CSHudSafeAreaLayouts.HudGeometry> geometry,
            RosterSource roster,
            KillFeedSource killFeed,
            SpectatorCardSource spectatorCard
    ) {
        this.geometry = Objects.requireNonNull(geometry, "geometry");
        this.roster = roster;
        this.killFeed = killFeed;
        this.spectatorCard = spectatorCard;
    }

    public void contributeAll(HudSafeAreaRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        CSHudSafeAreaLayouts.HudGeometry frame = Objects.requireNonNull(
                geometry.get(), "current HUD geometry"
        );
        contributeAll(registry, frame, frame.spectator());
    }

    /**
     * The render manager's context is authoritative for transient spectator surfaces.
     * A stale roster/card projection must not reserve space after a spectator becomes a player.
     */
    public void contributeAll(HudSafeAreaRegistry registry, boolean spectator) {
        Objects.requireNonNull(registry, "registry");
        contributeAll(
                registry,
                Objects.requireNonNull(geometry.get(), "current HUD geometry"),
                spectator
        );
    }

    private void contributeAll(
            HudSafeAreaRegistry registry,
            CSHudSafeAreaLayouts.HudGeometry frame,
            boolean spectator
    ) {
        frame.safeAreas().forEach((id, rect) -> registry.contributeFixed(
                id,
                CSHudSafeAreaLayouts.PRIORITY,
                rect
        ));
        contributeRoster(registry, spectator);
        contributeKillFeed(registry);
        contributeSpectatorCard(registry, spectator);
    }

    private void contributeRoster(HudSafeAreaRegistry registry, boolean spectator) {
        if (!spectator || roster == null || !roster.visible().getAsBoolean()) {
            return;
        }
        int rows = roster.rowCount().getAsInt();
        if (rows <= 0) {
            return;
        }
        registry.contributeFixed(
                CSHudSafeAreaLayouts.ID_SPECTATOR_ROSTER,
                CSHudSafeAreaLayouts.PRIORITY,
                CSHudSafeAreaLayouts.spectatorRoster(roster.screenWidth().getAsInt(), rows)
        );
    }

    private void contributeKillFeed(HudSafeAreaRegistry registry) {
        if (killFeed == null || !killFeed.visible().getAsBoolean()) {
            return;
        }
        int rows = killFeed.rows().getAsInt();
        int maxWidth = killFeed.maxRowWidth().getAsInt();
        if (rows <= 0 || maxWidth <= 0) {
            return;
        }
        registry.contributeFixed(
                CSHudSafeAreaLayouts.ID_KILL_FEED,
                CSHudSafeAreaLayouts.PRIORITY,
                CSHudSafeAreaLayouts.killFeed(
                        killFeed.screenWidth().getAsInt(),
                        killFeed.screenHeight().getAsInt(),
                        killFeed.position().getAsInt(),
                        rows,
                        maxWidth
                )
        );
    }

    private void contributeSpectatorCard(HudSafeAreaRegistry registry, boolean spectator) {
        if (!spectator || spectatorCard == null || !spectatorCard.visible().getAsBoolean()) {
            return;
        }
        registry.contributeFixed(
                CSHudSafeAreaLayouts.ID_SPECTATOR_CARD,
                CSHudSafeAreaLayouts.PRIORITY,
                CSHudSafeAreaLayouts.spectatorCard(
                        spectatorCard.screenWidth().getAsInt(),
                        spectatorCard.screenHeight().getAsInt(),
                        spectatorCard.slideYPixels().get()
                )
        );
    }
}
