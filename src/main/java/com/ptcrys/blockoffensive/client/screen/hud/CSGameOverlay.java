package com.ptcrys.blockoffensive.client.screen.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.ptcrys.blockoffensive.client.data.CSClientData;
import com.ptcrys.blockoffensive.util.BOUtil;
import com.ptcrys.fpsmatch.common.client.FPSMClient;
import com.ptcrys.fpsmatch.common.client.data.FPSMClientGlobalData;
import com.ptcrys.fpsmatch.core.data.PlayerData;
import com.ptcrys.fpsmatch.util.RenderUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.*;

import static com.ptcrys.fpsmatch.util.RenderUtil.color;

public class CSGameOverlay {
    public static int noColor = color(0,0,0,0);
    public static int textRoundTimeColor = color(255,255,255);

    // 每帧绘制复用的静态文案（参数不变，避免每帧重建 Component）
    private static final Component LIVING_TEXT = Component.translatable("blockoffensive.hud.living").withStyle(ChatFormatting.BOLD);
    private static final Component TIME_PLACEHOLDER = Component.translatable("blockoffensive.hud.time_placeholder").withStyle(ChatFormatting.BOLD);

    private final Map<UUID,String> cachedName = new HashMap<>();

    public void render(GuiGraphics guiGraphics, int screenWidth, int screenHeight) {
        Font font = Minecraft.getInstance().font;
        FPSMClientGlobalData data = FPSMClient.getGlobalData();
        // 计算缩放因子 (以855x480为基准)
        float scaleFactor = Math.min(screenWidth / 855.0f, screenHeight / 480.0f);

        int centerX = screenWidth / 2;
        int startY = (int)(2 * scaleFactor);
        int backgroundHeight = (int)(35 * scaleFactor);
        int timeBarHeight = (int)(13 * scaleFactor);
        int scoreBarHeight = (int)(19 * scaleFactor);
        int boxWidth = (int)(24 * scaleFactor);

        // 计算各种间距
        int gap = (int)(2 * scaleFactor); // 统一的2px间距
        int timeAreaWidth = (int)(20 * scaleFactor); // 16 * 1.25 = 20

        // 计算存活栏位置
        int ctBoxX = centerX - timeAreaWidth - gap - boxWidth; // 左侧存活栏
        int tBoxX = centerX + timeAreaWidth + gap; // 右侧存活栏

        // 渲染中间时间区域背景 (扩大1.25倍)
        guiGraphics.fillGradient(centerX - timeAreaWidth, startY, centerX + timeAreaWidth, startY + timeBarHeight, -1072689136, -804253680);

        // 分数栏背景
        guiGraphics.fillGradient(centerX - timeAreaWidth, startY + timeBarHeight + gap, // 只间隔2px
                centerX - gap/2, startY + backgroundHeight, -1072689136, noColor);
        guiGraphics.fillGradient(centerX + gap/2, startY + timeBarHeight + gap,
                centerX + timeAreaWidth, startY + backgroundHeight, -1072689136, noColor);

        // 渲染CT存活信息（左侧）
        int ctLivingCount = data.getLivingWithTeam("ct");
        Component ctLivingStr = Component.literal(String.valueOf(ctLivingCount)).withStyle(ChatFormatting.BOLD);

        // CT背景渐变
        int gradientStartY = (int)(startY + timeBarHeight + scaleFactor);
        // 上半部分
        guiGraphics.fillGradient(
                ctBoxX,
                startY,
                ctBoxX + boxWidth,
                startY + timeBarHeight + (int)scaleFactor, // 增加1px高度
                -1072689136,
                -1072689136
        );
        // 下半部分渐变
        guiGraphics.fillGradient(
                ctBoxX,
                gradientStartY,
                ctBoxX + boxWidth,
                startY + backgroundHeight,
                -1072689136,
                noColor
        );

        // CT存活数字
        guiGraphics.pose().pushPose();
        float numberScale = scaleFactor * 1.5f;
        guiGraphics.pose().translate(
                ctBoxX + (float) boxWidth /2,
                startY + (float) backgroundHeight /2 - 6 * scaleFactor, // 从-2改为-6，向上移动4px
                0
        );
        guiGraphics.pose().scale(numberScale, numberScale, 1.0f);
        int ctNumberWidth = font.width(ctLivingStr);
        guiGraphics.drawString(font, ctLivingStr,
                -ctNumberWidth/2,
                -4,
                BOUtil.CT_COLOR,
                false);
        guiGraphics.pose().popPose();

        // CT "存活" 文字
        float smallScale = numberScale * 0.5f; // 恢复为数字大小的一半
        Component livingText = LIVING_TEXT;
        int smallTextWidth = font.width(livingText);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(
                ctBoxX + (float) boxWidth /2,
                startY + (float) backgroundHeight /2 + 2 * scaleFactor,
                0
        );
        guiGraphics.pose().scale(smallScale, smallScale, 1.0f); // 使用smallScale
        guiGraphics.drawString(font, livingText,
                -smallTextWidth/2, // 使用smallTextWidth
                0,
                BOUtil.CT_COLOR,
                false);
        guiGraphics.pose().popPose();

        // 渲染T存活信息（右侧）
        int tLivingCount = data.getLivingWithTeam("t");
        Component tLivingStr = Component.literal(String.valueOf(tLivingCount)).withStyle(ChatFormatting.BOLD);

        // T背景渐变
        // 上半部分
        guiGraphics.fillGradient(
                tBoxX,
                startY,
                tBoxX + boxWidth,
                startY + timeBarHeight + (int)scaleFactor, // 增加1px高度
                -1072689136,
                -1072689136
        );
        // 下半部分渐变
        guiGraphics.fillGradient(
                tBoxX,
                gradientStartY,
                tBoxX + boxWidth,
                startY + backgroundHeight,
                -1072689136,
                noColor
        );

        // T存活数字
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(
                tBoxX + (float) boxWidth /2,
                startY + (float) backgroundHeight /2 - 6 * scaleFactor, // 从-2改为-6
                0
        );
        guiGraphics.pose().scale(numberScale, numberScale, 1.0f);
        int tNumberWidth = font.width(tLivingStr);
        guiGraphics.drawString(font, tLivingStr,
                -tNumberWidth/2,
                -4,
                BOUtil.T_COLOR,
                false);
        guiGraphics.pose().popPose();

        // T "存活" 文字
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(
                tBoxX + (float) boxWidth /2,
                startY + (float) backgroundHeight /2 + 2 * scaleFactor,
                0
        );
        guiGraphics.pose().scale(smallScale, smallScale, 1.0f); // 使用smallScale
        guiGraphics.drawString(font, livingText,
                -smallTextWidth/2, // 使用smallTextWidth
                0,
                BOUtil.T_COLOR,
                false);
        guiGraphics.pose().popPose();

        // 渲染时间
        Component roundTime = getRoundTimeString();
        float timeScale = scaleFactor * 1.2f;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(centerX, startY + (float) timeBarHeight /2, 0);
        guiGraphics.pose().scale(timeScale, timeScale, 1.0f);
        guiGraphics.drawString(font, roundTime,
                -font.width(roundTime) / 2,
                -4,
                textRoundTimeColor,
                false);
        guiGraphics.pose().popPose();

        // 渲染比分
        float scoreScale = scaleFactor * 1.2f;

        // CT比分
        Component ctScore = Component.literal(String.valueOf(CSClientData.cTWinnerRounds)).withStyle(ChatFormatting.BOLD);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(
                centerX - (float) timeAreaWidth /2 - scaleFactor, // 左侧分数栏中心，向左偏移1px
                startY + timeBarHeight + gap + (float) scoreBarHeight /2,
                0
        );
        guiGraphics.pose().scale(scoreScale, scoreScale, 1.0f);
        int ctScoreWidth = font.width(ctScore);
        guiGraphics.drawString(font, ctScore,
                -ctScoreWidth/2,
                -font.lineHeight/2,
                BOUtil.CT_COLOR,
                false);
        guiGraphics.pose().popPose();

        // T比分
        Component tScore = Component.literal(String.valueOf(CSClientData.tWinnerRounds)).withStyle(ChatFormatting.BOLD);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(
                centerX + (float) timeAreaWidth /2 + scaleFactor, // 右侧分数栏中心，向右偏移1px
                startY + timeBarHeight + gap + (float) scoreBarHeight /2,
                0
        );
        guiGraphics.pose().scale(scoreScale, scoreScale, 1.0f);
        int tScoreWidth = font.width(tScore);
        guiGraphics.drawString(font, tScore,
                -tScoreWidth/2,
                -font.lineHeight/2,
                BOUtil.T_COLOR,
                false);
        guiGraphics.pose().popPose();

        if(CSClientData.dismantleBombProgress > 0) {
            renderDemolitionProgress(guiGraphics,screenWidth,screenHeight);
        }

        this.renderMoneyText(guiGraphics, screenHeight);

        int avatarSize = (int)(24.0F * scaleFactor);
        int avatarGap  = (int)(3 * scaleFactor);
        int offset     = (int)(26.0F * scaleFactor);

        Map<String, List<PlayerInfo>> teamPlayers = RenderUtil.getTeamsPlayerInfo();


        boolean showInfo = CSClientData.isWaiting;

        if(teamPlayers.containsKey("ct")) {
            renderAvatarRow(guiGraphics, teamPlayers.get("ct"),
                    ctBoxX - offset, startY, boxWidth,
                    avatarSize, avatarGap, true,showInfo,
                    "ct",scaleFactor);
        }

        if(teamPlayers.containsKey("t")) {
            renderAvatarRow(guiGraphics, teamPlayers.get("t"),
                    tBoxX + offset, startY, boxWidth,
                    avatarSize, avatarGap, false,showInfo,
                    "t",scaleFactor);
        }
    }

    private Component getRoundTimeString() {
        if(CSClientData.time == -1 && !CSClientData.isWaitingWinner) {
            return TIME_PLACEHOLDER;
        }
        return getCSGameTime();
    }

    private void renderDemolitionProgress(GuiGraphics guiGraphics, int screenWidth, int screenHeight) {
        float progress = CSClientData.dismantleBombProgress;

        int progressBarWidth = 150;
        int progressBarHeight = 6;
        int progressBarX = screenWidth / 2 - progressBarWidth / 2;
        int progressBarY = (int) (screenHeight / 2F + 90);

        drawRoundedRect(guiGraphics, progressBarX, progressBarY, progressBarWidth, progressBarHeight, 0xFF2D2D2D, 3);

        if (progress > 0) {
            int progressWidth = (int) (progressBarWidth * progress);

            int color;
            if (progress < 0.5f) {
                float ratio = progress * 2;
                int red = 255;
                int green = (int) (165 * ratio);
                color = (0xFF << 24) | (red << 16) | (green << 8);
            } else {
                float ratio = (progress - 0.5f) * 2;
                int red = 255 - (int) (255 * ratio);
                int green = 165 + (int) (90 * ratio);
                color = (0xFF << 24) | (red << 16) | (green << 8);
            }

            drawRoundedRect(guiGraphics, progressBarX, progressBarY, progressWidth, progressBarHeight, color, 3);
        }

        guiGraphics.fill(progressBarX, progressBarY, progressBarX + progressBarWidth, progressBarY + 1, 0x66FFFFFF);
        guiGraphics.fill(progressBarX, progressBarY + 1, progressBarX + 1, progressBarY + progressBarHeight - 1, 0x66FFFFFF);

        guiGraphics.fill(progressBarX + progressBarWidth - 1, progressBarY + 1, progressBarX + progressBarWidth, progressBarY + progressBarHeight, 0x66000000);
        guiGraphics.fill(progressBarX + 1, progressBarY + progressBarHeight - 1, progressBarX + progressBarWidth, progressBarY + progressBarHeight, 0x66000000);
    }

    private void drawRoundedRect(GuiGraphics guiGraphics, int x, int y, int width, int height, int color, int cornerRadius) {
        guiGraphics.fill(x + cornerRadius, y, x + width - cornerRadius, y + height, color);
        guiGraphics.fill(x, y + cornerRadius, x + width, y + height - cornerRadius, color);

        guiGraphics.fill(x + cornerRadius, y + cornerRadius, x + width - cornerRadius, y + height - cornerRadius, color);

        if (cornerRadius > 0) {
            guiGraphics.fill(x, y, x + cornerRadius, y + cornerRadius, color);
            guiGraphics.fill(x + width - cornerRadius, y, x + width, y + cornerRadius, color);
            guiGraphics.fill(x, y + height - cornerRadius, x + cornerRadius, y + height, color);
            guiGraphics.fill(x + width - cornerRadius, y + height - cornerRadius, x + width, y + height, color);
        }
    }

    private void renderMoneyText(GuiGraphics guiGraphics, int screenHeight) {
        Font font = Minecraft.getInstance().font;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(5,screenHeight - 20,0 );
        guiGraphics.pose().scale(2,2,0);
        guiGraphics.drawString(font, "$ "+CSClientData.getMoney(), 0,0, FPSMClient.getGlobalData().isCurrentTeam("ct") ? BOUtil.CT_COLOR : BOUtil.T_COLOR);
        guiGraphics.pose().popPose();
    }

    private void renderAvatarRow(GuiGraphics guiGraphics,
                                 List<PlayerInfo> players,
                                 int boxStartX,
                                 int rowY,
                                 int boxWidth,
                                 int avatarSize,
                                 int gap,
                                 boolean leftSide,
                                 boolean showNameInfo,
                                 String rowTeam,
                                 float scaleFactor
    )
    {
        boolean isSameTeam = FPSMClient.getGlobalData().isCurrentTeam(rowTeam);
        boolean isCT = rowTeam.equals("ct");
        if (showNameInfo) {
            rowY += 6;
        }
        Font font = Minecraft.getInstance().font;
        for (int i=0; i<players.size(); i++) {
            PlayerInfo player = players.get(i);
            UUID uuid = player.getProfile().getId();
            int drawX = leftSide
                    ? (boxStartX + boxWidth - avatarSize - 2 - i*(avatarSize+gap))
                    : (boxStartX + 2 + i*(avatarSize+gap));

            Optional<PlayerData> data = RenderUtil.getPlayerData(player);
            boolean checked = data.isPresent();

            float barRatio = (!isSameTeam) ? 0f : checked ? data.get().getHealthPercent() : 0f;

            int bgColor = BOUtil.getColor(uuid);
            guiGraphics.fill(drawX, rowY, drawX + avatarSize, rowY + avatarSize, bgColor);

            // 4) 灰度头像(dead)
            float r=1f,g=1f,b=1f,a=1f;
            if (checked && !data.get().isLiving()) {
                r=g=b=0.3f;
            }
            RenderSystem.setShaderColor(r,g,b,a);

            int margin      = 1;
            int avX         = drawX + margin;
            int avY         = rowY + margin;
            int smallAvSize = avatarSize - margin*2;

            PlayerFaceRenderer.draw(guiGraphics, player.getSkinLocation(), avX, avY, smallAvSize);

            RenderSystem.setShaderColor(1f,1f,1f,1f);
            int startY = rowY + avatarSize + margin;
            if (barRatio>0f) {
                int barHeight = showNameInfo ? 4 : 2;
                drawSmoothHealthBar(guiGraphics, barRatio, drawX, startY, drawX+avatarSize,startY + barHeight);
                startY += barHeight + margin;
            }

            int killCount = FPSMClient.getGlobalData().getPlayerData(uuid).map(PlayerData::getTempKills).orElse(0);
            if(killCount > 0){
                drawPlayerKills(guiGraphics,font,killCount,avX + (smallAvSize/2),startY,scaleFactor);
                startY += 5 + margin;
            }

            if(showNameInfo){
                drawPlayerName(guiGraphics, font, uuid, avX, avY + 1, smallAvSize,avatarSize,isCT,scaleFactor);

                if(isSameTeam) {
                    drawPlayerMoney(guiGraphics, font, uuid, avX + (smallAvSize/2), startY,scaleFactor);
                }
            }
        }
    }

    private void drawPlayerName(GuiGraphics guiGraphics, Font font, UUID uuid,
                                int avX, int avY, int smallAvSize,int width,boolean isCT,float scale)
    {
        String nameStr = getNameFromUUID(uuid);
        float textScale = 0.8f * scale;
        int xCenter = avX + (smallAvSize/2);
        int nameY = avY - 8;
        guiGraphics.fill(avX-1, nameY, avX + width - 1, nameY+6, -1072689136);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(xCenter, nameY - 1, 0);
        guiGraphics.pose().scale(textScale, textScale, 1f);

        int nameWidth = font.width(nameStr);
        if(nameWidth < width) {
            guiGraphics.drawString(font, nameStr, -nameWidth/2, 0,
                    isCT ? BOUtil.CT_COLOR:BOUtil.T_COLOR, false);
        }else{
            StringBuilder modified = new StringBuilder();
            for (char c : nameStr.toCharArray()){
                int currentWidth = font.width(modified.toString());
                if(currentWidth + font.width(String.valueOf(c)) + 2 < width) {
                    modified.append(c);
                }else{
                    modified.append("..");
                    break;
                }
            }
            int truncatedWidth = font.width(modified.toString());
            guiGraphics.drawString(font, modified.toString(), -truncatedWidth/2, 0,
                    isCT ? BOUtil.CT_COLOR:BOUtil.T_COLOR, false);
        }
        guiGraphics.pose().popPose();
    }

    private String getNameFromUUID(UUID uuid) {
        Player p = Minecraft.getInstance().level.getPlayerByUUID(uuid);
        if (p != null) {
            String name = p.getName().getString();
            cachedName.put(uuid,name);
            return p.getName().getString();
        }
        return cachedName.getOrDefault(uuid,uuid.toString().substring(0,8));
    }

    private void drawPlayerKills(GuiGraphics guiGraphics, Font font,int count,
                                 int centerX, int startY,float scaleFactor){
        String str = "\uD83D\uDC80".repeat(Math.max(0, count));
        if(str.isEmpty()) return;
        float textScale = 0.6f * scaleFactor;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(centerX, startY, 0);
        guiGraphics.pose().scale(textScale, textScale, 1f);

        int w = font.width(str);
        guiGraphics.drawString(font, str, -w/2, 0, 0xFFFFFFFF, false);
        guiGraphics.pose().popPose();
    }

    private void drawPlayerMoney(GuiGraphics guiGraphics, Font font, UUID uuid,
                                 int centerX, int startY ,float scaleFactor)
    {
        int moneyValue = FPSMClient.getGlobalData().getPlayerMoney(uuid);
        Component moneyStr = Component.literal("$" + moneyValue).withStyle(ChatFormatting.BOLD);

        float textScale = 0.8f * scaleFactor;

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(centerX, startY, 0);
        guiGraphics.pose().scale(textScale, textScale, 1f);

        int w = font.width(moneyStr);
        guiGraphics.drawString(font, moneyStr, -w/2, 0, 0xFFFFFFFF, false);

        guiGraphics.pose().popPose();
    }

    // 平滑血条
    private void drawSmoothHealthBar(GuiGraphics gg, float ratio,
                                     int startX, int startY, int endX, int endY)
    {
        int total = endX - startX;
        if(ratio != 1.0f){
            for (int i = 0; i < total; i++) {
                // 计算当前alpha值（从255到0线性变化）
                float progress = (float)i / total;
                int alpha = 255 - (int)(progress * 255);

                // 确保alpha在0-255范围内
                alpha = Math.max(0, Math.min(255, alpha));

                gg.fill(startX, startY, startX + i, endY,
                        RenderUtil.color(166, 42, 39, alpha));
            }
        }

        int fillW = (int)(total*(ratio));
        gg.fill(startX, startY, startX+fillW, endY, RenderUtil.color(255,255,255));
    }

    public static Component getCSGameTime(){
        return Component.literal(formatTime(CSClientData.time / 20)).withStyle(ChatFormatting.BOLD);
    }

    /**
     * 将总秒数和过去秒数转换为分钟和秒的字符串表示。
     *
     * @param totalSeconds 总秒数
     * @return 格式化的时间字符串，如 "01:00"
     */
    public static String formatTime(int totalSeconds) {
        // 倒计时剩余 <=10 秒时变红
        int remainingMinutes = totalSeconds / 60;
        int remainingSecondsPart = totalSeconds % 60;

        if(remainingMinutes == 0 && remainingSecondsPart <= 10){
            textRoundTimeColor = color(240,40,40);
        }else {
            textRoundTimeColor = color(255,255,255);
        }

        return BOUtil.formatMinutesSeconds(totalSeconds);
    }

}
