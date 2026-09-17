package net.ptcrys.blockoffensive.client.screen.hud;

/** Small immutable rectangle used by the BlockOffensive HUD layout code. */
public record ScreenRect(int x, int y, int width, int height) {

    public ScreenRect {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("ScreenRect size must be positive");
        }
    }

    public int right() {
        return x + width;
    }

    public int bottom() {
        return y + height;
    }

    public boolean intersects(ScreenRect other) {
        return x < other.right() && right() > other.x && y < other.bottom() && bottom() > other.y;
    }
}
