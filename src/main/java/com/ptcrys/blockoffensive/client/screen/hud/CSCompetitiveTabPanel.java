package com.ptcrys.blockoffensive.client.screen.hud;

import com.ptcrys.blockoffensive.BlockOffensive;
import com.ptcrys.blockoffensive.client.data.CSClientData;
import com.ptcrys.blockoffensive.data.CSScoreboardHistory;
import com.ptcrys.blockoffensive.util.BOUtil;
import com.ptcrys.fpsmatch.common.client.FPSMClient;
import com.ptcrys.fpsmatch.common.client.screen.TabScreen;
import com.ptcrys.fpsmatch.core.data.PlayerData;
import com.ptcrys.fpsmatch.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Reference-sized CS panel. All geometry here is independent of the legacy deathmatch layout. */
@Mod.EventBusSubscriber(modid = BlockOffensive.MODID, value = Dist.CLIENT)
public final class CSCompetitiveTabPanel {
    private static final int CT = 0xFFB4D5EB;
    private static final int T = 0xFFE7CD79;
    private static final int TEXT = 0xFFD4D4D2;
    private static final int MUTED = 0xFFB8BABB;
    private static final int TABLE_X = 55;
    private static final int STATS_X = 274;
    private static final int TABLE_RIGHT = 440;
    private static final int ROW = 13;
    private static final int FIRST_ROW = 61;
    private static final int[] COLUMN_WIDTHS = {34, 26, 26, 26, 28, 26};
    private static final String[] BASIC_HEADERS = {"money", "kills", "deaths", "assists", "headshot", "damage"};
    private static final String[] ADVANCED_HEADERS = {"mvp", "utility", "flashed", "kd", "adr", "damage"};

    private CSCompetitiveTabPanel() {}

    private static Minecraft mc() { return Minecraft.getInstance(); }

    static void render(GuiGraphics g, int width, List<PlayerInfo> players, Map<UUID, PlayerData> data) {
        Map<String, List<PlayerInfo>> teams = RenderUtil.getTeamsPlayerInfo(players);
        List<PlayerInfo> ct = sorted(teams.getOrDefault("ct", List.of()), data);
        List<PlayerInfo> t = sorted(teams.getOrDefault("t", List.of()), data);
        Layout layout = layout(width, mc().getWindow().getGuiScaledHeight(), ct.size(), t.size());
        g.pose().pushPose();
        try {
            g.pose().translate(layout.x(), layout.y(), 0);
            g.pose().scale(layout.scale(), layout.scale(), 1);
            g.fillGradient(0, 0, 480, layout.height(), 0xEC343638, 0xF01C1D1E);
            g.fill(12, 28, 440, 29, 0x187F8386);
            renderHeading(g);
            renderHeaders(g);
            renderTeam(g, ct, data, FIRST_ROW, layout.slots(), true);
            renderRoundStrip(g, FIRST_ROW + layout.slots() * ROW + 3);
            renderTeam(g, t, data, layout.secondRow(), layout.slots(), false);
            String hint = mc().screen instanceof TabScreen
                    ? Component.translatable("blockoffensive.tab.cs.cursor_hint").getString()
                    : Component.translatable("blockoffensive.tab.cs.switch_hint",
                            mc().options.keyPlayerList.getTranslatedKeyMessage()).getString();
            text(g, hint, 440 - Minecraft.getInstance().font.width(hint) * .85f, layout.height() - 16, MUTED, .85f);
        } finally {
            g.pose().popPose();
        }
    }

    private static List<PlayerInfo> sorted(List<PlayerInfo> players, Map<UUID, PlayerData> data) {
        List<PlayerInfo> result = new ArrayList<>(players);
        result.sort(Comparator.<PlayerInfo>comparingDouble(info -> {
            PlayerData pd = data.get(info.getProfile().getId());
            return pd == null ? 0 : pd.getDamage();
        }).reversed().thenComparing(info -> info.getProfile().getName(), String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private static void renderHeading(GuiGraphics g) {
        g.fill(13, 13, 24, 24, 0xFF8A7543);
        centered(g, "CS", 18.5f, 16, CT, .6f);
        String title = Component.translatable("blockoffensive.tab.cs.title",
                FPSMClient.getGlobalData().getCurrentMap()).getString();
        text(g, fit(title, 340, 1), 30, 16, MUTED, 1);
        int seconds = CSClientData.scoreboardElapsedSeconds;
        String time = String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
        text(g, time, 440 - Minecraft.getInstance().font.width(time) * .95f, 15, MUTED, .95f);
        g.fill(13, 33, 22, 43, 0xFF606466);
        centered(g, "B", 17.5f, 34, TEXT, .95f);
        text(g, "Block Offensive", 27, 35, MUTED, .95f);
        badge(g, CSClientData.scoreboardAdvanced ? "II" : "I", 428, 36, TEXT);
    }

    private static void renderHeaders(GuiGraphics g) {
        centered(g, "ms", 74, 53, TEXT, .85f);
        String[] headers = CSClientData.scoreboardAdvanced ? ADVANCED_HEADERS : BASIC_HEADERS;
        int x = STATS_X;
        for (int i = 0; i < headers.length; i++) {
            String label = Component.translatable("blockoffensive.tab.cs." + headers[i]).getString();
            centered(g, label, x + COLUMN_WIDTHS[i] / 2f, 53, TEXT, cellScale(label, COLUMN_WIDTHS[i], .85f));
            x += COLUMN_WIDTHS[i];
        }
    }

    private static void renderTeam(GuiGraphics g, List<PlayerInfo> players, Map<UUID, PlayerData> data,
                                   int y, int slots, boolean ct) {
        int color = ct ? CT : T;
        int score = ct ? CSClientData.cTWinnerRounds : CSClientData.tWinnerRounds;
        g.fill(14, y + 7, 46, y + 38, (color & 0xFFFFFF) | 0x16000000);
        centered(g, Integer.toString(score), 30, y + 9, color, cellScale(Integer.toString(score), 42, 3.2f));
        centered(g, ct ? "CT" : "T", 30, y + 43, color, .75f);
        long living = players.stream().map(info -> data.get(info.getProfile().getId()))
                .filter(pd -> pd != null && pd.isLiving()).count();
        centered(g, Component.translatable("blockoffensive.tab.cs.alive", living, players.size()).getString(),
                30, y + 57, color, .85f);
        for (int i = 0; i < slots; i++) {
            int rowY = y + i * ROW;
            g.fill(TABLE_X, rowY, TABLE_RIGHT, rowY + ROW - 1, i % 2 == 0 ? 0x255C6062 : 0x205C6062);
            if (i < players.size()) renderPlayer(g, players.get(i), data.get(players.get(i).getProfile().getId()), rowY, ct);
        }
    }

    private static void renderPlayer(GuiGraphics g, PlayerInfo info, PlayerData pd, int y, boolean ct) {
        UUID id = info.getProfile().getId();
        int color = ct ? CT : T;
        boolean local = mc().player != null && id.equals(mc().player.getUUID());
        if (local) g.fill(TABLE_X, y, TABLE_RIGHT, y + ROW - 1, (color & 0xFFFFFF) | 0x35000000);
        centered(g, Integer.toString(info.getLatency()), 74, y + 3, MUTED, .95f);
        g.fill(101, y, 113, y + 12, BOUtil.getColor(id));
        PlayerFaceRenderer.draw(g, info.getSkinLocation(), 102, y + 1, 10);
        String name = info.getTabListDisplayName() == null ? info.getProfile().getName() : info.getTabListDisplayName().getString();
        text(g, fit(name, STATS_X - 122, .95f), 117, y + 3, color, .95f);
        String[] values = values(id, pd, ct);
        int x = STATS_X;
        for (int i = 0; i < values.length; i++) {
            if (i % 2 == 0 || i == values.length - 1) {
                g.fill(x, y, x + COLUMN_WIDTHS[i], y + ROW - 1, i == 5 ? 0x286E7375 : 0x126E7375);
            }
            centered(g, values[i], x + COLUMN_WIDTHS[i] / 2f, y + 3, i == 5 ? 0xFFF6F6EE : color,
                    cellScale(values[i], COLUMN_WIDTHS[i], .95f));
            x += COLUMN_WIDTHS[i];
        }
        if (pd != null && !pd.isLiving()) g.fill(TABLE_X, y, TABLE_RIGHT, y + ROW - 1, 0x60000000);
    }

    private static String[] values(UUID id, PlayerData pd, boolean ct) {
        if (pd == null) return new String[]{"—", "—", "—", "—", "—", "—"};
        String damage = Long.toString(Math.round(pd.getDamage()));
        if (CSClientData.scoreboardAdvanced) {
            // Aggregated player damage includes the current round; include it in the ADR denominator too.
            int rounds = CSClientData.cTWinnerRounds + CSClientData.tWinnerRounds;
            if (CSClientData.isStart && !CSClientData.isWaiting && !CSClientData.isWaitingWinner) rounds++;
            return new String[]{pd.getMvpCount() == 0 ? "" : "*" + pd.getMvpCount(),
                    Long.toString(Math.round(pd.getUtilityDamage())), Integer.toString(pd.getFlashedEnemies()),
                    String.format(Locale.ROOT, "%.2f", pd.getKills() / (float) Math.max(1, pd.getDeaths())),
                    Long.toString(Math.round(pd.getDamage() / Math.max(1, rounds))), damage};
        }
        boolean teammate = (ct ? "ct" : "t").equals(FPSMClient.getGlobalData().getCurrentTeam());
        return new String[]{teammate ? "$" + FPSMClient.getGlobalData().getPlayerMoney(id) : "",
                Integer.toString(pd.getKills()), Integer.toString(pd.getDeaths()), Integer.toString(pd.getAssists()),
                Math.round(pd.getHeadshotRate() * 100) + "%", damage};
    }

    private static void renderRoundStrip(GuiGraphics g, int y) {
        int[] rounds = CSClientData.scoreboardRounds;
        int half = CSClientData.scoreboardHalfRounds;
        // Half scores follow the current roster, while the timeline uses the current side's color.
        halfScore(g, rounds, half, true, y + 2, CT);
        centered(g, Component.translatable("blockoffensive.tab.cs.first_half").getString(), 20, y + 17, TEXT, .85f);
        centered(g, Component.translatable("blockoffensive.tab.cs.second_half").getString(), 40, y + 17, TEXT, .85f);
        halfScore(g, rounds, half, false, y + 40, T);

        int visible = Math.min(30, Math.max(half * 2, rounds.length + 1));
        int first = Math.max(0, rounds.length + 1 - visible);
        float cell = 226f / visible;
        int axisY = y + 24;
        for (int slot = 0; slot < visible; slot++) {
            int round = first + slot;
            int x = Math.round(90 + slot * cell);
            int right = Math.round(90 + (slot + 1) * cell) - 1;
            g.fill(x, axisY, right, axisY + 1, round == rounds.length ? 0xFFDDDDDD : 0xFF646765);
            if (round < rounds.length) {
                int result = rounds[round];
                String marker = switch (Math.abs(result)) {
                    case CSScoreboardHistory.TIMEOUT -> "T";
                    case CSScoreboardHistory.DEFUSE -> "D";
                    case CSScoreboardHistory.EXPLOSION -> "B";
                    default -> "x";
                };
                centered(g, marker, (x + right) / 2f, result > 0 ? axisY - 11 : axisY + 5,
                        result > 0 ? CT : T, .85f);
            }
            if ((round + 1) % 5 == 0) centered(g, Integer.toString(round + 1), right, axisY - 2, MUTED, .5f);
            if (round + 1 == half) g.fill(right, axisY - 12, right + 1, axisY + 14, 0x357F8386);
        }
        centered(g, Component.translatable("blockoffensive.tab.cs.loss_bonus").getString(), 423, y + 23, TEXT, .85f);
        lossBar(g, 408, y + 16, CSClientData.scoreboardCtLoss, CT);
        lossBar(g, 408, y + 35, CSClientData.scoreboardTLoss, T);
    }

    private static void halfScore(GuiGraphics g, int[] rounds, int half, boolean ct, int y, int color) {
        centered(g, Integer.toString(CSScoreboardHistory.wins(rounds, ct, 0, half)), 20, y, color, .85f);
        centered(g, Integer.toString(CSScoreboardHistory.wins(rounds, ct, half, half * 2)), 40, y, color, .85f);
    }

    private static void lossBar(GuiGraphics g, int x, int y, int factor, int color) {
        for (int i = 0; i < 5; i++) g.fill(x + i * 6, y, x + i * 6 + 5, y + 1, i < factor ? color : 0xFF181A1B);
    }

    private static void badge(GuiGraphics g, String label, int x, int y, int color) {
        g.fill(x, y, x + 12, y + 9, 0x237F8386);
        centered(g, label, x + 6, y + 2, color, .6f);
    }

    private static String fit(String text, int width, float scale) {
        var font = Minecraft.getInstance().font;
        int available = Math.max(0, (int) (width / scale));
        return font.width(text) <= available ? text
                : font.plainSubstrByWidth(text, Math.max(0, available - font.width("…"))) + "…";
    }

    private static float cellScale(String text, int width, float preferred) {
        return Math.min(preferred, (width - 3f) / Math.max(1, Minecraft.getInstance().font.width(text)));
    }

    private static void centered(GuiGraphics g, String value, float x, float y, int color, float scale) {
        text(g, value, x - Minecraft.getInstance().font.width(value) * scale / 2, y, color, scale);
    }

    private static void text(GuiGraphics g, String value, float x, float y, int color, float scale) {
        g.pose().pushPose();
        // Align the final position to framebuffer pixels after both panel and GUI scaling.
        var matrix = g.pose().last().pose();
        float pixels = (float) mc().getWindow().getGuiScale();
        float snappedX = (Math.round((matrix.m30() + x * matrix.m00()) * pixels) / pixels - matrix.m30()) / matrix.m00();
        float snappedY = (Math.round((matrix.m31() + y * matrix.m11()) * pixels) / pixels - matrix.m31()) / matrix.m11();
        g.pose().translate(snappedX, snappedY, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(Minecraft.getInstance().font, value, 0, 0, color, false);
        g.pose().popPose();
    }

    private static Layout layout(int width, int height, int ct, int t) {
        int slots = Math.max(5, Math.max(ct, t));
        int panelHeight = 292 + (slots - 5) * ROW * 2;
        // Keep Chinese labels readable on smaller windows, independently of Minecraft GUI scale.
        float preferred = Math.max(Math.min(width / 960f, height / 540f),
                2f / (float) mc().getWindow().getGuiScale());
        float scale = Math.min(preferred, Math.min((width - 24f) / 480, (height - 24f) / (panelHeight + 30)));
        return new Layout((width - 480 * scale) / 2, (height - panelHeight * scale) / 2 + 15 * scale,
                scale, panelHeight, slots, FIRST_ROW + slots * ROW + 56);
    }

    private record Layout(float x, float y, float scale, int height, int slots, int secondRow) {}

    private static boolean csTabVisible() {
        if (!FPSMClient.getGlobalData().isCurrentGameType("cs")) return false;
        return mc().screen instanceof TabScreen tab && "cs".equals(tab.tab.getGameType())
                || mc().screen == null && mc().options.keyPlayerList.isDown();
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (mc().screen == null && event.getAction() == GLFW.GLFW_PRESS
                && event.getKey() == GLFW.GLFW_KEY_RIGHT && csTabVisible()) toggle();
    }

    @SubscribeEvent
    public static void onScreenKey(ScreenEvent.KeyPressed.Pre event) {
        if (event.getKeyCode() == GLFW.GLFW_KEY_RIGHT && csTabVisible()) {
            toggle();
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onScreenClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != 0 || !csTabVisible()) return;
        Map<String, List<PlayerInfo>> teams = RenderUtil.getTeamsPlayerInfo();
        Layout l = layout(mc().getWindow().getGuiScaledWidth(), mc().getWindow().getGuiScaledHeight(),
                teams.getOrDefault("ct", List.of()).size(), teams.getOrDefault("t", List.of()).size());
        double x = (event.getMouseX() - l.x()) / l.scale();
        double y = (event.getMouseY() - l.y()) / l.scale();
        if (x >= 426 && x <= 442 && y >= 32 && y <= 48) {
            toggle();
            event.setCanceled(true);
        }
    }

    private static void toggle() { CSClientData.scoreboardAdvanced = !CSClientData.scoreboardAdvanced; }
}
