package com.ptcrys.blockoffensive.client.screen.hud;

import com.ptcrys.fpsmatch.common.client.FPSMClient;
import com.ptcrys.fpsmatch.core.data.PlayerData;
import com.ptcrys.fpsmatch.util.RenderUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CSDMTabRenderer extends CSGameTabRenderer {

    private static final int COL_KD = 35;
    private static final int COL_SCORE = 48;

    @Override
    public String getGameType() {
        return "csdm";
    }

    @Override
    public void render(GuiGraphics guiGraphics, int windowWidth, List<PlayerInfo> playerInfoList, Scoreboard scoreboard, Objective objective) {
        int playerAreaWidth = PLAYER_AREA_WIDTH;
        int playerRowHeight = ROW_HEIGHT;
        int playerGap = ROW_GAP;
        int headerHeight = HEADER_HEIGHT;
        int bgPadding = BG_PADDING;

        // 死亡竞技每个玩家各自成队(队伍名为数字)，遍历所有队伍收集玩家，避免阵营名误用 ct/t 导致列表为空
        Map<String, List<PlayerInfo>> teamPlayers = RenderUtil.getTeamsPlayerInfo(playerInfoList);
        List<PlayerInfo> allPlayers = new ArrayList<>();
        for (List<PlayerInfo> players : teamPlayers.values()) {
            allPlayers.addAll(players);
        }

        // 每帧只索引一次玩家数据，供排序与行渲染复用
        Map<UUID, PlayerData> playerDataById = indexPlayerData(playerInfoList);

        // 按得分排序
        Comparator<PlayerInfo> scoreComparator = (p1, p2) -> Integer.compare(
                scoresOf(playerDataById, p2), scoresOf(playerDataById, p1));
        allPlayers.sort(scoreComparator);

        int playerCount = allPlayers.size();
        int contentHeight = playerCount > 0 ? (playerRowHeight + playerGap) * playerCount - playerGap : 0;
        int totalContentHeight = headerHeight + contentHeight;

        // 背景（屏幕居中）
        int bgWidth = playerAreaWidth + bgPadding * 2;
        int bgHeight = totalContentHeight + bgPadding * 2;
        int bgX = (windowWidth - bgWidth) / 2;
        int bgY = (minecraft.getWindow().getGuiScaledHeight() - bgHeight) / 2;
        guiGraphics.fill(bgX, bgY, bgX + bgWidth, bgY + bgHeight, 0x80000000);

        int headerY = bgY + bgPadding;
        int playerStartY = headerY + headerHeight + 2;

        // 渲染表头
        renderHeader(guiGraphics, bgX + bgPadding, headerY);

        // 渲染所有玩家
        int currentY = playerStartY;
        for (PlayerInfo player : allPlayers) {
            renderDeathmatchPlayerRow(guiGraphics, player, bgX + bgPadding, currentY, playerAreaWidth, playerRowHeight, playerDataById);
            currentY += playerRowHeight + playerGap;
        }
    }

    private void renderHeader(GuiGraphics guiGraphics, int headerX, int headerY) {
        int currentHeaderX = headerX;

        // Ping图标（满格）
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0F, 0.0F, 100.0F);
        guiGraphics.blit(GUI_ICONS_LOCATION, currentHeaderX + (COL_PING - 10) / 2, headerY + 2, 0, 176, 10, 8);
        guiGraphics.pose().popPose();
        currentHeaderX += COL_PING;

        // 占位（头像+昵称）
        currentHeaderX += AVATAR_SIZE + COL_NAME + COL_PADDING;

        // 杀敌数
        currentHeaderX = drawHeaderLabel(guiGraphics, currentHeaderX, headerY, "blockoffensive.tab.header.kills", COL_KILL);
        // 死亡数
        currentHeaderX = drawHeaderLabel(guiGraphics, currentHeaderX, headerY, "blockoffensive.tab.header.deaths", COL_DEATH);
        // 助攻数
        currentHeaderX = drawHeaderLabel(guiGraphics, currentHeaderX, headerY, "blockoffensive.tab.header.assists", COL_ASSIST);
        // KD
        currentHeaderX = drawHeaderLabel(guiGraphics, currentHeaderX, headerY, "blockoffensive.tab.header.kd", COL_KD);
        // 爆头率
        currentHeaderX = drawHeaderLabel(guiGraphics, currentHeaderX, headerY, "blockoffensive.tab.header.headshot", COL_HEADSHOT);
        // 伤害
        currentHeaderX = drawHeaderLabel(guiGraphics, currentHeaderX, headerY, "blockoffensive.tab.header.damage", COL_DAMAGE);
        // 得分
        drawHeaderLabel(guiGraphics, currentHeaderX, headerY, "blockoffensive.tab.header.score", COL_SCORE);
    }

    /** 居中绘制表头标签并返回下一列 x。 */
    private int drawHeaderLabel(GuiGraphics guiGraphics, int x, int y, String translationKey, int columnWidth) {
        Component text = Component.translatable(translationKey);
        guiGraphics.drawString(minecraft.font, text, x + (columnWidth - minecraft.font.width(text)) / 2, y, 0xFFFFFFFF);
        return x + columnWidth;
    }

    private void renderDeathmatchPlayerRow(GuiGraphics guiGraphics, PlayerInfo player, int x, int y,
                                           int width, int height, Map<UUID, PlayerData> playerDataById) {
        PlayerData tabData = playerDataById.get(player.getProfile().getId());
        if (tabData == null) {
            return;
        }
        boolean isLocalPlayer = player.getProfile().getId().equals(minecraft.player.getUUID());

        // 背景 - 本地玩家高亮
        guiGraphics.fill(x, y, x + width, y + height, isLocalPlayer ? 0x40FFFFFF : 0x40000000);

        int textY = y + (height - 8) / 2;
        int currentX = x;

        // Ping值
        String pingText = String.valueOf(player.getLatency());
        guiGraphics.drawString(minecraft.font, pingText,
                currentX + (COL_PING - minecraft.font.width(pingText)) / 2, textY, RenderUtil.color(25, 180, 60));
        currentX += COL_PING;

        // 头像
        PlayerFaceRenderer.draw(guiGraphics, player.getSkinLocation(), currentX, y, AVATAR_SIZE);
        currentX += AVATAR_SIZE + COL_PADDING;

        // 玩家名（左对齐）
        guiGraphics.drawString(minecraft.font, getNameForDisplay(player), currentX, textY, 0xFFFFFFFF);

        // 数据列起点（表头对齐）
        int dataX = x + ROW_NAME_AREA;
        dataX = drawCenteredCell(guiGraphics, String.valueOf(tabData.getKills()), dataX, COL_KILL, textY);
        dataX = drawCenteredCell(guiGraphics, String.valueOf(tabData.getDeaths()), dataX, COL_DEATH, textY);
        dataX = drawCenteredCell(guiGraphics, String.valueOf(tabData.getAssists()), dataX, COL_ASSIST, textY);
        dataX = drawCenteredCell(guiGraphics, String.format("%.2f", tabData.getKD()), dataX, COL_KD, textY);

        // 爆头率
        String headshotStr = tabData.getHeadshotRate() > 0 ? ((int) (tabData.getHeadshotRate() * 100)) + "%" : "0%";
        dataX = drawCenteredCell(guiGraphics, headshotStr, dataX, COL_HEADSHOT, textY);

        dataX = drawCenteredCell(guiGraphics, String.valueOf(Math.round(tabData.getDamage())), dataX, COL_DAMAGE, textY);

        // 得分（右对齐钉在行尾）
        drawCenteredCell(guiGraphics, String.valueOf(tabData.getScores()), x + width - COL_SCORE, COL_SCORE, textY);

        if (!tabData.isLiving()) {
            // 渲染一层半透明灰色
            guiGraphics.fill(x, y, x + width, y + height, 0x40000000);
        }
    }

    private int drawCenteredCell(GuiGraphics guiGraphics, String text, int x, int columnWidth, int textY) {
        guiGraphics.drawString(minecraft.font, text,
                x + (columnWidth - minecraft.font.width(text)) / 2, textY, 0xFFFFFFFF);
        return x + columnWidth;
    }

    private static int scoresOf(Map<UUID, PlayerData> playerDataById, PlayerInfo info) {
        PlayerData pd = playerDataById.get(info.getProfile().getId());
        return pd == null ? 0 : pd.getScores();
    }
}