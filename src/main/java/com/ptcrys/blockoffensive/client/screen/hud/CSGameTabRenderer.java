package com.ptcrys.blockoffensive.client.screen.hud;

import com.ptcrys.blockoffensive.client.data.CSClientData;
import com.ptcrys.blockoffensive.util.BOUtil;
import com.ptcrys.fpsmatch.common.client.FPSMClient;
import com.ptcrys.fpsmatch.common.client.tab.TabRenderer;
import com.ptcrys.fpsmatch.core.data.PlayerData;
import com.ptcrys.fpsmatch.core.team.BaseTeam;
import com.ptcrys.fpsmatch.util.RenderUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** CS:GO-inspired scoreboard shown while the player holds Tab. */
public class CSGameTabRenderer implements TabRenderer {
    public static final ResourceLocation GUI_ICONS_LOCATION = ResourceLocation.tryBuild("minecraft", "textures/gui/icons.png");
    private static final ResourceLocation BACKGROUND = ResourceLocation.tryBuild("blockoffensive", "textures/ui/cs/background.png");
    protected final Minecraft minecraft = Minecraft.getInstance();

    protected static final int COL_PADDING = 5;
    protected static final int COL_PING = 40;
    protected static final int AVATAR_SIZE = 18;
    protected static final int COL_NAME = 300;
    protected static final int COL_MONEY = 68;
    protected static final int COL_KILL = 48;
    protected static final int COL_DEATH = 48;
    protected static final int COL_ASSIST = 48;
    protected static final int COL_HEADSHOT = 40;
    protected static final int COL_DAMAGE = 58;
    protected static final int ROW_HEIGHT = 26;
    protected static final int ROW_GAP = 1;
    protected static final int BG_PADDING = 0;
    protected static final int HEADER_HEIGHT = 22;
    protected static final int PLAYER_AREA_WIDTH = COL_PING + AVATAR_SIZE + COL_PADDING + COL_NAME
            + COL_MONEY + COL_KILL + COL_DEATH + COL_ASSIST + COL_DAMAGE;
    protected static final int ROW_NAME_AREA = COL_PING + AVATAR_SIZE + COL_PADDING + COL_NAME;
    private static final int CS_PLAYER_AREA_WIDTH = PLAYER_AREA_WIDTH - COL_HEADSHOT;

    @Override
    public String getGameType() {
        return "cs";
    }

    @Override
    public void render(GuiGraphics graphics, int windowWidth, List<PlayerInfo> playerInfoList,
                       Scoreboard scoreboard, Objective objective) {
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        drawBackdrop(graphics, windowWidth, screenHeight);

        Map<UUID, PlayerData> dataById = indexPlayerData(playerInfoList);
        Map<String, List<PlayerInfo>> teams = RenderUtil.getTeamsPlayerInfo(playerInfoList);
        Comparator<PlayerInfo> byDamage = Comparator.comparingDouble((PlayerInfo info) ->
                damageOf(dataById, info)).reversed();
        List<PlayerInfo> ctPlayers = new ArrayList<>(teams.getOrDefault("ct", Collections.emptyList()));
        List<PlayerInfo> tPlayers = new ArrayList<>(teams.getOrDefault("t", Collections.emptyList()));
        ctPlayers.sort(byDamage);
        tPlayers.sort(byDamage);

        int top = Math.max(58, Math.min(96, screenHeight / 8));
        drawMatchHeader(graphics, windowWidth, top - 42);
        drawTeamPanel(graphics, 24, top + 31, "CT", ctPlayers.size(), CSClientData.cTWinnerRounds,
                BOUtil.CT_COLOR, true);
        drawTeamPanel(graphics, 24, top + 31 + 248, "T", tPlayers.size(), CSClientData.tWinnerRounds,
                BOUtil.T_COLOR, false);

        int tableX = Math.max(108, (windowWidth - CS_PLAYER_AREA_WIDTH) / 2);
        int rowStart = top + 10;
        drawTableHeader(graphics, tableX, rowStart);
        renderRows(graphics, ctPlayers, tableX, rowStart + HEADER_HEIGHT, dataById, BOUtil.CT_COLOR);
        int tStart = rowStart + HEADER_HEIGHT + Math.max(5, ctPlayers.size() * (ROW_HEIGHT + ROW_GAP)) + 100;
        drawRoundTimeline(graphics, tableX + 50, tStart - 23, CS_PLAYER_AREA_WIDTH - 105);
        renderRows(graphics, tPlayers, tableX, tStart, dataById, BOUtil.T_COLOR);
        drawCompensation(graphics, tableX + CS_PLAYER_AREA_WIDTH - 62, tStart - 53);
    }

    private void drawBackdrop(GuiGraphics graphics, int width, int height) {
        graphics.fill(0, 0, width, height, 0xA0000000);
        graphics.blit(BACKGROUND, 0, 0, 0, 0, width, height, 1920, 1080);
        graphics.fill(0, 0, width, height, 0x4A000000);
    }

    private void drawMatchHeader(GuiGraphics graphics, int width, int y) {
        graphics.fill(27, y + 1, 44, y + 20, 0xFF27356E);
        graphics.fill(31, y - 1, 40, y + 7, 0xFFFFA31A);
        graphics.fill(35, y + 5, 42, y + 16, 0xFFFFA31A);
        String map = FPSMClient.getGlobalData().getCurrentMap();
        if (map == null || map.isBlank() || "none".equals(map) || "fpsm_none".equals(map)) map = "未知地图";
        draw(graphics, Component.literal("竞技模式 | " + map).withStyle(ChatFormatting.BOLD), 53, y + 1, 0xFFE3E3E3);
        draw(graphics, Component.literal("Valve · 反恐精英：全球攻势"), 53, y + 21, 0xFF8C8C8C);
        draw(graphics, Component.literal(formatTime(CSClientData.time)), width - 86, y + 4, 0xFFB8B8B8);
        graphics.fill(24, y + 44, width - 82, y + 45, 0x667A7A7A);
    }

    private void drawTeamPanel(GuiGraphics graphics, int x, int y, String team, int players, int score,
                               int color, boolean ct) {
        int circleColor = (color & 0xFFFFFF) | 0x66000000;
        graphics.fill(x + 11, y + 7, x + 78, y + 74, circleColor);
        graphics.fill(x + 15, y + 11, x + 74, y + 70, 0x33000000);
        draw(graphics, Component.literal(String.valueOf(score)), x + 38, y + 21, color | 0xFF000000);
        draw(graphics, Component.literal(team), x + 35, y + 79, color | 0xFF000000);
        int living = FPSMClient.getGlobalData().getLivingWithTeam(ct ? "ct" : "t");
        draw(graphics, Component.literal("存活: " + living + "/" + Math.max(players, 5)), x + 2, y + 102, 0xFF979797);
        draw(graphics, Component.literal("前半场"), x + 1, y + 133, 0xFF9B9B9B);
        draw(graphics, Component.literal("后半场"), x + 45, y + 133, 0xFF9B9B9B);
        draw(graphics, Component.literal(ct ? String.valueOf(CSClientData.cTWinnerRounds) : String.valueOf(CSClientData.tWinnerRounds)),
                x + 14, y + 154, color | 0xFF000000);
    }

    private void drawTableHeader(GuiGraphics graphics, int x, int y) {
        int cursor = x;
        blitPing(graphics, cursor + 15, y + 7);
        cursor += COL_PING + AVATAR_SIZE + COL_PADDING;
        drawCentered(graphics, "玩家", cursor, y + 6, COL_NAME, 0xFFA8A8A8);
        cursor += COL_NAME;
        drawCentered(graphics, "金钱", cursor, y + 6, COL_MONEY, 0xFFA8A8A8);
        cursor += COL_MONEY;
        drawCentered(graphics, "击杀", cursor, y + 6, COL_KILL, 0xFFA8A8A8);
        cursor += COL_KILL;
        drawCentered(graphics, "死亡", cursor, y + 6, COL_DEATH, 0xFFA8A8A8);
        cursor += COL_DEATH;
        drawCentered(graphics, "助攻", cursor, y + 6, COL_ASSIST, 0xFFA8A8A8);
        drawCentered(graphics, "伤害", cursor + COL_ASSIST, y + 6, COL_DAMAGE, 0xFFA8A8A8);
    }

    private void renderRows(GuiGraphics graphics, List<PlayerInfo> players, int x, int y,
                            Map<UUID, PlayerData> dataById, int teamColor) {
        for (int i = 0; i < players.size(); i++) {
            renderPlayerRow(graphics, players.get(i), x, y + i * (ROW_HEIGHT + ROW_GAP), dataById, teamColor);
        }
    }

    private void renderPlayerRow(GuiGraphics graphics, PlayerInfo info, int x, int y,
                                 Map<UUID, PlayerData> dataById, int teamColor) {
        UUID uuid = info.getProfile().getId();
        PlayerData data = dataById.get(uuid);
        if (data == null) return;
        boolean local = minecraft.player != null && uuid.equals(minecraft.player.getUUID());
        int rowColor = local ? ((teamColor & 0xFFFFFF) | 0x66555555) : 0xB0474747;
        graphics.fill(x, y, x + CS_PLAYER_AREA_WIDTH, y + ROW_HEIGHT, rowColor);
        if (!data.isLiving()) graphics.fill(x, y, x + CS_PLAYER_AREA_WIDTH, y + ROW_HEIGHT, 0x55333333);

        int textY = y + 8;
        int cursor = x;
        drawCentered(graphics, String.valueOf(info.getLatency()), cursor, textY, COL_PING, 0xFFB5B5B5);
        cursor += COL_PING;
        PlayerFaceRenderer.draw(graphics, info.getSkinLocation(), cursor, y + 4, AVATAR_SIZE);
        cursor += AVATAR_SIZE + COL_PADDING;
        draw(graphics, getNameForDisplay(info), cursor, textY, teamColor | 0xFF000000);
        cursor = x + ROW_NAME_AREA;

        boolean sameTeam = Objects.equals(FPSMClient.getGlobalData().getCurrentTeam(),
                FPSMClient.getGlobalData().getTeamByUUID(uuid).map(BaseTeam::getName).orElse(""));
        drawCell(graphics, sameTeam ? "$" + FPSMClient.getGlobalData().getPlayerMoney(uuid) : "",
                cursor, y, COL_MONEY, teamColor);
        cursor += COL_MONEY;
        drawCell(graphics, String.valueOf(data.getKills()), cursor, y, COL_KILL, 0xFFE5E5E5);
        cursor += COL_KILL;
        drawCell(graphics, String.valueOf(data.getDeaths()), cursor, y, COL_DEATH, 0xFFE5E5E5);
        cursor += COL_DEATH;
        drawCell(graphics, String.valueOf(data.getAssists()), cursor, y, COL_ASSIST, 0xFFE5E5E5);
        drawCell(graphics, String.valueOf(Math.round(data.getDamage())), cursor + COL_ASSIST, y, COL_DAMAGE, 0xFFE5E5E5);
    }

    private void drawRoundTimeline(GuiGraphics graphics, int x, int y, int width) {
        int lineY = y + 21;
        graphics.fill(x, lineY, x + width, lineY + 1, 0x667E7E7E);
        graphics.fill(x + width / 2, lineY - 4, x + width / 2 + 1, lineY + 5, 0xFFA8A8A8);
        draw(graphics, Component.literal("♜"), x + width / 2 - 4, y - 1, 0xFFD4D4D4);
        draw(graphics, Component.literal("♜"), x + width / 2 - 4, y + 14, 0xFFD4D4D4);
        draw(graphics, Component.literal("5"), x + width / 4 - 3, y + 14, 0xFF9D9D9D);
        draw(graphics, Component.literal("10"), x + width / 2 - 8, y + 14, 0xFF9D9D9D);
        draw(graphics, Component.literal("15"), x + width * 3 / 4 - 8, y + 14, 0xFF9D9D9D);
    }

    private void drawCompensation(GuiGraphics graphics, int x, int y) {
        draw(graphics, Component.literal("战败补偿").withStyle(ChatFormatting.BOLD), x - 8, y, 0xFFBDBDBD);
        graphics.fill(x - 8, y + 17, x + 42, y + 19, 0xFFDFC96E);
    }

    private void blitPing(GuiGraphics graphics, int x, int y) {
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 100.0F);
        graphics.blit(GUI_ICONS_LOCATION, x, y, 0, 176, 10, 8);
        graphics.pose().popPose();
    }

    private void drawCell(GuiGraphics graphics, String text, int x, int y, int width, int color) {
        graphics.fill(x, y, x + width, y + ROW_HEIGHT, 0x24101010);
        drawCentered(graphics, text, x, y + 8, width, color);
    }

    private void drawCentered(GuiGraphics graphics, String text, int x, int y, int width, int color) {
        drawCentered(graphics, Component.literal(text), x, y, width, color);
    }

    private void drawCentered(GuiGraphics graphics, Component text, int x, int y, int width, int color) {
        draw(graphics, text, x + (width - minecraft.font.width(text)) / 2, y, color);
    }

    private void draw(GuiGraphics graphics, Component text, int x, int y, int color) {
        graphics.drawString(minecraft.font, text, x, y, color, false);
    }

    private static String formatTime(int ticks) {
        int seconds = Math.max(0, ticks / 20);
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    protected static Map<UUID, PlayerData> indexPlayerData(List<PlayerInfo> playerInfoList) {
        Map<UUID, PlayerData> map = new HashMap<>();
        for (PlayerInfo info : playerInfoList) {
            UUID id = info.getProfile().getId();
            FPSMClient.getGlobalData().getPlayerData(id).ifPresent(data -> map.put(id, data));
        }
        return map;
    }

    private static float damageOf(Map<UUID, PlayerData> dataById, PlayerInfo info) {
        PlayerData data = dataById.get(info.getProfile().getId());
        return data == null ? 0.0F : data.getDamage();
    }

    protected Component getNameForDisplay(PlayerInfo info) {
        return info.getTabListDisplayName() != null
                ? info.getTabListDisplayName() : Component.literal(info.getProfile().getName());
    }
}
