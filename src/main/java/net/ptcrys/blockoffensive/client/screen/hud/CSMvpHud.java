package net.ptcrys.blockoffensive.client.screen.hud;

import net.ptcrys.blockoffensive.data.MvpReason;
import net.ptcrys.blockoffensive.event.CSHUDRenderEvent;
import net.ptcrys.blockoffensive.sound.BOSoundRegister;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.common.MinecraftForge;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import org.joml.Matrix4f;

import java.util.UUID;

public class CSMvpHud {

    private static final Minecraft minecraft = Minecraft.getInstance();
    private static final Font font = minecraft.font;

    // 动画时长配置 (ms)
    private static final int ROUND_BANNER_DURATION = 320;
    private static final int MVP_PANEL_DURATION = 480;
    private static final int VINYL_SLIDE_DURATION = 560;
    private static final int COLOR_TRANSITION_DURATION = 160;
    private static final int CLOSING_ANIMATION_DURATION = 280;
    private static final float CLOSING_SPEED_FACTOR = 2.0f;

    // 基础分辨率（用于缩放计算）
    private static final int BASE_WIDTH = 1920;
    private static final int BASE_HEIGHT = 1080;

    // UI尺寸配置（基础分辨率下）
    private static final int ROUND_BANNER_WIDTH = 480;
    private static final int ROUND_BANNER_HEIGHT = 68;
    private static final int MVP_PANEL_WIDTH = 680;
    private static final int MVP_PANEL_HEIGHT = 106;
    private static final int SLEEVE_SIZE = 82;
    private static final int COLOR_BAR_HEIGHT = 20;

    // 动画状态
    private long roundBannerStartTime = -1;
    private long mvpInfoStartTime = -1;
    private long colorTransitionStartTime = -1;
    private long mvpColorTransitionStartTime = -1;
    private boolean animationPlaying = false;
    private long closeAnimationStartTime = -1;
    private boolean isClosing = false;

    // 玩家信息
    private UUID player;
    private Component currentPlayerName = Component.empty();
    private Component currentTeamName = Component.empty();
    private Component extraInfo1 = Component.empty();
    private Component extraInfo2 = Component.empty();
    private Component mvpReason = Component.empty();
    private boolean currentWinnerCtTeam = true;
    private int currentBannerWidthPx = 0;

    public void triggerAnimation(MvpReason reason) {
        MinecraftForge.EVENT_BUS.post(new CSHUDRenderEvent.RenderMvpHud.TriggeredAnimation(reason));
        this.player = reason.uuid;
        boolean isCtTeam = reason.isCtWinner();
        this.currentWinnerCtTeam = isCtTeam;
        this.currentTeamName = isCtTeam ? Component.translatable("blockoffensive.map.cs.winner.ct.round.message").withStyle(ChatFormatting.BOLD) : Component.translatable("blockoffensive.map.cs.winner.t.round.message").withStyle(ChatFormatting.BOLD);
        this.currentPlayerName = reason.getPlayerName().withStyle(ChatFormatting.BOLD);
        this.mvpReason = reason.getMvpReason().withStyle(ChatFormatting.BOLD);
        this.extraInfo1 = reason.getExtraInfo1().withStyle(ChatFormatting.BOLD);
        this.extraInfo2 = reason.getExtraInfo2().withStyle(ChatFormatting.BOLD);

        // 重置动画状态
        this.roundBannerStartTime = System.currentTimeMillis();
        this.mvpInfoStartTime = -1;
        this.colorTransitionStartTime = -1;
        this.mvpColorTransitionStartTime = -1;
        this.animationPlaying = true;

        if (minecraft.level != null && minecraft.player != null) {
            minecraft.level.playLocalSound(
                    minecraft.player.getOnPos().above(2),
                    isCtTeam ? BOSoundRegister.VOICE_CT_WIN.get() : BOSoundRegister.VOICE_T_WIN.get(),
                    SoundSource.VOICE,
                    1.0f,
                    1.0f,
                    false);
        }
    }

    public long getMvpInfoStartTime() {
        return mvpInfoStartTime;
    }

    /**
     * 渲染MVP HUD（适配MC原生GUI缩放）
     */
    public void render(GuiGraphics guiGraphics, int screenWidth, int screenHeight) {
        if (isClosing) {
            renderCloseAnimation(guiGraphics, screenWidth, screenHeight);
            return;
        }

        if (!animationPlaying) return;

        MinecraftForge.EVENT_BUS.post(new CSHUDRenderEvent.RenderMvpHud.Pre(guiGraphics, screenWidth, screenHeight, this));

        long currentTime = System.currentTimeMillis();
        PoseStack pose = guiGraphics.pose();
        float scaleFactor = ((float) screenWidth / BASE_WIDTH);

        // 回合胜利横幅动画
        if (roundBannerStartTime != -1) {
            float bannerProgress = Math.min((currentTime - roundBannerStartTime) / (float) ROUND_BANNER_DURATION, 1f);
            renderRoundVictoryBanner(guiGraphics, pose, bannerProgress, scaleFactor,
                    screenWidth, screenHeight, currentTime);

            // 横幅动画完成后触发MVP面板动画
            if (bannerProgress >= 1f && mvpInfoStartTime == -1) {
                mvpInfoStartTime = currentTime + 400;
                colorTransitionStartTime = currentTime;
            }
        }

        // MVP信息面板动画
        if (mvpInfoStartTime != -1 && currentTime > mvpInfoStartTime && !currentPlayerName.getString().isEmpty()) {
            float mvpProgress = Math.min((currentTime - mvpInfoStartTime) / (float) MVP_PANEL_DURATION, 1f);
            renderMVPInfoPanel(guiGraphics, pose, mvpProgress, scaleFactor, screenWidth, screenHeight, currentTime);
        }

        MinecraftForge.EVENT_BUS.post(new CSHUDRenderEvent.RenderMvpHud.Post(guiGraphics, screenWidth, screenHeight, this));
    }

    /**
     * 渲染回合胜利横幅 (CS:GO 风格)
     */
    private void renderRoundVictoryBanner(GuiGraphics guiGraphics, PoseStack pose, float bannerProgress,
                                          float scaleFactor, int screenWidth, int screenHeight, long currentTime) {
        Component leftArrow = Component.literal("»").withStyle(ChatFormatting.BOLD);
        Component rightArrow = Component.literal("«").withStyle(ChatFormatting.BOLD);

        final float ARROW_SCALE = 2.4f;
        final float TEXT_SCALE = 2.4f;
        float combinedArrowScale = scaleFactor * ARROW_SCALE;
        float combinedTextScale = scaleFactor * TEXT_SCALE;

        int sideBarWidth = Math.max(3, (int) (6 * scaleFactor));
        int outerPadding = Math.max(6, (int) (12 * scaleFactor));
        int innerGap = Math.max(4, (int) (10 * scaleFactor));
        int baseTextWidth = font.width(currentTeamName);
        int textWidth = (int) (baseTextWidth * combinedTextScale);
        int arrowWidth = (int) (font.width(leftArrow) * combinedArrowScale);
        int contentWidth = textWidth + arrowWidth * 2 + innerGap * 2;
        int requiredWidth = contentWidth + sideBarWidth * 2 + outerPadding * 2;

        int baseWidth = (int) (ROUND_BANNER_WIDTH * scaleFactor);
        int maxWidth = (int) (screenWidth * 0.92f);
        int scaledWidth = Math.max(baseWidth, Math.min(requiredWidth, maxWidth));

        int maxTextAreaWidth = scaledWidth - sideBarWidth * 2 - outerPadding * 2 - innerGap * 2 - arrowWidth * 2;
        if (maxTextAreaWidth > 0 && textWidth > maxTextAreaWidth && baseTextWidth > 0) {
            float fitScale = (float) maxTextAreaWidth / (float) baseTextWidth;
            combinedTextScale = Math.max(scaleFactor * 1.4f, Math.min(combinedTextScale, fitScale));
            textWidth = (int) (baseTextWidth * combinedTextScale);
        }
        currentBannerWidthPx = scaledWidth;
        int scaledHeight = Math.max(22, (int) (ROUND_BANNER_HEIGHT * scaleFactor));
        int animatedWidth = (int) (scaledWidth * bannerProgress);
        int x = (screenWidth - animatedWidth) / 2;
        int y = (int) (180 * ((float) screenHeight / BASE_HEIGHT));

        // 背景颜色过渡（从亮白闪烁平滑过渡至 CS:GO 深色半透明玻璃质感）
        int bgColor = 0xFFFFFFFF;
        if (colorTransitionStartTime != -1) {
            float colorProgress = Math.min((currentTime - colorTransitionStartTime) / (float) COLOR_TRANSITION_DURATION, 1f);
            bgColor = lerpColor(0xFFFFFFFF, 0xD411151D, colorProgress);
        } else {
            bgColor = 0xEEFFFFFF;
        }

        // 绘制背景面板与边框
        guiGraphics.fill(x, y, x + animatedWidth, y + scaledHeight, bgColor);

        // 细微上下边缘高光与阴影条
        guiGraphics.fill(x, y, x + animatedWidth, y + 1, 0x40FFFFFF);
        guiGraphics.fill(x, y + scaledHeight - 1, x + animatedWidth, y + scaledHeight, 0x50000000);

        // 绘制队伍颜色侧边条
        int teamColor = resolveTeamColor();
        guiGraphics.fill(x, y, x + sideBarWidth, y + scaledHeight, teamColor);
        guiGraphics.fill(x + animatedWidth - sideBarWidth, y, x + animatedWidth, y + scaledHeight, teamColor);

        // 横幅完全展开后渲染文本
        if (bannerProgress >= 1f) {
            int rightArrowWidth = (int) (font.width(rightArrow) * combinedArrowScale);
            int rightX = x + animatedWidth - sideBarWidth - (int) (12 * scaleFactor) - rightArrowWidth;
            int leftX = x + sideBarWidth + (int) (12 * scaleFactor);

            // 垂直居中计算
            int textTotalHeight = (int) (font.lineHeight * combinedTextScale);
            int arrowTotalHeight = (int) (font.lineHeight * combinedArrowScale);
            int verticalOffset = (textTotalHeight - arrowTotalHeight) / 2;
            int textY = y + (scaledHeight - textTotalHeight) / 2 + verticalOffset;

            // 文本颜色（含透明度过渡）
            int textColor = 0x00FFFFFF;
            if (colorTransitionStartTime != -1) {
                float alpha = Math.min((currentTime - colorTransitionStartTime) / 200f, 1f);
                textColor = (int) (alpha * 255) << 24 | 0xFFFFFF;
            } else {
                textColor = 0xFFFFFFFF;
            }

            // 渲染左侧箭头
            pose.pushPose();
            pose.scale(combinedArrowScale, combinedArrowScale, 1f);
            guiGraphics.drawString(font, leftArrow,
                    (int) ((leftX / scaleFactor) / ARROW_SCALE),
                    (int) ((textY / scaleFactor) / ARROW_SCALE) - verticalOffset / 2,
                    textColor, false);
            pose.popPose();

            // 渲染右侧箭头
            pose.pushPose();
            pose.scale(combinedArrowScale, combinedArrowScale, 1f);
            guiGraphics.drawString(font, rightArrow,
                    (int) ((rightX / scaleFactor) / ARROW_SCALE),
                    (int) ((textY / scaleFactor) / ARROW_SCALE) - verticalOffset / 2,
                    textColor, false);
            pose.popPose();

            // 渲染中间队伍名称
            int middleWidth = (int) (font.width(currentTeamName) * combinedTextScale);
            renderScaledText(guiGraphics, pose, currentTeamName,
                    x + (animatedWidth - middleWidth) / 2,
                    textY, textColor, combinedTextScale);
        }
    }

    /**
     * 渲染MVP信息面板（CS:GO 黑胶唱片 / Music Kit 风格）
     */
    private void renderMVPInfoPanel(GuiGraphics guiGraphics, PoseStack pose, float progress,
                                    float scaleFactor, int screenWidth, int screenHeight, long currentTime) {
        int scaledPanelWidth = (int) (MVP_PANEL_WIDTH * scaleFactor);
        int scaledPanelHeight = Math.max(48, (int) (MVP_PANEL_HEIGHT * scaleFactor));
        int bannerHeight = Math.max(22, (int) (ROUND_BANNER_HEIGHT * scaleFactor));
        int yOffset = bannerHeight + Math.max(4, (int) (12 * scaleFactor));
        int yPosition = (int) (180 * ((float) screenHeight / BASE_HEIGHT)) + yOffset;

        int animatedWidth = (int) (scaledPanelWidth * progress);
        int x = (screenWidth - animatedWidth) / 2;

        // 背景颜色过渡
        int bgColor = 0xFFFFFFFF;
        if (progress >= 1f) {
            if (mvpColorTransitionStartTime == -1) {
                mvpColorTransitionStartTime = currentTime;
            }
            float colorProgress = Math.min((currentTime - mvpColorTransitionStartTime) / (float) COLOR_TRANSITION_DURATION, 1f);
            bgColor = lerpColor(0xFFFFFFFF, 0xD80D1117, colorProgress);
        } else {
            bgColor = 0xCCFFFFFF;
        }

        // 绘制面板背景与微边框
        guiGraphics.fill(x, yPosition, x + animatedWidth, yPosition + scaledPanelHeight, bgColor);
        guiGraphics.fill(x, yPosition, x + animatedWidth, yPosition + 1, 0x30FFFFFF);
        guiGraphics.fill(x, yPosition + scaledPanelHeight - 1, x + animatedWidth, yPosition + scaledPanelHeight, 0x50000000);

        // 面板未展开到一定程度时不渲染内容
        if (progress < 0.2f) return;

        int scaledSleeveSize = Math.max(32, (int) (SLEEVE_SIZE * scaleFactor));
        int sleeveX = x + Math.max(12, (int) (20 * scaleFactor));
        int sleeveY = yPosition + (scaledPanelHeight - scaledSleeveSize) / 2;

        // 黑胶唱片出鞘动效 (平滑从封套后方滑出半截)
        float vinylSlideProgress = Math.min((currentTime - mvpInfoStartTime) / (float) VINYL_SLIDE_DURATION, 1f);
        float easedSlide = easeOutBack(vinylSlideProgress);
        float maxSlideDistance = scaledSleeveSize * 0.50f;
        float currentSlide = maxSlideDistance * easedSlide;

        float discCenterX = sleeveX + scaledSleeveSize / 2.0f + currentSlide;
        float discCenterY = sleeveY + scaledSleeveSize / 2.0f;
        float vinylRadius = scaledSleeveSize * 0.46f;

        // 1. 渲染黑胶唱片（在封套下方，露出右侧并保持旋转）
        renderVinylDisc(pose, discCenterX, discCenterY, vinylRadius, currentTime);

        // 2. 渲染唱片封套 (Album Sleeve / Jacket - 显示玩家皮肤头像与黑胶封套边框)
        renderAlbumSleeve(guiGraphics, sleeveX, sleeveY, scaledSleeveSize);

        // 3. 渲染右侧 MVP 信息与音乐盒详情
        int infoStartX = sleeveX + (int) (scaledSleeveSize + maxSlideDistance) + Math.max(8, (int) (14 * scaleFactor));
        int infoMaxWidth = (x + animatedWidth) - infoStartX - Math.max(8, (int) (16 * scaleFactor));
        if (infoMaxWidth < 50) return;

        renderMVPInfoText(guiGraphics, pose, infoStartX, sleeveY, scaledSleeveSize, infoMaxWidth, scaleFactor, currentTime);
    }

    /**
     * 渲染黑胶唱片 (带同心圆音轨、阵营色标签、中心孔与动态反光)
     */
    private void renderVinylDisc(PoseStack pose, float cx, float cy, float radius, long currentTime) {
        if (radius <= 4) return;

        // 匀速旋转角度 (约 45 RPM)
        float rotationDegrees = ((currentTime - (mvpInfoStartTime > 0 ? mvpInfoStartTime : 0)) * 0.08f) % 360.0f;

        pose.pushPose();
        pose.translate(cx, cy, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(rotationDegrees));

        // 1. 黑胶主体盘面 (深黑炭色)
        drawCircle(pose, 0, 0, radius, 0xFF101216, 40);

        // 2. 黑胶外圈边缘微光
        drawRing(pose, 0, 0, radius * 0.95f, radius, 0xFF222630, 40);

        // 3. 同心音轨刻线 (Concentric Vinyl Grooves)
        drawRing(pose, 0, 0, radius * 0.84f, radius * 0.86f, 0x2A3D4758, 36);
        drawRing(pose, 0, 0, radius * 0.72f, radius * 0.74f, 0x223D4758, 36);
        drawRing(pose, 0, 0, radius * 0.60f, radius * 0.62f, 0x2A3D4758, 32);
        drawRing(pose, 0, 0, radius * 0.48f, radius * 0.50f, 0x203D4758, 28);

        // 4. 经典对角高光扇形反光 (Specular Vinyl Sheen)
        drawArcWedge(pose, 0, 0, radius * 0.38f, radius * 0.94f, (float) Math.toRadians(35), (float) Math.toRadians(75), 0x18FFFFFF, 12);
        drawArcWedge(pose, 0, 0, radius * 0.38f, radius * 0.94f, (float) Math.toRadians(215), (float) Math.toRadians(255), 0x18FFFFFF, 12);

        // 5. 中心唱片标签 (Record Label - 阵营色 + 装饰圈)
        int labelColor = resolveTeamColor();
        drawCircle(pose, 0, 0, radius * 0.36f, labelColor, 28);
        drawRing(pose, 0, 0, radius * 0.34f, radius * 0.36f, 0xFFEAEFF5, 28);
        drawRing(pose, 0, 0, radius * 0.22f, radius * 0.24f, 0x60FFFFFF, 24);

        // 6. 唱机中心轴孔 (Spindle Hole)
        drawCircle(pose, 0, 0, radius * 0.09f, 0xFF0B0C0E, 16);
        drawRing(pose, 0, 0, radius * 0.08f, radius * 0.095f, 0x808899A6, 16);

        pose.popPose();
    }

    /**
     * 渲染唱片封套 (Album Cover / Jacket)
     */
    private void renderAlbumSleeve(GuiGraphics guiGraphics, int x, int y, int size) {
        // 投影与外框底板
        guiGraphics.fill(x - 2, y - 1, x + size + 2, y + size + 3, 0x90000000);
        guiGraphics.fill(x, y, x + size, y + size, 0xFF14171E);

        // 玩家头像（作为唱片封面）
        renderAvatar(guiGraphics, x, y, size);

        // 封套外框与玻璃光泽质感
        guiGraphics.fill(x, y, x + size, y + 1, 0x50FFFFFF);
        guiGraphics.fill(x, y + size - 1, x + size, y + size, 0x60000000);
        guiGraphics.fill(x + size - 1, y, x + size, y + size, 0x40000000);
    }

    /**
     * 渲染 MVP 信息与音乐盒文字排版
     */
    private void renderMVPInfoText(GuiGraphics guiGraphics, PoseStack pose,
                                   int startX, int sleeveY, int sleeveSize, int maxWidth,
                                   float scaleFactor, long currentTime) {
        // 1. 第一行：★ 最有价值玩家: PlayerName (CS:GO 风格金色五角星 + 玩家名)
        Component star = Component.literal("★ ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        Component titlePrefix = Component.translatable("blockoffensive.hud.mvp.title_prefix");
        // 若缺少特定语言键则回退为默认格式
        String titlePrefixStr = titlePrefix.getString();
        if (titlePrefixStr.equals("blockoffensive.hud.mvp.title_prefix")) {
            titlePrefixStr = "MVP: ";
        }
        MutableComponent headerComp = Component.empty()
                .append(star)
                .append(Component.literal(titlePrefixStr).withStyle(ChatFormatting.BOLD, ChatFormatting.WHITE))
                .append(currentPlayerName);

        float nameScale = fitTextScale(headerComp, scaleFactor * 1.65f, maxWidth);
        int nameY = sleeveY + Math.max(2, (int) (4 * scaleFactor));
        renderScaledText(guiGraphics, pose, headerComp, startX, nameY, 0xFFFFFFFF, nameScale);

        // 2. 第二行：MVP 获胜原因（CS:GO 胶囊标签样式）
        Component displayReason = !mvpReason.getString().isEmpty() ? mvpReason : extraInfo1;
        float reasonScale = fitTextScale(displayReason, scaleFactor * 1.15f, maxWidth - 16);
        int reasonTextWidth = (int) (font.width(displayReason) * reasonScale);
        int pillPadding = Math.max(4, (int) (6 * scaleFactor));
        int pillHeight = Math.max(12, (int) (16 * scaleFactor));
        int reasonY = nameY + (int) (font.lineHeight * nameScale) + Math.max(4, (int) (6 * scaleFactor));

        // 胶囊背景
        guiGraphics.fill(startX, reasonY - 1,
                startX + reasonTextWidth + pillPadding * 2,
                reasonY + pillHeight - 1,
                withAlpha(resolveTeamColor(), 0x38));
        guiGraphics.fill(startX, reasonY - 1, startX + Math.max(2, (int) (2 * scaleFactor)), reasonY + pillHeight - 1, resolveTeamColor());

        // 胶囊内文字
        renderScaledText(guiGraphics, pose, displayReason,
                startX + pillPadding,
                reasonY + (pillHeight - (int) (font.lineHeight * reasonScale)) / 2,
                0xFFE6EDF5, reasonScale);

        // 3. 第三行：♫ 音乐盒 | 曲目名称 (CS:GO 经典音乐盒铭牌)
        Component musicNote = Component.literal("♫ ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        String musicNameStr = extraInfo2.getString();
        if (musicNameStr.isEmpty()) {
            musicNameStr = "Valve, CS:GO";
        }
        MutableComponent musicComp = Component.empty()
                .append(musicNote)
                .append(Component.translatable("blockoffensive.hud.mvp.musickit_prefix"))
                .append(Component.literal(" | " + musicNameStr).withStyle(ChatFormatting.GRAY));

        // 回退检查
        if (musicComp.getString().contains("blockoffensive.hud.mvp.musickit_prefix")) {
            musicComp = Component.empty()
                    .append(musicNote)
                    .append(Component.literal("Music Kit | " + musicNameStr).withStyle(ChatFormatting.AQUA));
        }

        float musicScale = fitTextScale(musicComp, scaleFactor * 1.35f, maxWidth);
        int musicY = reasonY + pillHeight + Math.max(4, (int) (6 * scaleFactor));
        renderScaledText(guiGraphics, pose, musicComp, startX, musicY, 0xFFCAD4DF, musicScale);
    }

    /**
     * 绘制实心圆形
     */
    private void drawCircle(PoseStack pose, float cx, float cy, float radius, int color, int segments) {
        Matrix4f matrix = pose.last().pose();
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        float a = ((color >> 24) & 0xFF) / 255.0f;
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        buffer.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        buffer.vertex(matrix, cx, cy, 0).color(r, g, b, a).endVertex();
        for (int i = 0; i <= segments; i++) {
            float angle = (float) (i * Math.PI * 2.0 / segments);
            float x = cx + (float) Math.cos(angle) * radius;
            float y = cy + (float) Math.sin(angle) * radius;
            buffer.vertex(matrix, x, y, 0).color(r, g, b, a).endVertex();
        }
        tesselator.end();
        RenderSystem.disableBlend();
    }

    /**
     * 绘制圆环
     */
    private void drawRing(PoseStack pose, float cx, float cy, float innerRadius, float outerRadius, int color, int segments) {
        Matrix4f matrix = pose.last().pose();
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        float a = ((color >> 24) & 0xFF) / 255.0f;
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        buffer.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i <= segments; i++) {
            float angle = (float) (i * Math.PI * 2.0 / segments);
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            buffer.vertex(matrix, cx + cos * outerRadius, cy + sin * outerRadius, 0).color(r, g, b, a).endVertex();
            buffer.vertex(matrix, cx + cos * innerRadius, cy + sin * innerRadius, 0).color(r, g, b, a).endVertex();
        }
        tesselator.end();
        RenderSystem.disableBlend();
    }

    /**
     * 绘制带内径的圆弧扇面（用于黑胶反光）
     */
    private void drawArcWedge(PoseStack pose, float cx, float cy, float innerRadius, float outerRadius,
                              float startAngleRad, float endAngleRad, int color, int segments) {
        Matrix4f matrix = pose.last().pose();
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        float a = ((color >> 24) & 0xFF) / 255.0f;
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        buffer.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i <= segments; i++) {
            float angle = startAngleRad + (endAngleRad - startAngleRad) * (i / (float) segments);
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            buffer.vertex(matrix, cx + cos * outerRadius, cy + sin * outerRadius, 0).color(r, g, b, a).endVertex();
            buffer.vertex(matrix, cx + cos * innerRadius, cy + sin * innerRadius, 0).color(r, g, b, a).endVertex();
        }
        tesselator.end();
        RenderSystem.disableBlend();
    }

    /**
     * 缓动函数：Ease-Out-Back 弹出回弹动效
     */
    private float easeOutBack(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1.0f;
        return 1.0f + c3 * (float) Math.pow(t - 1.0f, 3) + c1 * (float) Math.pow(t - 1.0f, 2);
    }

    /**
     * 若文本缩放后超出最大可用宽度，则等比缩小到可用宽度（带下限，防止缩到不可读）。
     */
    private float fitTextScale(Component text, float scale, int maxWidth) {
        if (text == null || maxWidth <= 0) {
            return scale;
        }
        int baseWidth = font.width(text);
        if (baseWidth <= 0) {
            return scale;
        }
        int scaledWidth = (int) (baseWidth * scale);
        if (scaledWidth > maxWidth) {
            return Math.max(0.45f, (float) maxWidth / baseWidth);
        }
        return scale;
    }

    /**
     * 渲染缩放后的文本
     */
    private void renderScaledText(GuiGraphics guiGraphics, PoseStack pose, Component text,
                                  int x, int y, int color, float scale) {
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(scale, scale, 1f);
        guiGraphics.drawString(font, text, 0, 0, color, false);
        pose.popPose();
    }

    /**
     * 渲染玩家头像
     */
    private void renderAvatar(GuiGraphics guiGraphics, int x, int y, int size) {
        ResourceLocation defaultAvatar = ResourceLocation.tryBuild("fpsmatch", "textures/ui/avatar.png");
        PlayerInfo info = getPlayerInfoByUUID(this.player);

        if (info != null) {
            PlayerFaceRenderer.draw(guiGraphics, info.getSkinLocation(), x, y, size);
        } else {
            if (defaultAvatar != null) {
                guiGraphics.blit(defaultAvatar, x, y, size, size, 0, 0, 64, 64, 64, 64);
            }
        }
    }

    /**
     * 颜色插值（用于过渡动画）
     */
    private int lerpColor(int startColor, int endColor, float progress) {
        int startA = (startColor >> 24) & 0xFF;
        int startR = (startColor >> 16) & 0xFF;
        int startG = (startColor >> 8) & 0xFF;
        int startB = startColor & 0xFF;

        int endA = (endColor >> 24) & 0xFF;
        int endR = (endColor >> 16) & 0xFF;
        int endG = (endColor >> 8) & 0xFF;
        int endB = endColor & 0xFF;

        return ((int) (startA + (endA - startA) * progress) << 24) |
                ((int) (startR + (endR - startR) * progress) << 16) |
                ((int) (startG + (endG - startG) * progress) << 8) |
                (int) (startB + (endB - startB) * progress);
    }

    private int resolveTeamColor() {
        return currentWinnerCtTeam ? 0xFF4B729F : 0xFFDE9B35;
    }

    private int withAlpha(int rgbColor, int alpha) {
        return (alpha << 24) | (rgbColor & 0x00FFFFFF);
    }

    /**
     * 渲染关闭动画
     */
    private void renderCloseAnimation(GuiGraphics guiGraphics, int screenWidth, int screenHeight) {
        long currentTime = System.currentTimeMillis();
        float progress = Math.min((currentTime - closeAnimationStartTime) / (float) CLOSING_ANIMATION_DURATION, 1f);

        float scaleFactor = ((float) screenWidth / BASE_WIDTH);

        // 颜色过渡（更快变白）
        int bgColor = lerpColor(0xD80D1117, 0xFFFFFFFF, Math.min(progress * CLOSING_SPEED_FACTOR, 1f));

        // 缓动收缩比例
        float closingRatio = (float) Math.pow(progress, 0.8);

        // 渲染横幅关闭动画
        renderClosingBanner(guiGraphics, screenWidth, screenHeight, closingRatio, bgColor, scaleFactor);

        // 渲染MVP面板关闭动画
        renderClosingPanel(guiGraphics, screenWidth, screenHeight, closingRatio, bgColor, scaleFactor);

        // 动画完成后重置状态
        if (progress >= 1f) {
            resetAnimation();
        }
    }

    /**
     * 渲染关闭中的胜利横幅
     */
    private void renderClosingBanner(GuiGraphics guiGraphics, int screenWidth, int screenHeight,
                                     float closingRatio, int color, float scaleFactor) {
        int fallbackWidth = (int) (ROUND_BANNER_WIDTH * scaleFactor);
        int originalWidth = currentBannerWidthPx > 0 ? currentBannerWidthPx : fallbackWidth;
        int currentWidth = (int) (originalWidth * (1 - closingRatio));
        int yPos = (int) (180 * ((float) screenHeight / BASE_HEIGHT));
        int centerX = screenWidth / 2;

        int leftStart = centerX - currentWidth / 2;
        int rightEnd = centerX + currentWidth / 2;

        guiGraphics.fill(leftStart, yPos,
                rightEnd,
                yPos + Math.max(22, (int) (ROUND_BANNER_HEIGHT * scaleFactor)),
                color);
    }

    /**
     * 渲染关闭中的MVP面板
     */
    private void renderClosingPanel(GuiGraphics guiGraphics, int screenWidth, int screenHeight,
                                    float closingRatio, int color, float scaleFactor) {
        int originalWidth = (int) (MVP_PANEL_WIDTH * scaleFactor);
        int currentWidth = (int) (originalWidth * (1 - closingRatio));
        int bannerY = (int) (180 * ((float) screenHeight / BASE_HEIGHT));
        int bannerHeight = Math.max(22, (int) (ROUND_BANNER_HEIGHT * scaleFactor));
        int panelY = bannerY + bannerHeight + Math.max(4, (int) (12 * scaleFactor));

        int centerX = screenWidth / 2;
        int leftStart = centerX - currentWidth / 2;
        int rightEnd = centerX + currentWidth / 2;

        guiGraphics.fill(leftStart, panelY,
                rightEnd,
                panelY + Math.max(48, (int) (MVP_PANEL_HEIGHT * scaleFactor)),
                color);
    }

    /**
     * 触发关闭动画
     */
    public void triggerCloseAnimation() {
        if (!animationPlaying) return;
        CSGameHud.getInstance().stopKillAnim();
        isClosing = true;
        closeAnimationStartTime = System.currentTimeMillis();
    }

    public long getRoundBannerStartTime() {
        return roundBannerStartTime;
    }

    public long getColorTransitionStartTime() {
        return colorTransitionStartTime;
    }

    public long getMvpColorTransitionStartTime() {
        return mvpColorTransitionStartTime;
    }

    public boolean isAnimationPlaying() {
        return animationPlaying;
    }

    public long getCloseAnimationStartTime() {
        return closeAnimationStartTime;
    }

    public boolean isClosing() {
        return isClosing;
    }

    public Component getCurrentTeamName() {
        return currentTeamName;
    }

    public Component getCurrentPlayerName() {
        return currentPlayerName;
    }

    public UUID getPlayer() {
        return player;
    }

    public Component getExtraInfo2() {
        return extraInfo2;
    }

    public Component getExtraInfo1() {
        return extraInfo1;
    }

    public Component getMvpReason() {
        return mvpReason;
    }

    /**
     * 重置所有动画状态
     */
    public void resetAnimation() {
        closeAnimationStartTime = -1;
        isClosing = false;
        roundBannerStartTime = -1;
        mvpInfoStartTime = -1;
        colorTransitionStartTime = -1;
        mvpColorTransitionStartTime = -1;
        animationPlaying = false;
        currentPlayerName = Component.empty();
        currentTeamName = Component.empty();
        extraInfo1 = Component.empty();
        extraInfo2 = Component.empty();
        player = null;
        currentWinnerCtTeam = true;
        currentBannerWidthPx = 0;
    }

    /**
     * 根据UUID获取玩家信息
     */
    public PlayerInfo getPlayerInfoByUUID(UUID uuid) {
        if (minecraft.player == null || minecraft.player.connection == null || uuid == null) {
            return null;
        }
        return minecraft.player.connection.getListedOnlinePlayers().stream()
                .filter(playerInfo -> playerInfo.getProfile().getId().equals(uuid))
                .findFirst()
                .orElse(null);
    }
}
