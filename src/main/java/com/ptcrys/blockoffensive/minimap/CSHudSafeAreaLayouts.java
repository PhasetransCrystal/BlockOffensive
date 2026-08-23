package com.ptcrys.blockoffensive.minimap;

import com.ptcrys.fpsmatch.core.minimap.hud.ScreenRect;

/**
 * Pure layout math matching BlockOffensive HUD screens. No Minecraft types.
 * Dimensions must stay in lock-step with the real HUD classes.
 */
public final class CSHudSafeAreaLayouts {
    public static final int PRIORITY = 100;

    public static final String ID_SCOREBOARD = "blockoffensive:scoreboard";
    public static final String ID_VOTE = "blockoffensive:vote";
    public static final String ID_BOMB_FUSE = "blockoffensive:bomb_fuse";
    public static final String ID_SPECTATOR_ROSTER = "blockoffensive:spectator_roster";
    public static final String ID_KILL_FEED = "blockoffensive:kill_feed";
    public static final String ID_SPECTATOR_CARD = "blockoffensive:spectator_card";

    public static final int VOTE_WIDTH = 168;
    public static final int VOTE_HEIGHT = 60;
    public static final int VOTE_TOP = 34;

    public static final int BOMB_WIDTH = 120;
    public static final int BOMB_HEIGHT = 26;
    public static final int BOMB_TOP = 50;

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

    private CSHudSafeAreaLayouts() {
    }

    /**
     * Top scoreboard band matching CSGameOverlay / CSDMOverlay scale math (855x480 baseline),
     * including avatar row horizontal offsets.
     */
    public static ScreenRect scoreboard(int screenWidth, int screenHeight) {
        requirePositive(screenWidth, screenHeight);
        float scaleFactor = Math.min(screenWidth / 855.0f, screenHeight / 480.0f);
        int centerX = screenWidth / 2;
        int startY = Math.max(1, Math.round(2 * scaleFactor));
        int backgroundHeight = Math.max(1, Math.round(35 * scaleFactor));
        int boxWidth = Math.max(1, Math.round(24 * scaleFactor));
        int gap = Math.max(0, Math.round(2 * scaleFactor));
        int timeAreaWidth = Math.max(1, Math.round(20 * scaleFactor));
        int offset = Math.max(0, Math.round(26.0f * scaleFactor));
        int left = centerX - timeAreaWidth - gap - boxWidth - offset;
        int right = centerX + timeAreaWidth + gap + boxWidth + offset;
        int width = Math.max(1, right - left);
        return new ScreenRect(left, startY, width, backgroundHeight);
    }

    public static ScreenRect vote(int screenWidth) {
        if (screenWidth <= 0) {
            throw new IllegalArgumentException("screenWidth must be positive");
        }
        int left = (screenWidth - VOTE_WIDTH) / 2;
        return new ScreenRect(left, VOTE_TOP, VOTE_WIDTH, VOTE_HEIGHT);
    }

    public static ScreenRect bombFuse(int screenWidth) {
        if (screenWidth <= 0) {
            throw new IllegalArgumentException("screenWidth must be positive");
        }
        int left = (screenWidth - BOMB_WIDTH) / 2;
        return new ScreenRect(left, BOMB_TOP, BOMB_WIDTH, BOMB_HEIGHT);
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
            panelHeight += ROSTER_ROW_HEIGHT; // overflow "+N" row
        }
        int left = screenWidth - ROSTER_WIDTH - ROSTER_RIGHT;
        return new ScreenRect(left, ROSTER_TOP, ROSTER_WIDTH, panelHeight);
    }

    /**
     * Kill feed corner: position 1 top-left, 2 top-right, 3 bottom-left, 4 bottom-right (BOConfig mapping).
     */
    public static ScreenRect killFeed(int screenWidth, int screenHeight, int position, int rows, int maxRowWidth) {
        requirePositive(screenWidth, screenHeight);
        if (rows <= 0 || maxRowWidth <= 0) {
            throw new IllegalArgumentException("rows and maxRowWidth must be positive when visible");
        }
        int height = rows * KILL_FEED_ROW_HEIGHT;
        int x;
        int y;
        switch (position) {
            case 1 -> { // top-left
                x = KILL_FEED_MARGIN;
                y = KILL_FEED_MARGIN;
            }
            case 2 -> { // top-right
                x = screenWidth - KILL_FEED_MARGIN - maxRowWidth;
                y = KILL_FEED_MARGIN;
            }
            case 4 -> { // bottom-right
                x = screenWidth - KILL_FEED_MARGIN - maxRowWidth;
                y = screenHeight - KILL_FEED_MARGIN * 5;
            }
            default -> { // 3 bottom-left and unknown
                x = KILL_FEED_MARGIN;
                y = screenHeight - KILL_FEED_MARGIN * 5;
            }
        }
        return new ScreenRect(x, y, maxRowWidth, height);
    }

    /**
     * Spectator name card: 320x64, bottom margin 10, centered; slideYPixels matches HUD animation.
     */
    public static ScreenRect spectatorCard(int screenWidth, int screenHeight, float slideYPixels) {
        requirePositive(screenWidth, screenHeight);
        int panelX = (screenWidth - SPECTATOR_CARD_WIDTH) / 2;
        int panelY = Math.round(screenHeight - SPECTATOR_CARD_HEIGHT - SPECTATOR_CARD_BOTTOM + slideYPixels);
        return new ScreenRect(panelX, panelY, SPECTATOR_CARD_WIDTH, SPECTATOR_CARD_HEIGHT);
    }

    private static void requirePositive(int screenWidth, int screenHeight) {
        if (screenWidth <= 0 || screenHeight <= 0) {
            throw new IllegalArgumentException("screen size must be positive");
        }
    }
}