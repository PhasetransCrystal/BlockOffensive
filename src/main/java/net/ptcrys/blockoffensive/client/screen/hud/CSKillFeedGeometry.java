package net.ptcrys.blockoffensive.client.screen.hud;

/** Shared kill-feed row geometry and text-budget math. */
public final class CSKillFeedGeometry {

    private CSKillFeedGeometry() {}

    public record StackGeometry(ScreenRect bounds, int position, int visibleRows) {

        public StackGeometry {
            if (position < 1 || position > 4) {
                throw new IllegalArgumentException("position must be between 1 and 4");
            }
            if (visibleRows <= 0) {
                throw new IllegalArgumentException("visibleRows must be positive");
            }
        }

        public int firstVisibleIndex(int totalRows) {
            if (totalRows < visibleRows) {
                throw new IllegalArgumentException("totalRows cannot be smaller than visibleRows");
            }
            return totalRows - visibleRows;
        }

        public ScreenRect row(int rowIndex, int requestedWidth) {
            if (rowIndex < 0 || rowIndex >= visibleRows) {
                throw new IndexOutOfBoundsException(rowIndex);
            }
            if (requestedWidth <= 0) {
                throw new IllegalArgumentException("requestedWidth must be positive");
            }
            int width = Math.min(requestedWidth, bounds.width());
            int x = isRightAligned(position) ? bounds.right() - width : bounds.x();
            int y = bounds.y() + rowIndex * CSHudSafeAreaLayouts.KILL_FEED_ROW_HEIGHT;
            return new ScreenRect(x, y, width, CSHudSafeAreaLayouts.KILL_FEED_ROW_HEIGHT);
        }
    }

    public record NameBudgets(int first, int second) {

        public NameBudgets {
            if (first < 0 || second < 0) {
                throw new IllegalArgumentException("name budgets cannot be negative");
            }
        }

        public int total() {
            return first + second;
        }
    }

    public static StackGeometry stack(
                                      int screenWidth,
                                      int screenHeight,
                                      int position,
                                      int rows,
                                      int requestedWidth) {
        if (screenWidth <= 0 || screenHeight < CSHudSafeAreaLayouts.KILL_FEED_ROW_HEIGHT) {
            throw new IllegalArgumentException("viewport must fit at least one kill-feed row");
        }
        if (rows <= 0 || requestedWidth <= 0) {
            throw new IllegalArgumentException("rows and requestedWidth must be positive");
        }

        int normalizedPosition = position >= 1 && position <= 4 ? position : 3;
        int widthLimit = Math.max(1, screenWidth - CSHudSafeAreaLayouts.KILL_FEED_MARGIN * 2);
        int width = Math.min(requestedWidth, widthLimit);
        int horizontalMargin = Math.min(
                CSHudSafeAreaLayouts.KILL_FEED_MARGIN,
                Math.max(0, screenWidth - width));
        int x = isRightAligned(normalizedPosition) ? screenWidth - horizontalMargin - width : horizontalMargin;

        int maxRows = Math.max(
                1,
                (screenHeight - CSHudSafeAreaLayouts.KILL_FEED_MARGIN * 2) / CSHudSafeAreaLayouts.KILL_FEED_ROW_HEIGHT);
        int visibleRows = Math.min(rows, maxRows);
        int height = visibleRows * CSHudSafeAreaLayouts.KILL_FEED_ROW_HEIGHT;
        int verticalMargin = Math.min(
                CSHudSafeAreaLayouts.KILL_FEED_MARGIN,
                Math.max(0, screenHeight - height));
        int y = isBottomAligned(normalizedPosition) ? screenHeight - verticalMargin - height : verticalMargin;

        return new StackGeometry(
                new ScreenRect(x, y, width, height),
                normalizedPosition,
                visibleRows);
    }

    public static NameBudgets fitNameBudgets(int firstWidth, int secondWidth, int availableWidth) {
        if (firstWidth < 0 || secondWidth < 0 || availableWidth < 0) {
            throw new IllegalArgumentException("text widths cannot be negative");
        }
        if ((long) firstWidth + secondWidth <= availableWidth) {
            return new NameBudgets(firstWidth, secondWidth);
        }

        int firstBudget = availableWidth / 2;
        int secondBudget = availableWidth - firstBudget;
        if (firstWidth < firstBudget) {
            firstBudget = firstWidth;
            secondBudget = availableWidth - firstBudget;
        } else if (secondWidth < secondBudget) {
            secondBudget = secondWidth;
            firstBudget = availableWidth - secondBudget;
        }
        return new NameBudgets(firstBudget, secondBudget);
    }

    private static boolean isRightAligned(int position) {
        return position == 2 || position == 4;
    }

    private static boolean isBottomAligned(int position) {
        return position == 3 || position == 4;
    }
}
