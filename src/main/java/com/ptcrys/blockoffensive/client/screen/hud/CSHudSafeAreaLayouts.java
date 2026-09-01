package com.ptcrys.blockoffensive.minimap;

import com.ptcrys.fpsmatch.core.minimap.hud.ScreenRect;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Pure geometry shared by BO HUD renderers and FPSMatch safe-area contributors. */
public final class CSHudSafeAreaLayouts {
    public static final int PRIORITY = 100;
    public static final int MAX_SCOREBOARD_PLAYERS = 15;

    public static final String ID_SCOREBOARD = "blockoffensive:scoreboard";
    public static final String ID_VOTE = "blockoffensive:vote";
    public static final String ID_BOMB_FUSE = "blockoffensive:bomb_fuse";
    public static final String ID_DEMOLITION_PROGRESS = "blockoffensive:demolition_progress";
    public static final String ID_MONEY = "blockoffensive:money";
    public static final String ID_COMBAT_INFO = "blockoffensive:combat_info";
    public static final String ID_ITEM_BAR = "blockoffensive:item_bar";
    public static final String ID_SPECTATOR_ROSTER = "blockoffensive:spectator_roster";
    public static final String ID_KILL_FEED = "blockoffensive:kill_feed";
    public static final String ID_SPECTATOR_CARD = "blockoffensive:spectator_card";

    public static final int VOTE_WIDTH = 168;
    public static final int VOTE_HEIGHT = 60;
    public static final int BOMB_WIDTH = 120;
    public static final int BOMB_HEIGHT = 26;
    public static final int STATUS_GAP = 4;

    public static final int ROSTER_WIDTH = 108;
    public static final int ROSTER_ROW_HEIGHT = 11;
    public static final int ROSTER_MAX_ROWS = 8;
    public static final int ROSTER_RIGHT = 6;
    public static final int ROSTER_TOP = 60;

    public static final int SPECTATOR_CARD_WIDTH = 320;
    public static final int SPECTATOR_CARD_HEIGHT = 64;
    public static final int SPECTATOR_CARD_BOTTOM = 10;

    public static final int KILL_FEED_ROW_HEIGHT = 16;
    public static final int KILL_FEED_MARGIN = 10;

    private static final int VIEWPORT_MARGIN = 4;
    private static final int ITEM_BAR_BASE_WIDTH = 144;
    private static final int ITEM_BAR_BASE_HEIGHT = 100;
    private static final int ITEM_BAR_RIGHT = 10;
    private static final int ITEM_BAR_BOTTOM = 10;

    private CSHudSafeAreaLayouts() {
    }

    public enum GameType {
        CS,
        CSDM
    }

    public sealed interface ScoreboardLayout permits CsScoreboardLayout, CsdmScoreboardLayout {
        ScreenRect bounds();
    }

    public record AvatarStrip(
            int startX,
            int y,
            int avatarSize,
            int gap,
            int count,
            int direction,
            int renderedHeight
    ) {
        public AvatarStrip {
            if (avatarSize <= 0 || gap < 0 || count < 0 || renderedHeight <= 0) {
                throw new IllegalArgumentException("invalid avatar strip dimensions");
            }
            if (direction != -1 && direction != 1) {
                throw new IllegalArgumentException("direction must be -1 or 1");
            }
        }

        public int xAt(int index) {
            if (index < 0 || index >= count) {
                throw new IndexOutOfBoundsException(index);
            }
            return startX + direction * index * (avatarSize + gap);
        }

        public Optional<ScreenRect> bounds() {
            if (count == 0) {
                return Optional.empty();
            }
            int lastX = xAt(count - 1);
            int left = Math.min(startX, lastX);
            int right = Math.max(startX, lastX) + avatarSize;
            return Optional.of(new ScreenRect(left, y, right - left, renderedHeight));
        }
    }

    public record CsScoreboardLayout(
            ScreenRect bounds,
            float scale,
            int centerX,
            int startY,
            int backgroundHeight,
            int timeBarHeight,
            int scoreBarHeight,
            int boxWidth,
            int gap,
            int timeAreaWidth,
            ScreenRect ctBox,
            ScreenRect tBox,
            AvatarStrip ctAvatars,
            AvatarStrip tAvatars,
            boolean expandedInfo
    ) implements ScoreboardLayout {
        public CsScoreboardLayout {
            Objects.requireNonNull(bounds, "bounds");
            Objects.requireNonNull(ctBox, "ctBox");
            Objects.requireNonNull(tBox, "tBox");
            Objects.requireNonNull(ctAvatars, "ctAvatars");
            Objects.requireNonNull(tAvatars, "tAvatars");
        }
    }

    public record CsdmScoreboardLayout(
            ScreenRect bounds,
            float scale,
            int centerX,
            int startY,
            int timeBarHeight,
            int scoreBgHeight,
            int timeAreaWidth,
            AvatarStrip avatars
    ) implements ScoreboardLayout {
        public CsdmScoreboardLayout {
            Objects.requireNonNull(bounds, "bounds");
            Objects.requireNonNull(avatars, "avatars");
        }
    }

    public record TopStatusStack(Optional<ScreenRect> bombFuse, Optional<ScreenRect> vote) {
        public TopStatusStack {
            Objects.requireNonNull(bombFuse, "bombFuse");
            Objects.requireNonNull(vote, "vote");
        }
    }

    public record MoneyLayout(ScreenRect bounds, float scale) {
        public MoneyLayout {
            Objects.requireNonNull(bounds, "bounds");
        }
    }

    public record CombatInfoLayout(
            ScreenRect bounds,
            int centerX,
            int lineWidth,
            int baselineY,
            int leftExtent,
            int rightExtent
    ) {
        public CombatInfoLayout {
            Objects.requireNonNull(bounds, "bounds");
        }
    }

    public record ItemBarLayout(ScreenRect bounds, float scale) {
        public ItemBarLayout {
            Objects.requireNonNull(bounds, "bounds");
        }
    }

    public record BottomHudLayout(
            Optional<MoneyLayout> money,
            CombatInfoLayout combatInfo,
            ItemBarLayout itemBar
    ) {
        public BottomHudLayout {
            Objects.requireNonNull(money, "money");
            Objects.requireNonNull(combatInfo, "combatInfo");
            Objects.requireNonNull(itemBar, "itemBar");
        }
    }

    public record HudGeometry(
            GameType gameType,
            int screenWidth,
            int screenHeight,
            boolean spectator,
            ScoreboardLayout scoreboardLayout,
            ScreenRect scoreboard,
            TopStatusStack topStatus,
            Optional<BottomHudLayout> bottomHud,
            Map<String, ScreenRect> safeAreas
    ) {
        public HudGeometry {
            Objects.requireNonNull(gameType, "gameType");
            Objects.requireNonNull(scoreboardLayout, "scoreboardLayout");
            Objects.requireNonNull(scoreboard, "scoreboard");
            Objects.requireNonNull(topStatus, "topStatus");
            Objects.requireNonNull(bottomHud, "bottomHud");
            safeAreas = Collections.unmodifiableMap(new LinkedHashMap<>(safeAreas));
        }
    }

    public static HudGeometry geometry(
            GameType gameType,
            int screenWidth,
            int screenHeight,
            int leftPlayers,
            int rightPlayers,
            boolean spectator,
            boolean bombVisible,
            boolean voteVisible,
            int moneyWidth,
            int moneyHeight,
            int combatLeftExtent,
            int combatRightExtent,
            int combatHeight
    ) {
        return geometry(
                gameType,
                screenWidth,
                screenHeight,
                leftPlayers,
                rightPlayers,
                spectator,
                bombVisible,
                voteVisible,
                moneyWidth,
                moneyHeight,
                combatLeftExtent,
                combatRightExtent,
                combatHeight,
                false
        );
    }

    public static HudGeometry geometry(
            GameType gameType,
            int screenWidth,
            int screenHeight,
            int leftPlayers,
            int rightPlayers,
            boolean spectator,
            boolean bombVisible,
            boolean voteVisible,
            int moneyWidth,
            int moneyHeight,
            int combatLeftExtent,
            int combatRightExtent,
            int combatHeight,
            boolean expandedInfo
    ) {
        return geometry(
                gameType,
                screenWidth,
                screenHeight,
                leftPlayers,
                rightPlayers,
                spectator,
                bombVisible,
                voteVisible,
                moneyWidth,
                moneyHeight,
                combatLeftExtent,
                combatRightExtent,
                combatHeight,
                expandedInfo,
                false
        );
    }

    public static HudGeometry geometry(
            GameType gameType,
            int screenWidth,
            int screenHeight,
            int leftPlayers,
            int rightPlayers,
            boolean spectator,
            boolean bombVisible,
            boolean voteVisible,
            int moneyWidth,
            int moneyHeight,
            int combatLeftExtent,
            int combatRightExtent,
            int combatHeight,
            boolean expandedInfo,
            boolean demolitionProgressVisible
    ) {
        Objects.requireNonNull(gameType, "gameType");
        requirePositive(screenWidth, screenHeight);
        requireNonNegative(leftPlayers, "leftPlayers");
        requireNonNegative(rightPlayers, "rightPlayers");
        requirePositive(moneyWidth, moneyHeight);
        if (combatLeftExtent < 0 || combatRightExtent < 0 || combatHeight <= 0) {
            throw new IllegalArgumentException("invalid combat HUD extents");
        }

        ScoreboardLayout scoreboardLayout = gameType == GameType.CS
                ? csScoreboard(screenWidth, screenHeight, leftPlayers, rightPlayers, expandedInfo)
                : csdmScoreboard(screenWidth, screenHeight, leftPlayers + rightPlayers);
        ScreenRect scoreboard = scoreboardLayout.bounds();
        TopStatusStack topStatus = topStatusStack(
                screenWidth,
                scoreboard,
                gameType == GameType.CS && spectator && bombVisible,
                voteVisible
        );

        LinkedHashMap<String, ScreenRect> safeAreas = new LinkedHashMap<>();
        safeAreas.put(ID_SCOREBOARD, scoreboard);
        topStatus.bombFuse().ifPresent(rect -> safeAreas.put(ID_BOMB_FUSE, rect));
        topStatus.vote().ifPresent(rect -> safeAreas.put(ID_VOTE, rect));
        if (gameType == GameType.CS && demolitionProgressVisible) {
            safeAreas.put(ID_DEMOLITION_PROGRESS, demolitionProgress(screenWidth, screenHeight));
        }

        Optional<BottomHudLayout> bottomHud = Optional.empty();
        if (!spectator) {
            CombatInfoLayout combat = combatInfo(
                    screenWidth,
                    screenHeight,
                    combatLeftExtent,
                    combatRightExtent,
                    combatHeight
            );
            Optional<MoneyLayout> money = gameType == GameType.CS
                    ? Optional.of(money(screenWidth, screenHeight, moneyWidth, moneyHeight, combat.bounds()))
                    : Optional.empty();
            int topLimit = topStatus.vote().or(() -> topStatus.bombFuse())
                    .map(ScreenRect::bottom)
                    .orElse(scoreboard.bottom()) + STATUS_GAP;
            ItemBarLayout itemBar = itemBar(screenWidth, screenHeight, combat.bounds(), topLimit);
            bottomHud = Optional.of(new BottomHudLayout(money, combat, itemBar));
            money.ifPresent(layout -> safeAreas.put(ID_MONEY, layout.bounds()));
            safeAreas.put(ID_COMBAT_INFO, combat.bounds());
            safeAreas.put(ID_ITEM_BAR, itemBar.bounds());
        }

        return new HudGeometry(
                gameType,
                screenWidth,
                screenHeight,
                spectator,
                scoreboardLayout,
                scoreboard,
                topStatus,
                bottomHud,
                safeAreas
        );
    }

    public static ScreenRect demolitionProgress(int screenWidth, int screenHeight) {
        requirePositive(screenWidth, screenHeight);
        int width = 150;
        int height = 6;
        int centeredY = (int) (screenHeight / 2.0f + 90);
        return new ScreenRect(
                screenWidth / 2 - width / 2,
                Math.min(centeredY, screenHeight - height),
                width,
                height
        );
    }

    public static CsScoreboardLayout csScoreboard(
            int screenWidth,
            int screenHeight,
            int ctPlayers,
            int tPlayers,
            boolean expandedInfo
    ) {
        requirePositive(screenWidth, screenHeight);
        requireNonNegative(ctPlayers, "ctPlayers");
        requireNonNegative(tPlayers, "tPlayers");
        int ctCount = Math.min(MAX_SCOREBOARD_PLAYERS, ctPlayers);
        int tCount = Math.min(MAX_SCOREBOARD_PLAYERS, tPlayers);
        float scale = baseScale(screenWidth, screenHeight);
        int centerX = screenWidth / 2;
        int startY = Math.max(1, px(2, scale));
        int backgroundHeight = px(35, scale);
        int timeBarHeight = px(13, scale);
        int scoreBarHeight = px(19, scale);
        int boxWidth = px(24, scale);
        int gap = px(2, scale);
        int timeAreaWidth = px(20, scale);
        int avatarGap = px(3, scale);
        int offset = px(26, scale);
        int inset = px(2, scale);
        int rowY = startY + (expandedInfo ? px(6, scale) : 0);
        int extraBelow = expandedInfo ? px(14, scale) : px(3, scale);

        int ctBoxX = centerX - timeAreaWidth - gap - boxWidth;
        int tBoxX = centerX + timeAreaWidth + gap;
        int ctAnchorRight = ctBoxX - offset + boxWidth - inset;
        int tAnchorLeft = tBoxX + offset + inset;
        int ctAvatarSize = fittedAvatarSize(px(24, scale), avatarGap, ctCount,
                Math.max(1, ctAnchorRight - VIEWPORT_MARGIN));
        int tAvatarSize = fittedAvatarSize(px(24, scale), avatarGap, tCount,
                Math.max(1, screenWidth - VIEWPORT_MARGIN - tAnchorLeft));
        int avatarSize = Math.max(1, Math.min(ctAvatarSize, tAvatarSize));
        int renderedHeight = avatarSize + extraBelow;

        AvatarStrip ctAvatars = new AvatarStrip(
                ctAnchorRight - avatarSize,
                rowY,
                avatarSize,
                avatarGap,
                ctCount,
                -1,
                renderedHeight
        );
        AvatarStrip tAvatars = new AvatarStrip(
                tAnchorLeft,
                rowY,
                avatarSize,
                avatarGap,
                tCount,
                1,
                renderedHeight
        );
        ScreenRect ctBox = new ScreenRect(ctBoxX, startY, boxWidth, backgroundHeight);
        ScreenRect tBox = new ScreenRect(tBoxX, startY, boxWidth, backgroundHeight);
        int left = Math.min(ctBox.x(), ctAvatars.bounds().map(ScreenRect::x).orElse(ctBox.x()));
        int right = Math.max(tBox.right(), tAvatars.bounds().map(ScreenRect::right).orElse(tBox.right()));
        int top = expandedInfo ? Math.max(0, rowY - px(7, scale)) : startY;
        int bottom = Math.max(startY + backgroundHeight,
                Math.max(ctAvatars.bounds().map(ScreenRect::bottom).orElse(startY),
                        tAvatars.bounds().map(ScreenRect::bottom).orElse(startY)));
        ScreenRect bounds = new ScreenRect(left, top, right - left, bottom - top);

        return new CsScoreboardLayout(
                bounds,
                scale,
                centerX,
                startY,
                backgroundHeight,
                timeBarHeight,
                scoreBarHeight,
                boxWidth,
                gap,
                timeAreaWidth,
                ctBox,
                tBox,
                ctAvatars,
                tAvatars,
                expandedInfo
        );
    }

    public static CsdmScoreboardLayout csdmScoreboard(int screenWidth, int screenHeight, int players) {
        requirePositive(screenWidth, screenHeight);
        requireNonNegative(players, "players");
        int count = Math.min(MAX_SCOREBOARD_PLAYERS, players);
        float scale = baseScale(screenWidth, screenHeight);
        int centerX = screenWidth / 2;
        int startY = Math.max(1, px(2, scale));
        int timeBarHeight = px(13, scale);
        int scoreBgHeight = px(8, scale);
        int timeAreaWidth = px(20, scale);
        int avatarGap = px(16, scale);
        int available = Math.max(1, screenWidth - VIEWPORT_MARGIN * 2);
        int avatarSize = fittedAvatarSize(px(24, scale), avatarGap, count, available);
        int totalWidth = stripWidth(avatarSize, avatarGap, count);
        int rowY = startY + timeBarHeight + px(2, scale);
        AvatarStrip avatars = new AvatarStrip(
                centerX - totalWidth / 2,
                rowY,
                avatarSize,
                avatarGap,
                count,
                1,
                avatarSize + scoreBgHeight
        );
        int left = Math.min(centerX - timeAreaWidth,
                avatars.bounds().map(ScreenRect::x).orElse(centerX - timeAreaWidth));
        int right = Math.max(centerX + timeAreaWidth,
                avatars.bounds().map(ScreenRect::right).orElse(centerX + timeAreaWidth));
        int bottom = Math.max(startY + timeBarHeight,
                avatars.bounds().map(ScreenRect::bottom).orElse(startY + timeBarHeight));
        ScreenRect bounds = new ScreenRect(left, startY, right - left, bottom - startY);
        return new CsdmScoreboardLayout(
                bounds,
                scale,
                centerX,
                startY,
                timeBarHeight,
                scoreBgHeight,
                timeAreaWidth,
                avatars
        );
    }

    /** Compatibility overload used by older fixed-area integrations. */
    public static ScreenRect scoreboard(int screenWidth, int screenHeight) {
        return csScoreboard(screenWidth, screenHeight, 0, 0, false).bounds();
    }

    public static TopStatusStack topStatusStack(
            int screenWidth,
            ScreenRect scoreboard,
            boolean bombVisible,
            boolean voteVisible
    ) {
        if (screenWidth <= 0) {
            throw new IllegalArgumentException("screenWidth must be positive");
        }
        Objects.requireNonNull(scoreboard, "scoreboard");
        int nextY = scoreboard.bottom() + STATUS_GAP;
        Optional<ScreenRect> bomb = Optional.empty();
        if (bombVisible) {
            ScreenRect rect = centered(screenWidth, nextY, BOMB_WIDTH, BOMB_HEIGHT);
            bomb = Optional.of(rect);
            nextY = rect.bottom() + STATUS_GAP;
        }
        Optional<ScreenRect> vote = voteVisible
                ? Optional.of(centered(screenWidth, nextY, VOTE_WIDTH, VOTE_HEIGHT))
                : Optional.empty();
        return new TopStatusStack(bomb, vote);
    }

    /** Compatibility helpers now anchor below the legacy scoreboard instead of hard-coded rows. */
    public static ScreenRect vote(int screenWidth) {
        return centered(screenWidth, scoreboard(screenWidth, 480).bottom() + STATUS_GAP,
                VOTE_WIDTH, VOTE_HEIGHT);
    }

    public static ScreenRect bombFuse(int screenWidth) {
        return centered(screenWidth, scoreboard(screenWidth, 480).bottom() + STATUS_GAP,
                BOMB_WIDTH, BOMB_HEIGHT);
    }

    public static MoneyLayout money(
            int screenWidth,
            int screenHeight,
            int renderedWidth,
            int renderedHeight,
            ScreenRect combatBounds
    ) {
        requirePositive(screenWidth, screenHeight);
        requirePositive(renderedWidth, renderedHeight);
        Objects.requireNonNull(combatBounds, "combatBounds");
        int width = Math.min(renderedWidth, Math.max(1, screenWidth - 10));
        int x = 5;
        int y = Math.max(0, screenHeight - renderedHeight - 2);
        ScreenRect candidate = new ScreenRect(x, y, width, renderedHeight);
        if (candidate.intersects(combatBounds)) {
            y = Math.max(0, combatBounds.y() - STATUS_GAP - renderedHeight);
            candidate = new ScreenRect(x, y, width, renderedHeight);
        }
        return new MoneyLayout(candidate, Math.min(2.0f, renderedHeight / 9.0f));
    }

    public static CombatInfoLayout combatInfo(
            int screenWidth,
            int screenHeight,
            int leftExtent,
            int rightExtent,
            int renderedHeight
    ) {
        requirePositive(screenWidth, screenHeight);
        if (leftExtent < 0 || rightExtent < 0 || renderedHeight <= 0) {
            throw new IllegalArgumentException("invalid combat HUD extents");
        }
        int centerX = screenWidth / 2;
        int lineWidth = Math.max(1, Math.round(screenWidth * 0.26f));
        int requestedWidth = lineWidth + leftExtent + rightExtent;
        int width = Math.min(requestedWidth, Math.max(1, screenWidth - VIEWPORT_MARGIN * 2));
        int x = Math.max(VIEWPORT_MARGIN, centerX - width / 2);
        if (x + width > screenWidth - VIEWPORT_MARGIN) {
            x = screenWidth - VIEWPORT_MARGIN - width;
        }
        int y = Math.max(0, screenHeight - renderedHeight - VIEWPORT_MARGIN);
        int boundedLeft = Math.min(leftExtent, Math.max(0, centerX - x));
        int boundedRight = Math.min(rightExtent, Math.max(0, x + width - centerX));
        int boundedLineWidth = Math.max(1, width - boundedLeft - boundedRight);
        int baselineY = Math.min(screenHeight - 1, y + Math.max(1, renderedHeight - 12));
        return new CombatInfoLayout(
                new ScreenRect(x, y, width, renderedHeight),
                centerX,
                boundedLineWidth,
                baselineY,
                boundedLeft,
                boundedRight
        );
    }

    public static ItemBarLayout itemBar(
            int screenWidth,
            int screenHeight,
            ScreenRect combatBounds,
            int topLimit
    ) {
        requirePositive(screenWidth, screenHeight);
        Objects.requireNonNull(combatBounds, "combatBounds");
        float responsiveScale = clamp(
                Math.min(screenWidth / 640.0f, screenHeight / 360.0f),
                0.72f,
                1.0f
        );
        int width = Math.min(px(ITEM_BAR_BASE_WIDTH, responsiveScale), screenWidth - VIEWPORT_MARGIN * 2);
        int height = px(ITEM_BAR_BASE_HEIGHT, responsiveScale);
        int x = Math.max(VIEWPORT_MARGIN, screenWidth - ITEM_BAR_RIGHT - width);
        int y = Math.max(topLimit, screenHeight - ITEM_BAR_BOTTOM - height);
        ScreenRect candidate = new ScreenRect(x, y, width, height);
        if (candidate.intersects(combatBounds)) {
            y = combatBounds.y() - STATUS_GAP - height;
        }
        int availableHeight = Math.max(1, y + height - topLimit);
        if (y < topLimit) {
            height = Math.min(height, availableHeight);
            y = topLimit;
        }
        if (y + height > screenHeight) {
            height = Math.max(1, screenHeight - y);
        }
        candidate = new ScreenRect(x, y, width, height);
        float actualScale = Math.min(width / (float) ITEM_BAR_BASE_WIDTH,
                height / (float) ITEM_BAR_BASE_HEIGHT);
        return new ItemBarLayout(candidate, actualScale);
    }

    public static ScreenRect spectatorRoster(int screenWidth, int rowCount) {
        if (screenWidth <= 0) {
            throw new IllegalArgumentException("screenWidth must be positive");
        }
        if (rowCount <= 0) {
            throw new IllegalArgumentException("rowCount must be positive when visible");
        }
        int shown = Math.min(rowCount, ROSTER_MAX_ROWS);
        int panelHeight = 16 + shown * ROSTER_ROW_HEIGHT + 4;
        if (rowCount > ROSTER_MAX_ROWS) {
            panelHeight += ROSTER_ROW_HEIGHT;
        }
        int left = screenWidth - ROSTER_WIDTH - ROSTER_RIGHT;
        return new ScreenRect(left, ROSTER_TOP, ROSTER_WIDTH, panelHeight);
    }

    /** Kill feed corner: 1 top-left, 2 top-right, 3 bottom-left, 4 bottom-right. */
    public static ScreenRect killFeed(int screenWidth, int screenHeight, int position, int rows, int maxRowWidth) {
        return CSKillFeedGeometry.stack(
                screenWidth,
                screenHeight,
                position,
                rows,
                maxRowWidth
        ).bounds();
    }

    public static ScreenRect spectatorCard(int screenWidth, int screenHeight, float slideYPixels) {
        requirePositive(screenWidth, screenHeight);
        int width = Math.min(SPECTATOR_CARD_WIDTH, screenWidth);
        int panelX = (screenWidth - width) / 2;
        int panelY = Math.round(screenHeight - SPECTATOR_CARD_HEIGHT - SPECTATOR_CARD_BOTTOM + slideYPixels);
        return new ScreenRect(panelX, panelY, width, SPECTATOR_CARD_HEIGHT);
    }

    private static ScreenRect centered(int screenWidth, int y, int requestedWidth, int height) {
        int width = Math.min(requestedWidth, Math.max(1, screenWidth - VIEWPORT_MARGIN * 2));
        return new ScreenRect((screenWidth - width) / 2, y, width, height);
    }

    private static int fittedAvatarSize(int preferred, int gap, int count, int available) {
        if (count == 0) {
            return preferred;
        }
        int fitted = (available - gap * Math.max(0, count - 1)) / count;
        return Math.max(1, Math.min(preferred, fitted));
    }

    private static int stripWidth(int avatarSize, int gap, int count) {
        if (count == 0) {
            return 1;
        }
        return count * avatarSize + Math.max(0, count - 1) * gap;
    }

    private static float baseScale(int screenWidth, int screenHeight) {
        return Math.min(screenWidth / 855.0f, screenHeight / 480.0f);
    }

    private static int px(int base, float scale) {
        return Math.max(1, Math.round(base * scale));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void requirePositive(int first, int second) {
        if (first <= 0 || second <= 0) {
            throw new IllegalArgumentException("dimensions must be positive");
        }
    }

    private static void requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
    }
}
