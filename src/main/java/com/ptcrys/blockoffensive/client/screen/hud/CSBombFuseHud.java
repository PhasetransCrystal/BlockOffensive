package com.ptcrys.blockoffensive.client.screen.hud;

import com.ptcrys.blockoffensive.client.data.CSClientData;
import com.ptcrys.fpsmatch.common.client.screen.mapselect.FPSMGuiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * 观战者游戏内 C4 引信倒计时 HUD。
 * <p>数据来自 {@link CSClientData#bombFuse}/{@link CSClientData#bombTotalFuse}（服务端 setFuse 每 tick 同步给观战者，
 * 回合清理时置 0）。此前该数据仅被 Web 覆盖层消费，游戏内无 HUD——本类补齐这一缺口。
 */
public final class CSBombFuseHud {
    private static final CSBombFuseHud INSTANCE = new CSBombFuseHud();
    private static final int PANEL_WIDTH = 120;
    private static final int PANEL_HEIGHT = 26;
    private static final int TOP_MARGIN = 50;

    private CSBombFuseHud() {
    }

    public static CSBombFuseHud getInstance() {
        return INSTANCE;
    }

    public boolean isRendering() {
        if (!com.ptcrys.blockoffensive.BOConfig.client.spectatorBombHudEnabled.get()) {
            return false;
        }
        int fuse = CSClientData.bombFuse;
        int total = CSClientData.bombTotalFuse;
        return fuse > 0 && total > 0;
    }

    public void render(GuiGraphics graphics, int screenWidth, int screenHeight) {
        int left = (screenWidth - PANEL_WIDTH) / 2;
        render(graphics, new ScreenRect(left, TOP_MARGIN, PANEL_WIDTH, PANEL_HEIGHT));
    }

    public void render(GuiGraphics graphics, ScreenRect bounds) {
        if (!com.ptcrys.blockoffensive.BOConfig.client.spectatorBombHudEnabled.get()) {
            return;
        }
        int fuse = CSClientData.bombFuse;
        int total = CSClientData.bombTotalFuse;
        if (fuse <= 0 || total <= 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        int left = bounds.x();
        int top = bounds.y();
        int right = bounds.right();
        int bottom = bounds.bottom();
        int panelWidth = bounds.width();

        // 临近爆炸的红色脉冲
        float fraction = Mth.clamp(fuse / (float) total, 0f, 1f);
        float pulse = 0.6f + 0.4f * (float) Math.abs(Math.sin(System.currentTimeMillis() / 200.0));
        int danger = fraction < 0.25f
                ? withPulse(FPSMGuiTheme.ACCENT_DANGER, pulse)
                : FPSMGuiTheme.ACCENT_DANGER;

        graphics.fill(left, top, right, bottom, FPSMGuiTheme.BG_PANEL);
        graphics.fill(left, top, right, top + 2, danger);
        drawBorder(graphics, left, top, right, bottom, FPSMGuiTheme.BORDER_INNER);

        // 文本：C4 + 剩余秒数
        int seconds = Mth.ceil(fuse / 20.0f);
        Component label = Component.translatable("blockoffensive.spectator.bomb.fuse", seconds);
        graphics.drawString(font, label, left + 8, top + 5, danger, false);

        // 倒计时进度条
        int barX = left + 8;
        int barY = top + 17;
        int barW = panelWidth - 16;
        int barH = 3;
        graphics.fill(barX, barY, barX + barW, barY + barH, FPSMGuiTheme.SCROLL_TRACK);
        graphics.fill(barX, barY, barX + (int) (barW * fraction), barY + barH, danger);
    }

    private static void drawBorder(GuiGraphics g, int left, int top, int right, int bottom, int color) {
        g.fill(left, top, right, top + 1, color);
        g.fill(left, bottom - 1, right, bottom, color);
        g.fill(left, top, left + 1, bottom, color);
        g.fill(right - 1, top, right, bottom, color);
    }

    private static int withPulse(int argb, float pulse) {
        int a = (int) (((argb >> 24) & 0xFF) * pulse) & 0xFF;
        return (a << 24) | (argb & 0x00FFFFFF);
    }
}
