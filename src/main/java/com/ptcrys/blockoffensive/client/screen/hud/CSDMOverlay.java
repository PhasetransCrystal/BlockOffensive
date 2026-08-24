package com.ptcrys.blockoffensive.client.screen.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.ptcrys.blockoffensive.client.data.CSClientData;
import com.ptcrys.blockoffensive.util.BOUtil;
import com.ptcrys.fpsmatch.core.data.PlayerData;
import com.ptcrys.fpsmatch.util.RenderUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.*;
import java.util.Comparator;

import static com.ptcrys.fpsmatch.util.RenderUtil.color;

public class CSDMOverlay {
    public static int textRoundTimeColor = color(255, 255, 255);
    public static int textFontColor = color(138, 118, 110);

    private final Minecraft minecraft = Minecraft.getInstance();

    public void render(GuiGraphics guiGraphics, int screenWidth, int screenHeight) {
        Font font = minecraft.font;
        if (minecraft.player == null) return;

        // 计算缩放因子 (以855x480为基准)
        float scaleFactor = Math.min(screenWidth / 855.0f, screenHeight / 480.0f);

        int centerX = screenWidth / 2;
        int startY = (int) (2 * scaleFactor);
        int timeBarHeight = (int) (13 * scaleFactor);
        int avatarSize = (int) (24.0F * scaleFactor);
        int avatarGap = (int) (16 * scaleFactor);
        int scoreBgHeight = (int) (8 * scaleFactor);

        // 渲染时间计数器
        renderTimeCounter(guiGraphics, font, centerX, startY, timeBarHeight, scaleFactor);

        // 渲染玩家头像和分数
        renderPlayerAvatars(guiGraphics, font, centerX, startY + timeBarHeight + 2, avatarSize, avatarGap, scoreBgHeight, scaleFactor);
    }

    private void renderTimeCounter(GuiGraphics guiGraphics, Font font, int centerX, int startY, int timeBarHeight, float scaleFactor) {
        // 渲染中间时间区域背景
        int timeAreaWidth = (int) (20 * scaleFactor);
        guiGraphics.fillGradient(centerX - timeAreaWidth, startY, centerX + timeAreaWidth, startY + timeBarHeight, -1072689136, -804253680);

        // 渲染时间
        Component roundTime = getRoundTimeString();
        float timeScale = scaleFactor * 1.2f;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(centerX, startY + (float) timeBarHeight / 2, 0);
        guiGraphics.pose().scale(timeScale, timeScale, 1.0f);
        guiGraphics.drawString(font, roundTime,
                -font.width(roundTime) / 2,
                -4,
                textRoundTimeColor,
                false);
        guiGraphics.pose().popPose();
    }

    private void renderPlayerAvatars(GuiGraphics guiGraphics, Font font, int centerX, int startY, int avatarSize, int avatarGap, int scoreBgHeight, float scaleFactor) {
        // 获取所有玩家信息
        Map<String, List<PlayerInfo>> teamPlayers = RenderUtil.getTeamsPlayerInfo();
        List<PlayerInfo> allPlayers = new ArrayList<>();
        for (List<PlayerInfo> players : teamPlayers.values()) {
            allPlayers.addAll(players);
        }

        // 按分数从高到低排序
        allPlayers.sort(Comparator.comparingInt(this::getPlayerScore).reversed());

        // 最多显示15个玩家
        int maxPlayers = Math.min(15, allPlayers.size());
        List<PlayerInfo> topPlayers = allPlayers.subList(0, maxPlayers);

        // 计算总宽度
        int totalWidth = topPlayers.size() * avatarSize + (topPlayers.size() - 1) * avatarGap;
        int startX = centerX - totalWidth / 2;

        // 渲染玩家头像和分数
        for (int i = 0; i < topPlayers.size(); i++) {
            PlayerInfo player = topPlayers.get(i);
            Optional<PlayerData> playerData = RenderUtil.getPlayerData(player);
            if(playerData.isEmpty()) continue;
            PlayerData data = playerData.get();
            int drawX = startX + i * (avatarSize + avatarGap);

            // 渲染玩家头像
            renderPlayerAvatar(guiGraphics, player, data, drawX, startY, avatarSize);

            // 渲染分数背景
            guiGraphics.fillGradient(drawX, startY + avatarSize, drawX + avatarSize, startY + avatarSize + scoreBgHeight, -1072689136, -804253680);

            // 渲染分数
            int playerScore = data.getScores();
            Component scoreText = Component.literal(String.valueOf(playerScore)).withStyle(ChatFormatting.BOLD);
            float scoreScale = scaleFactor * 0.8f;

            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(drawX + (float) avatarSize / 2, startY + avatarSize + (float) scoreBgHeight / 2, 0);
            guiGraphics.pose().scale(scoreScale, scoreScale, 1.0f);
            guiGraphics.drawString(font, scoreText,
                    -font.width(scoreText) / 2,
                    -font.lineHeight / 2,
                    textFontColor,
                    false);
            guiGraphics.pose().popPose();
        }
    }

    private void renderPlayerAvatar(GuiGraphics guiGraphics, PlayerInfo player, PlayerData data, int x, int y, int size) {
        // 灰度头像(dead)
        float r = 1f, g = 1f, b = 1f, a = 1f;
        if (!data.isLiving()) {
            r = g = b = 0.3f;
        }
        RenderSystem.setShaderColor(r, g, b, a);

        int margin = 1;
        int avX = x + margin;
        int avY = y + margin;
        int smallAvSize = size - margin * 2;

        PlayerFaceRenderer.draw(guiGraphics, player.getSkinLocation(), avX, avY, smallAvSize);

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private int getPlayerScore(PlayerInfo player) {
        return RenderUtil.getPlayerData(player).map(PlayerData::getScores).orElse(0);
    }

    private Component getRoundTimeString() {
        if (CSClientData.time == -1 && !CSClientData.isWaitingWinner) {
            return Component.translatable("blockoffensive.hud.time_placeholder").withStyle(net.minecraft.ChatFormatting.BOLD);
        }
        return getCSGameTime();
    }

    public static Component getCSGameTime() {
        return Component.literal(formatTime(CSClientData.time / 20)).withStyle(net.minecraft.ChatFormatting.BOLD);
    }

    public static String formatTime(int totalSeconds) {
        if (totalSeconds / 60 == 0 && totalSeconds % 60 <= 10) {
            textRoundTimeColor = color(240, 40, 40);
        } else {
            textRoundTimeColor = color(255, 255, 255);
        }

        return BOUtil.formatMinutesSeconds(totalSeconds);
    }
}