package net.ptcrys.blockoffensive.client.screen.hud;

import net.ptcrys.blockoffensive.BOConfig;
import net.ptcrys.blockoffensive.net.spec.SpectatorRosterS2CPacket;
import net.ptcrys.fpsmatch.common.client.screen.mapselect.FPSMGuiTheme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 观战者名单侧栏（仅观战时显示）。展示当前正在观战对局的玩家名单。
 * 弥补此前“无观战者列表 UI”的缺口。配色统一取自 {@link FPSMGuiTheme}。
 */
public final class CSSpectatorRoster {

    private static final CSSpectatorRoster INSTANCE = new CSSpectatorRoster();
    private static final int PANEL_WIDTH = 108;
    private static final int ROW_HEIGHT = 11;
    private static final int MAX_ROWS = 8;
    private static final int RIGHT_MARGIN = 6;
    private static final int TOP = 60;

    private volatile List<String> names = new ArrayList<>();

    private CSSpectatorRoster() {}

    public static CSSpectatorRoster getInstance() {
        return INSTANCE;
    }

    public void accept(SpectatorRosterS2CPacket packet) {
        this.names = packet.names();
    }

    public void reset() {
        this.names = new ArrayList<>();
    }

    public boolean isRendering() {
        if (!BOConfig.client.spectatorRosterEnabled.get()) {
            return false;
        }
        return !this.names.isEmpty();
    }

    public int visibleRowCount() {
        return this.names.size();
    }

    public void render(GuiGraphics graphics, int screenWidth, int screenHeight) {
        if (!BOConfig.client.spectatorRosterEnabled.get()) {
            return;
        }
        List<String> current = this.names;
        if (current.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        int shown = Math.min(current.size(), MAX_ROWS);
        int panelHeight = 16 + shown * ROW_HEIGHT + 4;
        int left = screenWidth - PANEL_WIDTH - RIGHT_MARGIN;
        int top = TOP;
        int right = left + PANEL_WIDTH;
        int bottom = top + panelHeight;

        graphics.fill(left, top, right, bottom, FPSMGuiTheme.BG_PANEL);
        graphics.fill(left, top, right, top + 2, FPSMGuiTheme.ST_SPECTATOR);
        drawBorder(graphics, left, top, right, bottom, FPSMGuiTheme.BORDER_INNER);

        Component title = Component.translatable("blockoffensive.spectator.roster.title", current.size());
        graphics.drawString(font, title, left + 6, top + 5, FPSMGuiTheme.TEXT_TITLE, false);

        int y = top + 16;
        for (int i = 0; i < shown; i++) {
            String name = current.get(i);
            graphics.drawString(font, clip(font, name, PANEL_WIDTH - 12), left + 6, y, FPSMGuiTheme.TEXT_BODY, false);
            y += ROW_HEIGHT;
        }
        if (current.size() > MAX_ROWS) {
            graphics.drawString(font, Component.literal("+" + (current.size() - MAX_ROWS)), left + 6, y, FPSMGuiTheme.TEXT_MUTED, false);
        }
    }

    private static String clip(Font font, String value, int maxWidth) {
        if (font.width(value) <= maxWidth) {
            return value;
        }
        return font.plainSubstrByWidth(value, maxWidth - font.width("...")) + "...";
    }

    private static void drawBorder(GuiGraphics g, int left, int top, int right, int bottom, int color) {
        g.fill(left, top, right, top + 1, color);
        g.fill(left, bottom - 1, right, bottom, color);
        g.fill(left, top, left + 1, bottom, color);
        g.fill(right - 1, top, right, bottom, color);
    }
}
