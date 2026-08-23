package com.ptcrys.blockoffensive.client.screen.hud;

import com.ptcrys.fpsmatch.common.client.FPSMClient;
import com.ptcrys.fpsmatch.core.data.PlayerData;
import com.ptcrys.fpsmatch.util.RenderUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class CSDMTabRenderer extends CSGameTabRenderer {

    @Override
    public String getGameType() {
        return "csdm";
    }

    @Override
    public void render(GuiGraphics guiGraphics, int windowWidth, List<PlayerInfo> playerInfoList, Scoreboard scoreboard, Objective objective) {
        // 列宽定义
        int padding = 5;
        int pingWidth = 40;
        int avatarSize = 12;
        int nameWidth = 110;
        int killsWidth = 35;
        int deathsWidth = 35;
        int assistsWidth = 35;
        int kdWidth = 35;
        int headshotWidth = 40;
        int damageWidth = 48;
        int scoreWidth = 48;

        // 玩家信息区域固定尺寸
        int playerAreaWidth = 400;
        int playerRowHeight = 12;
        int playerGap = 2;
        int headerHeight = 12;

        // 过滤并排序玩家
        Map<String, List<PlayerInfo>> teamPlayers = RenderUtil.getTeamsPlayerInfo(playerInfoList);
        List<PlayerInfo> allPlayers = new ArrayList<>(teamPlayers.getOrDefault("ct", List.of()));
        allPlayers.addAll(teamPlayers.getOrDefault("t", List.of()));

        // 按得分排序
        Comparator<PlayerInfo> scoreComparator = (p1, p2) -> {
            int t1 = FPSMClient.getGlobalData().getPlayerData(p1.getProfile().getId())
                    .map(PlayerData::getScores).orElse(0);
            int t2 = FPSMClient.getGlobalData().getPlayerData(p2.getProfile().getId())
                    .map(PlayerData::getScores).orElse(0);
            return Integer.compare(t2, t1);
        };

        allPlayers.sort(scoreComparator);

        // 计算实际玩家数量
        int playerCount = allPlayers.size();

        // 计算内容高度
        int contentHeight = playerCount > 0 ? (playerRowHeight + playerGap) * playerCount - playerGap : 0;

        // 计算总内容高度
        int totalContentHeight = headerHeight + contentHeight;

        // 背景尺寸（玩家信息栏+边距）
        int bgPadding = 10;
        int bgWidth = playerAreaWidth + bgPadding * 2;
        int bgHeight = totalContentHeight + bgPadding * 2;

        // 背景位置（屏幕居中）
        int bgX = (windowWidth - bgWidth) / 2;
        int bgY = (minecraft.getWindow().getGuiScaledHeight() - bgHeight) / 2;

        // 渲染背景
        guiGraphics.fill(bgX, bgY, bgX + bgWidth, bgY + bgHeight, 0x80000000);

        // 表头位置
        int headerY = bgY + bgPadding;

        // 计算玩家起始Y坐标（表头下方）
        int playerStartY = headerY + headerHeight + 2;

        // 渲染表头
        int currentHeaderX = bgX + bgPadding;

        // Ping图标（满格）
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0F, 0.0F, 100.0F);
        guiGraphics.blit(GUI_ICONS_LOCATION, currentHeaderX + (pingWidth - 10) / 2, headerY + 2, 0, 176, 10, 8);
        guiGraphics.pose().popPose();
        currentHeaderX += pingWidth;

        // 占位
        currentHeaderX += avatarSize + nameWidth + padding;

        // 杀敌数
        Component killsText = Component.translatable("blockoffensive.tab.header.kills").withStyle(ChatFormatting.BOLD);
        guiGraphics.drawString(minecraft.font, killsText,
                currentHeaderX + (killsWidth - minecraft.font.width(killsText)) / 2, headerY, 0xFFFFFFFF);
        currentHeaderX += killsWidth;

        // 死亡数
        Component deathsText = Component.translatable("blockoffensive.tab.header.deaths").withStyle(ChatFormatting.BOLD);
        guiGraphics.drawString(minecraft.font, deathsText,
                currentHeaderX + (deathsWidth - minecraft.font.width(deathsText)) / 2, headerY, 0xFFFFFFFF);
        currentHeaderX += deathsWidth;

        // 助攻数
        Component assistsText = Component.translatable("blockoffensive.tab.header.assists").withStyle(ChatFormatting.BOLD);
        guiGraphics.drawString(minecraft.font, assistsText,
                currentHeaderX + (assistsWidth - minecraft.font.width(assistsText)) / 2, headerY, 0xFFFFFFFF);
        currentHeaderX += assistsWidth;

        // KD
        Component kdText = Component.translatable("blockoffensive.tab.header.kd").withStyle(ChatFormatting.BOLD);
        guiGraphics.drawString(minecraft.font, kdText,
                currentHeaderX + (kdWidth - minecraft.font.width(kdText)) / 2, headerY, 0xFFFFFFFF);
        currentHeaderX += kdWidth;

        // 爆头率
        Component headshotText = Component.translatable("blockoffensive.tab.header.headshot").withStyle(ChatFormatting.BOLD);
        guiGraphics.drawString(minecraft.font, headshotText,
                currentHeaderX + (headshotWidth - minecraft.font.width(headshotText)) / 2, headerY, 0xFFFFFFFF);
        currentHeaderX += headshotWidth;

        // 伤害
        Component damageText = Component.translatable("blockoffensive.tab.header.damage").withStyle(ChatFormatting.BOLD);
        guiGraphics.drawString(minecraft.font, damageText,
                currentHeaderX + (damageWidth - minecraft.font.width(damageText)) / 2, headerY, 0xFFFFFFFF);
        currentHeaderX += damageWidth;

        // 得分
        Component scoreText = Component.translatable("blockoffensive.tab.header.score").withStyle(ChatFormatting.BOLD);
        guiGraphics.drawString(minecraft.font, scoreText,
                currentHeaderX + (scoreWidth - minecraft.font.width(scoreText)) / 2, headerY, 0xFFFFFFFF);

        // 渲染所有玩家
        int currentY = playerStartY;
        for (PlayerInfo player : allPlayers) {
            renderDeathmatchPlayerRow(guiGraphics, player, bgX + bgPadding, currentY, playerAreaWidth, playerRowHeight);
            currentY += playerRowHeight + playerGap;
        }
    }

    private void renderDeathmatchPlayerRow(GuiGraphics guiGraphics, PlayerInfo player, int x, int y, int width, int height) {
        PlayerData tabData = FPSMClient.getGlobalData().getPlayerData(player.getProfile().getId()).orElse(null);
        if (tabData == null) {
            return;
        }
        boolean isLocalPlayer = player.getProfile().getId().equals(minecraft.player.getUUID());

        // 背景 - 如果是本地玩家，使用高亮背景
        int bgColor = isLocalPlayer ? 0x40FFFFFF : 0x40000000;
        guiGraphics.fill(x, y, x + width, y + height, bgColor);

        // 列宽定义
        int padding = 5;
        int pingWidth = 40;
        int avatarSize = 12;
        int nameWidth = 110;
        int killsWidth = 35;
        int deathsWidth = 35;
        int assistsWidth = 35;
        int kdWidth = 35;
        int headshotWidth = 40;
        int damageWidth = 48;
        int scoreWidth = 48;

        int textY = y + (height - 8) / 2;
        int currentX = x;

        // Ping值
        String pingText = String.valueOf(player.getLatency());
        guiGraphics.drawString(minecraft.font, pingText,
                currentX + (pingWidth - minecraft.font.width(pingText)) / 2, textY, RenderUtil.color(25,180,60));
        currentX += pingWidth;

        // 头像
        PlayerFaceRenderer.draw(guiGraphics, player.getSkinLocation(), currentX, y, avatarSize);
        currentX += avatarSize + padding;

        // 玩家名（左对齐）
        guiGraphics.drawString(minecraft.font, getNameForDisplay(player), currentX, textY, 0xFFFFFFFF);

        // 杀敌数
        int killsX = x + pingWidth + avatarSize + padding + nameWidth;
        String kills = String.valueOf(tabData.getKills());
        guiGraphics.drawString(minecraft.font, kills,
                killsX + (killsWidth - minecraft.font.width(kills)) / 2, textY, 0xFFFFFFFF);

        // 死亡数
        int deathsX = killsX + killsWidth;
        String deaths = String.valueOf(tabData.getDeaths());
        guiGraphics.drawString(minecraft.font, deaths,
                deathsX + (deathsWidth - minecraft.font.width(deaths)) / 2, textY, 0xFFFFFFFF);

        // 助攻数
        int assistsX = deathsX + deathsWidth;
        String assists = String.valueOf(tabData.getAssists());
        guiGraphics.drawString(minecraft.font, assists,
                assistsX + (assistsWidth - minecraft.font.width(assists)) / 2, textY, 0xFFFFFFFF);

        // KD
        int kdX = assistsX + assistsWidth;
        float kd = tabData.getKD();
        String kdStr = String.format("%.2f", kd);
        guiGraphics.drawString(minecraft.font, kdStr,
                kdX + (kdWidth - minecraft.font.width(kdStr)) / 2, textY, 0xFFFFFFFF);

        // 爆头率
        int headshotX = kdX + kdWidth;
        float headshotRate = tabData.getHeadshotRate();
        String headshotPercentage = headshotRate > 0
                ? String.format("%.0f%%", headshotRate * 100)
                : "0%";
        guiGraphics.drawString(minecraft.font, headshotPercentage,
                headshotX + (headshotWidth - minecraft.font.width(headshotPercentage)) / 2, textY, 0xFFFFFFFF);

        // 伤害
        int damageX = headshotX + headshotWidth;
        String damage = String.valueOf(Math.round(tabData.getDamage()));
        guiGraphics.drawString(minecraft.font, damage,
                damageX + (damageWidth - minecraft.font.width(damage)) / 2, textY, 0xFFFFFFFF);

        // 得分
        int scoreX = x + width - scoreWidth;
        String score = String.valueOf(tabData.getScores());
        guiGraphics.drawString(minecraft.font, score,
                scoreX + (scoreWidth - minecraft.font.width(score)) / 2, textY, 0xFFFFFFFF);

        if(!tabData.isLiving()){
            //渲染一层半透明灰色
            guiGraphics.fill(x, y, x + width, y + height, 0x40000000);
        }
    }
}
