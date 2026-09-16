package com.ptcrys.blockoffensive.client.shop;

import icyllis.modernui.R;
import icyllis.modernui.animation.TimeInterpolator;
import icyllis.modernui.animation.ValueAnimator;
import icyllis.modernui.graphics.drawable.ShapeDrawable;

/** Slot fade from CSGameShopScreen.GunButtonLayout at Git revision 9a79ee2. */
final class ShopSlotBackground extends ShapeDrawable {
    static final int REST_COLOR = 0xE82A2A2A;
    private static final long FADE_MILLIS = 200;
    private float emphasis;
    private float target;
    private float scale;
    private int accent;
    private boolean pressed;
    private ValueAnimator animator;

    ShopSlotBackground(float scale, int accent) {
        setStyle(scale, accent);
    }

    void setStyle(float scale, int accent) {
        this.scale = scale;
        this.accent = accent;
        setCornerRadius(4 * scale);
        updateAppearance();
    }

    @Override public boolean isStateful() { return true; }
    @Override public boolean hasFocusStateSpecified() { return true; }

    @Override protected boolean onStateChange(int[] states) {
        boolean enabled = false, highlighted = false, down = false;
        for (int state : states) {
            if (state == R.attr.state_enabled) enabled = true;
            if (state == R.attr.state_hovered || state == R.attr.state_focused
                    || state == R.attr.state_selected) highlighted = true;
            if (state == R.attr.state_pressed) down = true;
        }
        pressed = enabled && down;
        float next = enabled && (highlighted || down) ? 1 : 0;
        if (next != target) {
            target = next;
            if (animator != null) animator.cancel();
            if (isVisible()) {
                // Reverse from the current shade, rather than snapping back to an endpoint.
                animator = ValueAnimator.ofFloat(emphasis, target);
                animator.setDuration(FADE_MILLIS);
                animator.setInterpolator(TimeInterpolator.SINE);
                animator.addUpdateListener(animation -> {
                    emphasis = (float) animation.getAnimatedValue();
                    updateAppearance();
                });
                animator.start();
            } else emphasis = target;
        }
        updateAppearance();
        return true;
    }

    private void updateAppearance() {
        int gray = Math.round(42 + 30 * emphasis);
        setColor((REST_COLOR & 0xFF000000) | gray << 16 | gray << 8 | gray);
        int border = pressed ? accent : 0xFFE1E1E1;
        setStroke(Math.max(1, Math.round(scale)), Math.round(255 * emphasis) << 24 | border & 0xFFFFFF);
    }

    @Override public void jumpToCurrentState() {
        if (animator != null) animator.cancel();
        emphasis = target;
        updateAppearance();
    }

    @Override public boolean setVisible(boolean visible, boolean restart) {
        boolean changed = super.setVisible(visible, restart);
        if (!visible) jumpToCurrentState();
        return changed;
    }
}
