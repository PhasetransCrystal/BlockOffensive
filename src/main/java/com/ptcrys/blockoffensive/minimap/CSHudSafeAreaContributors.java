package com.ptcrys.blockoffensive.minimap;

import com.ptcrys.fpsmatch.core.minimap.hud.HudSafeAreaRegistry;
import com.ptcrys.fpsmatch.core.minimap.hud.ScreenRect;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Per-frame BO HUD safe-area contributions. Priority 100, stable IDs.
 * Visibility predicates mirror real HUD render gates; rectangles use {@link CSHudSafeAreaLayouts}.
 */
public final class CSHudSafeAreaContributors {
    public record ScoreboardSource(BooleanSupplier visible, IntSupplier screenWidth, IntSupplier screenHeight) {}
    public record SimpleTopSource(BooleanSupplier visible, IntSupplier screenWidth) {}
    public record RosterSource(BooleanSupplier visible, IntSupplier screenWidth, IntSupplier rowCount) {}
    public record KillFeedSource(
            BooleanSupplier visible,
            IntSupplier screenWidth,
            IntSupplier screenHeight,
            IntSupplier position,
            IntSupplier rows,
            IntSupplier maxRowWidth
    ) {}
    public record SpectatorCardSource(
            BooleanSupplier visible,
            IntSupplier screenWidth,
            IntSupplier screenHeight,
            Supplier<Float> slideYPixels
    ) {}

    private final ScoreboardSource scoreboard;
    private final SimpleTopSource vote;
    private final SimpleTopSource bombFuse;
    private final RosterSource roster;
    private final KillFeedSource killFeed;
    private final SpectatorCardSource spectatorCard;

    public CSHudSafeAreaContributors(
            ScoreboardSource scoreboard,
            SimpleTopSource vote,
            SimpleTopSource bombFuse,
            RosterSource roster,
            KillFeedSource killFeed,
            SpectatorCardSource spectatorCard
    ) {
        this.scoreboard = Objects.requireNonNull(scoreboard, "scoreboard");
        this.vote = Objects.requireNonNull(vote, "vote");
        this.bombFuse = Objects.requireNonNull(bombFuse, "bombFuse");
        this.roster = Objects.requireNonNull(roster, "roster");
        this.killFeed = Objects.requireNonNull(killFeed, "killFeed");
        this.spectatorCard = Objects.requireNonNull(spectatorCard, "spectatorCard");
    }

    public void contributeAll(HudSafeAreaRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        contributeScoreboard(registry);
        contributeVote(registry);
        contributeBombFuse(registry);
        contributeRoster(registry);
        contributeKillFeed(registry);
        contributeSpectatorCard(registry);
    }

    public void contributeScoreboard(HudSafeAreaRegistry registry) {
        if (!scoreboard.visible.getAsBoolean()) {
            return;
        }
        ScreenRect rect = CSHudSafeAreaLayouts.scoreboard(
                scoreboard.screenWidth.getAsInt(),
                scoreboard.screenHeight.getAsInt()
        );
        registry.contributeFixed(CSHudSafeAreaLayouts.ID_SCOREBOARD, CSHudSafeAreaLayouts.PRIORITY, rect);
    }

    public void contributeVote(HudSafeAreaRegistry registry) {
        if (!vote.visible.getAsBoolean()) {
            return;
        }
        registry.contributeFixed(
                CSHudSafeAreaLayouts.ID_VOTE,
                CSHudSafeAreaLayouts.PRIORITY,
                CSHudSafeAreaLayouts.vote(vote.screenWidth.getAsInt())
        );
    }

    public void contributeBombFuse(HudSafeAreaRegistry registry) {
        if (!bombFuse.visible.getAsBoolean()) {
            return;
        }
        registry.contributeFixed(
                CSHudSafeAreaLayouts.ID_BOMB_FUSE,
                CSHudSafeAreaLayouts.PRIORITY,
                CSHudSafeAreaLayouts.bombFuse(bombFuse.screenWidth.getAsInt())
        );
    }

    public void contributeRoster(HudSafeAreaRegistry registry) {
        if (!roster.visible.getAsBoolean()) {
            return;
        }
        int rows = roster.rowCount.getAsInt();
        if (rows <= 0) {
            return;
        }
        registry.contributeFixed(
                CSHudSafeAreaLayouts.ID_SPECTATOR_ROSTER,
                CSHudSafeAreaLayouts.PRIORITY,
                CSHudSafeAreaLayouts.spectatorRoster(roster.screenWidth.getAsInt(), rows)
        );
    }

    public void contributeKillFeed(HudSafeAreaRegistry registry) {
        if (!killFeed.visible.getAsBoolean()) {
            return;
        }
        int rows = killFeed.rows.getAsInt();
        int maxW = killFeed.maxRowWidth.getAsInt();
        if (rows <= 0 || maxW <= 0) {
            return;
        }
        registry.contributeFixed(
                CSHudSafeAreaLayouts.ID_KILL_FEED,
                CSHudSafeAreaLayouts.PRIORITY,
                CSHudSafeAreaLayouts.killFeed(
                        killFeed.screenWidth.getAsInt(),
                        killFeed.screenHeight.getAsInt(),
                        killFeed.position.getAsInt(),
                        rows,
                        maxW
                )
        );
    }

    public void contributeSpectatorCard(HudSafeAreaRegistry registry) {
        if (!spectatorCard.visible.getAsBoolean()) {
            return;
        }
        registry.contributeFixed(
                CSHudSafeAreaLayouts.ID_SPECTATOR_CARD,
                CSHudSafeAreaLayouts.PRIORITY,
                CSHudSafeAreaLayouts.spectatorCard(
                        spectatorCard.screenWidth.getAsInt(),
                        spectatorCard.screenHeight.getAsInt(),
                        spectatorCard.slideYPixels.get()
                )
        );
    }
}