package com.ptcrys.blockoffensive.client.shop;

import com.ptcrys.fpsmatch.common.client.screen.modernui.ModernScreen;
import icyllis.modernui.R;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.drawable.Drawable;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.graphics.drawable.StateListDrawable;
import icyllis.modernui.resources.TypedValue;
import icyllis.modernui.util.ColorStateList;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.TextView;
import icyllis.modernui.widget.ImageView;
import java.util.Map;
import java.util.WeakHashMap;

/** Neutral charcoal surfaces with the original Modern UI shop's team accents. */
public final class ShopStyle {
    public static final int CT_ACCENT = 0xFF96C8FA;
    public static final int T_ACCENT = 0xFFEAC055;
    // Accessed only on the UI thread; discarded views must not be retained by the singleton screen.
    private final Map<View, Integer> viewAccents = new WeakHashMap<>();

    public void apply(View view, ModernScreen.Node node, float scale, boolean changed, int accent) {
        Integer previousAccent = viewAccents.put(view, accent);
        changed |= previousAccent == null || previousAccent != accent;
        String key = node.key();
        if (changed) {
            if (key.equals("header")) view.setBackground(surface(0xD9080808, 0, 0, scale));
            else if (key.endsWith(".surface")) view.setBackground(new MeshPanel(scale));
            else if (key.equals("footer.rule")) view.setBackground(surface(0x405F6265, 0, 0, scale));
            else if (key.startsWith("slot.empty.")) view.setBackground(surface(ShopSlotBackground.REST_COLOR, 0, 4, scale));
            else if (key.equals("refund") && view instanceof ImageView icon) {
                // Inline card action: indicate interaction on the arrow, never a second panel.
                view.setBackground(null);
                icon.setImageTintList(new ColorStateList(
                        new int[][]{{-R.attr.state_enabled}, {R.attr.state_pressed},
                                {R.attr.state_hovered}, {R.attr.state_focused}, {}},
                        new int[]{0xFF717171, accent, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFB7B7B7}));
            }
            else if (node.kind() == ModernScreen.Kind.ACTION_FRAME) {
                // Keep the drawable across data/selection refreshes so an active fade can finish.
                if (view.getBackground() instanceof ShopSlotBackground background) background.setStyle(scale, accent);
                else view.setBackground(new ShopSlotBackground(scale, accent));
            }
            else if (node.kind() == ModernScreen.Kind.BUTTON || node.kind() == ModernScreen.Kind.ICON) {
                StateListDrawable states = new StateListDrawable();
                states.addState(new int[]{R.attr.state_pressed}, surface(0x40000000 | accent & 0xFFFFFF, 0, 2, scale));
                states.addState(new int[]{R.attr.state_hovered}, surface(0x22000000 | accent & 0xFFFFFF, 0, 2, scale));
                states.addState(new int[]{R.attr.state_focused}, surface(0x22000000 | accent & 0xFFFFFF, accent, 2, scale));
                states.addState(new int[]{}, surface(0, 0, 0, scale));
                view.setBackground(states);
            }
        }
        if (!(view instanceof TextView text)) return;
        int color = node.kind() == ModernScreen.Kind.MUTED ? 0xFF717171
                : node.kind() == ModernScreen.Kind.ACCENT ? accent : 0xFFD1D1D1;
        float size = 14;
        int gravity = Gravity.CENTER_VERTICAL | Gravity.LEFT;
        boolean bold = false;
        if (key.equals("money")) { color = accent; size = 23; bold = true; }
        else if (key.equals("time")) { size = 20; gravity = Gravity.CENTER; }
        else if (key.equals("next")) { color = 0xFFE6DD28; size = 13; gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL; }
        else if (key.startsWith("category.name.")) { size = 26; gravity = Gravity.CENTER; color = node.selected() ? accent : 0xFFD3D3D3; }
        else if (key.startsWith("category.number.")) { size = 16; color = node.selected() ? accent : 0xFFBDBDBD; }
        else if (key.equals("number")) { size = 16; }
        else if (key.equals("name")) { size = 13; gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL; }
        else if (key.equals("price")) { size = 14; bold = true; gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL; }
        else if (key.equals("owned")) { color = accent; size = 13; bold = true; gravity = Gravity.CENTER; }
        else if (key.equals("drops.empty")) { color = 0xFF393939; size = 80; bold = true; gravity = Gravity.CENTER; }
        else if (key.equals("controls") || key.equals("refundAll") || key.equals("close")) { color = accent; size = 20; bold = true; gravity = Gravity.CENTER; }
        text.setTextColor(color);
        text.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * scale);
        text.setTextStyle(bold ? 1 : 0);
        text.setGravity(gravity);
        text.setPadding(0, 0, 0, 0);
        text.setSingleLine(true);
        text.setEllipsize(icyllis.modernui.text.TextUtils.TruncateAt.END);
    }

    private static ShapeDrawable surface(int color, int border, float radius, float scale) {
        ShapeDrawable shape = new ShapeDrawable();
        shape.setColor(color);
        shape.setCornerRadius(radius * scale);
        if (border != 0) shape.setStroke(Math.max(1, Math.round(scale)), border);
        return shape;
    }

    /** Fine mesh stays at display-pixel density instead of becoming a Minecraft-scale grid. */
    private static final class MeshPanel extends Drawable {
        private final float step;
        MeshPanel(float scale) { step = Math.max(3, 4 * scale); }
        @Override public void draw(Canvas canvas) {
            var bounds = getBounds();
            Paint paint = Paint.obtain();
            paint.setColor(0xCD090909);
            canvas.drawRect(bounds.left, bounds.top, bounds.right, bounds.bottom, paint);
            paint.setColor(0x153F3F3F);
            for (float x = bounds.left; x < bounds.right; x += step)
                canvas.drawRect(x, bounds.top, Math.min(x + 1, bounds.right), bounds.bottom, paint);
            for (float y = bounds.top; y < bounds.bottom; y += step)
                canvas.drawRect(bounds.left, y, bounds.right, Math.min(y + 1, bounds.bottom), paint);
            paint.recycle();
        }
    }
}
