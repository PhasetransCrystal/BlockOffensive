package com.ptcrys.blockoffensive.client.screen.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.ptcrys.blockoffensive.client.data.CSClientData;
import com.ptcrys.fpsmatch.common.client.FPSMClient;
import com.ptcrys.fpsmatch.core.data.PlayerData;
import com.ptcrys.fpsmatch.util.RenderUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.*;
import java.util.Comparator;

import static com.ptcrys.fpsmatch.util.RenderUtil.color;

public class CSDMOverlay {
    public static int textRoundTimeColor = color(255, 255, 255);
    public static int textFontColor = color(138, 118, 110);

    private final Minecraft minecraft = Minecraft.getInstance();

    public void render(GuiGraphics guiGraphics, int screenWidth, int screenHeight) {
        int players = RenderUtil.getTeamsPlayerInfo().values().stream().mapToInt(List::size).sum();
        render(guiGraphics, CSHudSafeAreaLayouts.csdmScoreboard(screenWidth, screenHeight, players));
    }

    public void render(GuiGraphics guiGraphics, CSHudSafeAreaLayouts.CsdmScoreboardLayout layout) {
        Font font = minecraft.font;
        if (minecraft.player == null) return;

        renderTimeCounter(guiGraphics, font, layout);

        renderPlayerAvatars(guiGraphics, font, layout);
    }

    private void renderTimeCounter(
            GuiGraphics guiGraphics,
            Font font,
            CSHudSafeAreaLayouts.CsdmScoreboardLayout layout
    ) {
        int centerX = layout.centerX();
        int startY = layout.startY();
        int timeBarHeight = layout.timeBarHeight();
        float scaleFactor = layout.scale();
        int timeAreaWidth = layout.timeAreaWidth();
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

        renderMatchStatus(guiGraphics, font, layout);
    }

    private void renderMatchStatus(
            GuiGraphics guiGraphics,
            Font font,
            CSHudSafeAreaLayouts.CsdmScoreboardLayout layout
    ) {
        LocalPlayer local = minecraft.player;
        if (local == null) return;

        Component status = respawnProtectionText();
        if (status == null) {
            List<PlayerInfo> ranked = rankedPlayers();
            int selfIndex = -1;
            int leaderScore = 0;
            int ownScore = 0;
            for (int i = 0; i < ranked.size(); i++) {
                PlayerInfo player = ranked.get(i);
                int score = getPlayerScore(player);
                if (i == 0) leaderScore = score;
                if (player.getProfile().getId().equals(local.getUUID())) {
                    selfIndex = i;
                    ownScore = score;
                }
            }
            if (selfIndex < 0) return;
            int deficit = Math.max(0, leaderScore - ownScore);
            status = Component.translatable("blockoffensive.hud.csdm.standing",
                    selfIndex + 1, ownScore, deficit);
        }

        float scale = Math.max(0.65F, layout.scale() * 0.72F);
        int x = layout.centerX();
        int y = layout.startY() + layout.timeBarHeight() - font.lineHeight - 1;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x, y, 0);
        guiGraphics.pose().scale(scale, scale, 1.0F);
        guiGraphics.drawString(font, status, -font.width(status) / 2, 0,
                respawnProtectionText() != null ? 0xFF8EE6A1 : 0xFFE7EDF2, false);
        guiGraphics.pose().popPose();
    }

    private Component respawnProtectionText() {
        if (!CSClientData.canOpenShop || CSClientData.shopCloseTime <= 0) return null;
        return Component.translatable("blockoffensive.hud.csdm.spawn_protection", CSClientData.shopCloseTime);
    }

    private void renderPlayerAvatars(
            GuiGraphics guiGraphics,
            Font font,
            CSHudSafeAreaLayouts.CsdmScoreboardLayout layout
    ) {
        float scaleFactor = layout.scale();
        int avatarSize = layout.avatars().avatarSize();
        int scoreBgHeight = layout.scoreBgHeight();
        // 获取所有玩家信息
        List<PlayerInfo> allPlayers = rankedPlayers();

        // 最多显示15个玩家
        int maxPlayers = Math.min(layout.avatars().count(), allPlayers.size());
        List<PlayerInfo> topPlayers = allPlayers.subList(0, maxPlayers);

        // 渲染玩家头像和分数
        for (int i = 0; i < topPlayers.size(); i++) {
            PlayerInfo player = topPlayers.get(i);
            Optional<PlayerData> playerData = RenderUtil.getPlayerData(player);
            if(playerData.isEmpty()) continue;
            PlayerData data = playerData.get();
            int drawX = layout.avatars().xAt(i);
            int startY = layout.avatars().y();

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

    private List<PlayerInfo> rankedPlayers() {
        List<PlayerInfo> allPlayers = new ArrayList<>();
        RenderUtil.getTeamsPlayerInfo().values().forEach(allPlayers::addAll);
        allPlayers.sort(Comparator.comparingInt(this::getPlayerScore).reversed()
                .thenComparing(player -> player.getProfile().getName(), String.CASE_INSENSITIVE_ORDER));
        return allPlayers;
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
        int remainingMinutes = totalSeconds / 60;
        int remainingSecondsPart = totalSeconds % 60;

        if (remainingMinutes == 0 && remainingSecondsPart <= 10) {
            textRoundTimeColor = color(240, 40, 40);
        } else {
            textRoundTimeColor = color(255, 255, 255);
        }

        String minutesPart = String.format("%02d", remainingMinutes);
        String secondsPart = String.format("%02d", remainingSecondsPart);

        return minutesPart + ":" + secondsPart;
    }
}
