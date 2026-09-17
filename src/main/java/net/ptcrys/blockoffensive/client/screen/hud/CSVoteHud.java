package net.ptcrys.blockoffensive.client.screen.hud;

import net.ptcrys.blockoffensive.net.vote.VoteSyncS2CPacket;
import net.ptcrys.fpsmatch.common.client.screen.mapselect.FPSMGuiTheme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * 全局可复用投票 HUD（加时/取消暂停/换边/未来的踢人/地图投票统一调用）。
 * 由 {@link VoteSyncS2CPacket} 驱动，展示：标题、倒计时进度条、实时同意/反对/未投票数、
 * 通过门槛刻度线、结果结算动画（通过=绿光、否决=红光淡出）。配色统一取自 {@link FPSMGuiTheme}。
 */
public final class CSVoteHud {

    private static final CSVoteHud INSTANCE = new CSVoteHud();

    private static final int PANEL_WIDTH = 168;
    private static final int PANEL_HEIGHT = 60;
    private static final int TOP_MARGIN = 34;
    private static final long RESULT_HOLD_MS = 1400L;

    // 客户端状态
    private boolean active = false;
    private String titleKey = "";
    private int remainingSeconds = 0;
    private int agree = 0;
    private int disagree = 0;
    private int notVoted = 0;
    private int eligible = 0;
    private float threshold = 0.6f;
    private int result = 0; // 0=ongoing 1=pass 2=fail
    private int totalSeconds = 1;
    private long lastUpdateMs = 0L;
    private long resultAtMs = 0L;

    private CSVoteHud() {}

    public static CSVoteHud getInstance() {
        return INSTANCE;
    }

    /** 是否有正在进行的投票（供按键投票门控，避免误触）。 */
    public boolean isActive() {
        return active && result == 0;
    }

    /** 接收服务端同步包（客户端线程）。 */
    public void accept(VoteSyncS2CPacket packet) {
        long now = System.currentTimeMillis();
        boolean wasActive = this.active;
        if (packet.active() && (!wasActive || packet.result() == 0 && this.result != 0)) {
            // 新投票开始：记录总时长用于进度条
            this.totalSeconds = Math.max(1, packet.remainingSeconds());
        }
        this.active = packet.active();
        this.titleKey = packet.titleKey();
        this.remainingSeconds = packet.remainingSeconds();
        this.agree = packet.agree();
        this.disagree = packet.disagree();
        this.notVoted = packet.notVoted();
        this.eligible = packet.eligible();
        this.threshold = packet.threshold();
        this.result = packet.result();
        this.lastUpdateMs = now;
        if (packet.result() != 0) {
            this.resultAtMs = now;
        }
    }

    public void reset() {
        this.active = false;
        this.result = 0;
        this.resultAtMs = 0L;
    }

    /** True when the vote panel occupies screen space this frame. */
    public boolean isRendering() {
        return shouldRender();
    }

    private boolean shouldRender() {
        if (active) {
            return true;
        }
        // 结果结算动画的余韵
        return result != 0 && System.currentTimeMillis() - resultAtMs < RESULT_HOLD_MS;
    }

    public void render(GuiGraphics graphics, int screenWidth, int screenHeight) {
        int left = (screenWidth - PANEL_WIDTH) / 2;
        render(graphics, new ScreenRect(left, TOP_MARGIN, PANEL_WIDTH, PANEL_HEIGHT));
    }

    public void render(GuiGraphics graphics, ScreenRect bounds) {
        if (!shouldRender()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        long now = System.currentTimeMillis();

        // 结果淡出透明度
        float alpha = 1f;
        if (result != 0) {
            float t = (now - resultAtMs) / (float) RESULT_HOLD_MS;
            alpha = Mth.clamp(1f - t, 0f, 1f);
        }
        int a = (int) (alpha * 255) & 0xFF;
        if (a <= 4) {
            return;
        }

        int left = bounds.x();
        int top = bounds.y();
        int right = bounds.right();
        int bottom = bounds.bottom();
        int panelWidth = bounds.width();
        int panelHeight = bounds.height();

        int accent = switch (result) {
            case 1 -> FPSMGuiTheme.ST_WAITING;         // 通过：绿
            case 2 -> FPSMGuiTheme.ACCENT_DANGER;      // 否决：红
            default -> FPSMGuiTheme.ACCENT_PRIMARY;    // 进行中：品牌蓝
        };

        // 面板背景 + 边框 + 顶部强调条
        graphics.fill(left, top, right, bottom, withAlpha(FPSMGuiTheme.BG_PANEL, a));
        graphics.fill(left, top, right, top + 2, withAlpha(accent, a));
        drawBorder(graphics, left, top, right, bottom, withAlpha(FPSMGuiTheme.BORDER_INNER, a));

        // 标题
        Component title = Component.translatable("blockoffensive.vote.hud.title",
                Component.translatable("blockoffensive.cs." + titleKey));
        graphics.drawString(font, title, left + 8, top + 6, withAlpha(FPSMGuiTheme.TEXT_TITLE, a), false);

        // 倒计时数字（右上）
        String secText = Math.max(0, remainingSeconds) + "s";
        graphics.drawString(font, secText, right - 8 - font.width(secText), top + 6, withAlpha(FPSMGuiTheme.TEXT_SUB, a), false);

        // 倒计时进度条
        int barX = left + 8;
        int barY = top + 20;
        int barW = panelWidth - 16;
        int barH = 4;
        float timeFrac = result != 0 ? 0f : Mth.clamp((remainingSeconds - (now - lastUpdateMs) / 1000f) / totalSeconds, 0f, 1f);
        graphics.fill(barX, barY, barX + barW, barY + barH, withAlpha(FPSMGuiTheme.SCROLL_TRACK, a));
        graphics.fill(barX, barY, barX + (int) (barW * timeFrac), barY + barH, withAlpha(accent, a));

        // 票数条：同意(绿) / 反对(红) / 未投(灰)，按 eligible 归一
        int tallyY = top + 30;
        int tallyH = 6;
        int denom = Math.max(1, eligible);
        int agreeW = (int) (barW * (agree / (float) denom));
        int disagreeW = (int) (barW * (disagree / (float) denom));
        int cursor = barX;
        graphics.fill(barX, tallyY, barX + barW, tallyY + tallyH, withAlpha(FPSMGuiTheme.BG_LIST, a));
        graphics.fill(cursor, tallyY, cursor + agreeW, tallyY + tallyH, withAlpha(FPSMGuiTheme.ST_WAITING, a));
        cursor += agreeW;
        graphics.fill(cursor, tallyY, cursor + disagreeW, tallyY + tallyH, withAlpha(FPSMGuiTheme.ACCENT_DANGER, a));

        // 门槛刻度线
        int thresholdX = barX + (int) (barW * Mth.clamp(threshold, 0f, 1f));
        graphics.fill(thresholdX, tallyY - 2, thresholdX + 1, tallyY + tallyH + 2, withAlpha(FPSMGuiTheme.TEXT_HIGHLIGHT, a));

        // 文本：同意/反对/未投
        Component counts = Component.translatable("blockoffensive.vote.hud.counts", agree, disagree, notVoted);
        graphics.drawString(font, counts, left + 8, top + Math.min(40, panelHeight - 20), withAlpha(FPSMGuiTheme.TEXT_BODY, a), false);

        // 提示或结果
        Component footer = switch (result) {
            case 1 -> Component.translatable("blockoffensive.vote.hud.pass");
            case 2 -> Component.translatable("blockoffensive.vote.hud.reject");
            default -> Component.translatable("blockoffensive.vote.hud.hint");
        };
        graphics.drawString(font, footer, left + 8, top + Math.min(50, panelHeight - 10), withAlpha(result == 0 ? FPSMGuiTheme.TEXT_MUTED : accent, a), false);
    }

    private static void drawBorder(GuiGraphics g, int left, int top, int right, int bottom, int color) {
        g.fill(left, top, right, top + 1, color);
        g.fill(left, bottom - 1, right, bottom, color);
        g.fill(left, top, left + 1, bottom, color);
        g.fill(right - 1, top, right, bottom, color);
    }

    private static int withAlpha(int argb, int alpha) {
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }
}
