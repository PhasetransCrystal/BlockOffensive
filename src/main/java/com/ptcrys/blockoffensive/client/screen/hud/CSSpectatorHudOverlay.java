package com.ptcrys.blockoffensive.client.screen.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.ptcrys.blockoffensive.client.spec.SpecHudAPI;
import com.ptcrys.blockoffensive.client.key.SwitchSpectatorKey;
import com.ptcrys.fpsmatch.FPSMatch;
import com.ptcrys.fpsmatch.common.client.FPSMClient;
import com.ptcrys.fpsmatch.common.client.screen.texture.NamecardResolver;
import com.ptcrys.fpsmatch.common.client.screen.texture.NameCardTexture;
import com.ptcrys.fpsmatch.core.team.ClientTeam;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.io.File;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.ptcrys.fpsmatch.util.RenderUtil.*;
import static net.minecraft.util.Mth.clamp;

/**
 * 观察者模式下的玩家信息HUD渲染器
 * 负责显示被观察玩家的头像、生命值、击杀数、爆头率等信息
 */
public final class CSSpectatorHudOverlay {
    // 线程池 - 处理名片IO操作
    private static final ExecutorService NAME_CARD_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "spechud-namecard-io");
        thread.setDaemon(true);
        return thread;
    });

    // 状态跟踪变量
    private static UUID lastTargetUuid = null;
    private static AbstractClientPlayer lastTargetPlayer = null;

    // 名片纹理相关
    private static volatile ResourceLocation currentCardLocation = null;
    private static volatile NameCardTexture currentCardTexture = null;
    private static volatile ResourceLocation previousCardLocation = null;
    private static volatile NameCardTexture previousCardTexture = null;

    // 名片缓存 - 避免重复加载
    private static final Map<UUID, ResourceLocation> CARD_LOCATION_CACHE = new ConcurrentHashMap<>();
    private static final Map<UUID, NameCardTexture> CARD_TEXTURE_CACHE = new ConcurrentHashMap<>();

    // 时间跟踪
    private static long lastFrameTimeNs = 0L;

    // 动画状态变量
    private static float visibilityAlpha = 0f;
    private static float slideYPixels = 12f;
    private static float currentCardAlpha = 0f;
    private static float previousCardAlpha = 0f;

    // 数值显示状态（带平滑动画）
    private static float shownHealth = 0f, targetHealth = 0f;
    private static float shownMaxHealth = 0f, targetMaxHealth = 0f;
    private static float shownHeadshotRate = 0f, targetHeadshotRate = 0f;
    private static float shownKills = 0f, targetKills = 0f;
    private static float shownHeadshots = 0f, targetHeadshots = 0f;

    // 动画半衰期常量（秒）
    private static final float ANIM_HALF_LIFE_VISIBILITY = 0.12f;
    private static final float ANIM_HALF_LIFE_SLIDE = 0.14f;
    private static final float ANIM_HALF_LIFE_CARD = 0.18f;
    private static final float ANIM_HALF_LIFE_VALUE = 0.18f;

    // 面板尺寸常量
    private static final float PANEL_HEIGHT = 64f;
    private static final float PANEL_WIDTH = 320f;
    private static final float PANEL_MARGIN = 10f;

    // 元素尺寸常量
    private static final int AVATAR_SIZE = 44;
    private static final int PADDING = 12;
    private static final int TEAM_STRIPE_WIDTH = 3;

    // 颜色常量
    private static final int PANEL_BACKGROUND_COLOR = 0xCC141416;
    private static final int PANEL_TOP_HIGHLIGHT = 0x1FFFFFFF;
    private static final int PANEL_STROKE = 0x40FFFFFF;
    private static final int PANEL_STROKE_LIGHT = 0x28FFFFFF;
    private static final int HP_BAR_BACKGROUND = 0x22000000;
    private static final int TEXT_COLOR_WHITE = 0xFFFFFFFF;
    private static final int TEXT_COLOR_ITEM = 0xFF11AA66;
    private static final int TEXT_COLOR_ITEM_EN = 0xFF6E6E6E;
    private static final int TEXT_COLOR_HEADSHOT = 0xFFFF9900;
    private static final int TEXT_COLOR_KILLS = 0xFF222222;

    // 团队颜色常量
    private static final int TEAM_COLOR_T = 0xFFFFA500;
    private static final int TEAM_COLOR_CT = 0xFF00BFFF;
    private static final int TEAM_COLOR_DEFAULT = 0xFF707070;

    // 字符串常量
    private static final String FORMAT_HEALTH = "%.0f/%.0f";
    private static final String TEAM_NONE = "none";
    private static final String TEAM_SPECTATOR = "spectator";
    private static final String ITEM_EMPTY_EN = "Empty";


    float deltaTime;
    /**
     * 渲染HUD元素
     * @param guiGraphics 渲染工具类
     */
    /** True when the name card has non-negligible visibility this frame. */
    public static boolean isOccupyingScreen() {
        return visibilityAlpha > 0.01f && lastTargetUuid != null;
    }

    public static float currentSlideYPixels() {
        return slideYPixels;
    }

    /** Clear spectator-only projection state at the player/spectator mode boundary. */
    public void reset() {
        lastTargetUuid = null;
        lastTargetPlayer = null;
        currentCardLocation = null;
        currentCardTexture = null;
        previousCardLocation = null;
        previousCardTexture = null;
        lastFrameTimeNs = 0L;
        visibilityAlpha = 0f;
        slideYPixels = 12f;
        currentCardAlpha = 0f;
        previousCardAlpha = 0f;
        shownHealth = 0f;
        targetHealth = 0f;
        shownMaxHealth = 0f;
        targetMaxHealth = 0f;
        shownHeadshotRate = 0f;
        targetHeadshotRate = 0f;
        shownKills = 0f;
        targetKills = 0f;
        shownHeadshots = 0f;
        targetHeadshots = 0f;
        deltaTime = 0f;
    }

    public void render(GuiGraphics guiGraphics) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer localPlayer = minecraft.player;
        if (localPlayer == null) return;

        // 计算帧间隔时间
        long currentTime = System.nanoTime();
        deltaTime = (lastFrameTimeNs == 0L) ? 0f : (currentTime - lastFrameTimeNs) / 1_000_000_000f;
        lastFrameTimeNs = currentTime;
        deltaTime = clamp(deltaTime,0, 0.1f);

        // 更新目标玩家
        UUID targetUuid = updateTargetPlayer(localPlayer, minecraft);

        // 更新动画目标值
        updateAnimationTargets(targetUuid);

        // 更新动画状态
        updateAnimations(deltaTime);

        // 如果不可见则不渲染
        if (visibilityAlpha <= 0.01f || lastTargetUuid == null) return;

        // 计算面板位置和尺寸
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int panelWidth = Math.round(PANEL_WIDTH);
        int panelHeight = Math.round(PANEL_HEIGHT);
        int panelX = (screenWidth - panelWidth) / 2;
        int panelY = Math.round(screenHeight - panelHeight - PANEL_MARGIN + slideYPixels);

        // 渲染各层元素
        renderPanelBackground(guiGraphics, panelX, panelY, panelWidth, panelHeight, visibilityAlpha);
        renderCardLayer(guiGraphics, panelX, panelY, panelWidth, panelHeight,
                previousCardLocation, previousCardTexture, previousCardAlpha * visibilityAlpha);
        renderCardLayer(guiGraphics, panelX, panelY, panelWidth, panelHeight,
                currentCardLocation, currentCardTexture, currentCardAlpha * visibilityAlpha);
        renderTeamStripes(guiGraphics, panelX, panelY, panelWidth, panelHeight, visibilityAlpha);
        renderPlayerInfo(guiGraphics, minecraft, panelX, panelY, panelWidth, panelHeight, visibilityAlpha);
        renderControls(guiGraphics, minecraft, panelX, panelY, panelWidth, visibilityAlpha);
    }

    private void renderControls(GuiGraphics graphics, Minecraft minecraft, int panelX, int panelY,
                                int panelWidth, float alpha) {
        String previous = SwitchSpectatorKey.KEY_SPECTATE_PREV.getTranslatedKeyMessage().getString();
        String next = SwitchSpectatorKey.KEY_SPECTATE_NEXT.getTranslatedKeyMessage().getString();
        String text = I18n.get("blockoffensive.spectator.controls", previous, next);
        int color = ((int) (Math.max(0.0F, Math.min(1.0F, alpha)) * 255) << 24) | 0x00C8D2DC;
        graphics.drawString(minecraft.font, text,
                panelX + (panelWidth - minecraft.font.width(text)) / 2, panelY - minecraft.font.lineHeight - 3,
                color, false);
    }

    /**
     * 更新当前观察的目标玩家
     */
    private UUID updateTargetPlayer(LocalPlayer localPlayer, Minecraft minecraft) {
        UUID targetUuid = null;
        AbstractClientPlayer targetPlayer = null;

        if (localPlayer.isSpectator()) {
            Entity cameraEntity = minecraft.getCameraEntity();
            if (cameraEntity instanceof AbstractClientPlayer player && !player.getUUID().equals(localPlayer.getUUID())) {
                targetUuid = player.getUUID();
                targetPlayer = player;
            }
        }

        // 目标变更时重置状态
        if (!Objects.equals(targetUuid, lastTargetUuid)) {
            lastTargetUuid = targetUuid;
            lastTargetPlayer = targetPlayer;

            // 切换名片时的动画处理
            previousCardLocation = currentCardLocation;
            previousCardTexture = currentCardTexture;
            previousCardAlpha = currentCardAlpha;

            currentCardLocation = null;
            currentCardTexture = null;
            currentCardAlpha = 0f;

            // 异步加载名片
            if (targetUuid != null) {
                loadNameCardAsync(targetUuid, minecraft);
            }
        }

        return targetUuid;
    }

    /**
     * 更新动画目标值
     */
    private void updateAnimationTargets(UUID targetUuid) {
        float visibilityTarget;
        float slideTarget;

        if (targetUuid != null) {
            visibilityTarget = 1f;
            slideTarget = 0f;

            // 更新玩家数据目标值
            updatePlayerStatsTargets();
        } else {
            visibilityTarget = 0f;
            slideTarget = 12f;
            // 完全不可见时重置状态
            if (visibilityAlpha <= 0.01f) {
                lastTargetUuid = null;
                lastTargetPlayer = null;
            }
        }

        // 更新动画目标
        visibilityAlpha = expSmooth(visibilityAlpha, visibilityTarget, deltaTime, ANIM_HALF_LIFE_VISIBILITY);
        slideYPixels = expSmooth(slideYPixels, slideTarget, deltaTime, ANIM_HALF_LIFE_SLIDE);
    }

    /**
     * 更新玩家统计数据的目标值
     */
    private void updatePlayerStatsTargets() {
        if (lastTargetUuid == null) return;

        // 更新击杀和爆头数据
        int kills = FPSMClient.getGlobalData().getKills(lastTargetUuid);
        int headshots = FPSMClient.getGlobalData().getHeadshots(lastTargetUuid);
        float headshotRate = kills > 0 ? (headshots / (float) kills) : 0f;

        targetKills = kills;
        targetHeadshots = headshots;
        targetHeadshotRate = headshotRate;

        // 更新生命值数据
        if (lastTargetPlayer != null) {
            targetHealth = lastTargetPlayer.getHealth();
            targetMaxHealth = lastTargetPlayer.getMaxHealth();
        } else {
            targetHealth = 0f;
            targetMaxHealth = 0f;
        }
    }

    /**
     * 更新所有动画状态
     */
    private void updateAnimations(float deltaTime) {
        // 卡片透明度动画
        currentCardAlpha = expSmooth(currentCardAlpha, currentCardTexture != null ? 1f : 0f, deltaTime, ANIM_HALF_LIFE_CARD);
        previousCardAlpha = expSmooth(previousCardAlpha, 0f, deltaTime, ANIM_HALF_LIFE_CARD);

        // 数值平滑动画
        shownHealth = expSmooth(shownHealth, targetHealth, deltaTime, ANIM_HALF_LIFE_VALUE);
        shownMaxHealth = expSmooth(shownMaxHealth, targetMaxHealth, deltaTime, ANIM_HALF_LIFE_VALUE);
        shownHeadshotRate = expSmooth(shownHeadshotRate, targetHeadshotRate, deltaTime, ANIM_HALF_LIFE_VALUE);
        shownKills = expSmooth(shownKills, targetKills, deltaTime, ANIM_HALF_LIFE_VALUE);
        shownHeadshots = expSmooth(shownHeadshots, targetHeadshots, deltaTime, ANIM_HALF_LIFE_VALUE);
    }

    /**
     * 异步加载玩家名片
     */
    private void loadNameCardAsync(UUID targetUuid, Minecraft minecraft) {
        NAME_CARD_EXECUTOR.execute(() -> {
            // 检查缓存
            ResourceLocation cachedLoc = CARD_LOCATION_CACHE.get(targetUuid);
            NameCardTexture cachedTex = CARD_TEXTURE_CACHE.get(targetUuid);
            if (cachedLoc != null && cachedTex != null) {
                minecraft.execute(() -> applyNamecard(targetUuid, cachedLoc, cachedTex));
                return;
            }

            // 加载新名片
            File namecardFile = NamecardResolver.resolve(targetUuid);
            if (namecardFile != null && namecardFile.isFile()) {
                ResourceLocation textureId = ResourceLocation.tryBuild(FPSMatch.MODID, "namecard/" + targetUuid);
                NameCardTexture texture = new NameCardTexture(namecardFile, textureId);

                minecraft.execute(() -> {
                    minecraft.getTextureManager().register(textureId, texture);
                    CARD_LOCATION_CACHE.put(targetUuid, textureId);
                    CARD_TEXTURE_CACHE.put(targetUuid, texture);
                    applyNamecard(targetUuid, textureId, texture);
                });
            }
        });
    }

    /**
     * 应用名片到当前显示
     */
    private static void applyNamecard(UUID owner, ResourceLocation id, NameCardTexture tex) {
        if (owner == null || id == null || tex == null) return;
        if (!owner.equals(lastTargetUuid)) return;

        currentCardLocation = id;
        currentCardTexture = tex;
        currentCardAlpha = 0f;
    }

    /**
     * 渲染面板背景
     */
    private void renderPanelBackground(GuiGraphics g, int x, int y, int width, int height, float alpha) {
        RenderSystem.enableBlend();

        // 绘制背景和高光
        int bgColor = mulAlpha(PANEL_BACKGROUND_COLOR, alpha);
        int topHighlight = mulAlpha(PANEL_TOP_HIGHLIGHT, alpha);
        int stroke = mulAlpha(PANEL_STROKE, alpha * 0.9f);
        int strokeLight = mulAlpha(PANEL_STROKE_LIGHT, alpha * 0.9f);

        g.fill(x, y, x + width, y + height, bgColor);
        g.fill(x, y, x + width, y + 1, topHighlight);

        // 绘制边框
        g.fill(x, y, x + width, y + 1, stroke);
        g.fill(x, y + height - 1, x + width, y + height, stroke);
        g.fill(x, y, x + 1, y + height, stroke);
        g.fill(x + width - 1, y, x + width, y + height, stroke);

        g.fill(x + 1, y + 1, x + width - 1, y + 2, strokeLight);
        g.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, strokeLight);

        RenderSystem.disableBlend();
    }

    /**
     * 渲染名片层
     */
    private void renderCardLayer(GuiGraphics g, int x, int y, int width, int height,
                                 ResourceLocation loc, NameCardTexture tex, float alpha) {
        if (loc == null || tex == null || alpha <= 0.01f) return;

        float scale = width / (float) tex.realWidth;
        float scaledHeight = tex.realHeight * scale;

        RenderSystem.enableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.setShaderColor(1f, 1f, 1f, clamp(alpha,0, 1f));

        g.pose().pushPose();
        // 垂直居中卡片
        float yOffset = scaledHeight < height ? (height - scaledHeight) / 2f : 0f;
        g.pose().translate(x, y + yOffset, 0);
        g.pose().scale(scale, scale, 1);

        Minecraft.getInstance().getTextureManager().bindForSetup(loc);

        // 绘制卡片（适配高度）
        if (scaledHeight <= height) {
            g.blit(loc, 0, 0, 0, 0, tex.realWidth, tex.realHeight, tex.realWidth, tex.realHeight);
        } else {
            float uvTop = (scaledHeight - height) / 2f / scaledHeight;
            float uvBottom = 1f - uvTop;
            int drawHeight = (int) (height / scale);
            g.blit(loc, 0, 0, width, drawHeight,
                    0, (int) (uvTop * tex.realHeight),
                    tex.realWidth, (int) ((uvBottom - uvTop) * tex.realHeight),
                    tex.realWidth, tex.realHeight);
        }

        g.pose().popPose();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    /**
     * 渲染团队条纹
     */
    private void renderTeamStripes(GuiGraphics g, int x, int y, int width, int height, float alpha) {
        String team = getNormalizedTeam();
        int baseColor = switch (team) {
            case "t" -> TEAM_COLOR_T;
            case "ct" -> TEAM_COLOR_CT;
            default -> TEAM_COLOR_DEFAULT;
        };
        int stripeColor = SpecHudAPI.style().stripeColor(lastTargetUuid, team, baseColor);

        int mainColor = mulAlpha(stripeColor, alpha);
        int brightColor = mulAlpha(0xFFFFFFFF, alpha * 0.35f);

        // 左侧条纹
        g.fill(x, y, x + TEAM_STRIPE_WIDTH, y + height, mainColor);
        g.fillGradient(x, y, x + TEAM_STRIPE_WIDTH, y + height, brightColor, 0x00000000);

        // 右侧条纹
        g.fill(x + width - TEAM_STRIPE_WIDTH, y, x + width, y + height, mainColor);
        g.fillGradient(x + width - TEAM_STRIPE_WIDTH, y, x + width, y + height, brightColor, 0x00000000);
    }

    /**
     * 渲染玩家详细信息（头像、名称、状态等）
     */
    private void renderPlayerInfo(GuiGraphics g, Minecraft mc, int x, int y, int width, int height, float alpha) {
        int centerX = x + width / 2;
        int centerY = y + height / 2;

        // 渲染头像
        renderAvatar(g, mc, centerX, centerY, alpha);

        // 计算文本位置
        int avatarLeft = centerX - AVATAR_SIZE / 2;
        int textLeft = avatarLeft - PADDING;
        int textRight = avatarLeft + AVATAR_SIZE + PADDING;

        // 渲染名称
        renderPlayerName(g, mc, textLeft, centerY, alpha);

        // 渲染物品信息
        renderItemInfo(g, mc, textLeft, centerY, alpha);

        // 渲染统计信息
        renderPlayerStats(g, mc, textRight, centerY, width, alpha);
    }

    /**
     * 渲染玩家头像
     */
    private void renderAvatar(GuiGraphics g, Minecraft mc, int centerX, int centerY, float alpha) {
        if (lastTargetPlayer == null) return;

        int avatarSize = AVATAR_SIZE;
        int avatarX = centerX - avatarSize / 2;
        int avatarY = centerY - avatarSize / 2;

        g.pose().pushPose();
        g.pose().translate(avatarX, avatarY, 0);
        g.pose().scale(avatarSize / 8f, avatarSize / 8f, 1f);
        g.blit(lastTargetPlayer.getSkinTextureLocation(), 0, 0, 8, 8, 8, 8, 64, 64);
        g.pose().popPose();
    }

    /**
     * 渲染玩家名称
     */
    private void renderPlayerName(GuiGraphics g, Minecraft mc, int leftX, int centerY, float alpha) {
        String playerName = lastTargetPlayer != null ? lastTargetPlayer.getScoreboardName() :
                (lastTargetUuid != null ? lastTargetUuid.toString() : "-");
        float nameScale = SpecHudAPI.style().nameScale(1.5f);
        int lineHeight = mc.font.lineHeight;
        int scaledLineHeight = Math.round(lineHeight * nameScale);
        int textY = centerY - (scaledLineHeight + 2 * lineHeight + 8) / 2; // 8 = 2*gap

        g.pose().pushPose();
        g.pose().translate(leftX - (int) (mc.font.width(playerName) * nameScale), textY, 0);
        g.pose().scale(nameScale, nameScale, 1);
        g.drawString(mc.font, playerName, 0, 0, mulAlpha(TEXT_COLOR_WHITE, alpha), false);
        g.pose().popPose();
    }

    /**
     * 渲染物品信息
     */
    private void renderItemInfo(GuiGraphics g, Minecraft mc, int leftX, int centerY, float alpha) {
        String itemNameCn = I18n.get("blockoffensive.spectator.item.empty");
        String itemNameEn = ITEM_EMPTY_EN;

        if (lastTargetPlayer != null) {
            ItemStack mainHandItem = lastTargetPlayer.getMainHandItem();
            if (!mainHandItem.isEmpty()) {
                itemNameCn = mainHandItem.getHoverName().getString();
                itemNameEn = getEnglishItemName(mainHandItem);
            }
        }

        float nameScale = SpecHudAPI.style().nameScale(1.5f);
        int lineHeight = mc.font.lineHeight;
        int scaledLineHeight = Math.round(lineHeight * nameScale);
        int gap = 4;

        int cnY = centerY - (scaledLineHeight + 2 * lineHeight + 2 * gap) / 2 + scaledLineHeight + gap;
        int enY = cnY + lineHeight + gap;

        g.drawString(mc.font, itemNameCn, leftX - mc.font.width(itemNameCn), cnY,
                mulAlpha(TEXT_COLOR_ITEM, alpha), false);
        g.drawString(mc.font, itemNameEn, leftX - mc.font.width(itemNameEn), enY,
                mulAlpha(TEXT_COLOR_ITEM_EN, alpha), false);
    }

    /**
     * 渲染玩家统计信息（爆头率、击杀数、生命值）
     */
    private void renderPlayerStats(GuiGraphics g, Minecraft mc, int rightX, int centerY, int panelWidth, float alpha) {
        int lineHeight = mc.font.lineHeight + 4;
        int startY = centerY - (lineHeight * 3 - 4) / 2;

        // 渲染爆头率
        String headshotText = I18n.get("blockoffensive.spectator.stat.headshot", clamp(shownHeadshotRate,0, 1f) * 100f);
        g.drawString(mc.font, headshotText, rightX, startY,
                mulAlpha(TEXT_COLOR_HEADSHOT, alpha), false);

        // 渲染击杀数
        int kills = Math.round(shownKills);
        g.drawString(mc.font, I18n.get("blockoffensive.spectator.stat.kills", kills), rightX, startY + lineHeight,
                mulAlpha(TEXT_COLOR_KILLS, alpha), false);

        // 渲染生命值条
        renderHealthBar(g, mc, rightX, startY + lineHeight * 2, panelWidth, alpha);
    }

    /**
     * 渲染生命值条
     */
    private void renderHealthBar(GuiGraphics g, Minecraft mc, int x, int y, int panelWidth, float alpha) {
        float health = shownHealth;
        float maxHealth = Math.max(1f, shownMaxHealth);
        float healthPercent = clamp(health / maxHealth,0, 1f);

        int barWidth = Math.max(100, panelWidth / 4);
        int barHeight = 10;

        // 绘制背景
        g.fill(x, y, x + barWidth, y + barHeight, mulAlpha(HP_BAR_BACKGROUND, alpha));

        // 计算渐变颜色
        int leftColor, rightColor;
        if (healthPercent < 0.5f) {
            float t = healthPercent / 0.5f;
            leftColor = lerpColor(0xFFFF4D4D, 0xFFFFAA33, t);
            rightColor = lerpColor(0xFFCC3A3A, 0xFFFF8C1A, t);
        } else {
            float t = (healthPercent - 0.5f) / 0.5f;
            leftColor = lerpColor(0xFFFFAA33, 0xFF36C06E, t);
            rightColor = lerpColor(0xFFFF8C1A, 0xFF2DA45C, t);
        }

        // 应用透明度并绘制
        leftColor = mulAlpha(leftColor, alpha);
        rightColor = mulAlpha(rightColor, alpha);
        int fillWidth = Math.round(barWidth * healthPercent);
        g.fillGradient(x, y, x + fillWidth, y + barHeight, leftColor, rightColor);

        // 绘制生命值文本
        String healthText = String.format(FORMAT_HEALTH, health, maxHealth);
        drawCenteredScaledString(g, mc, healthText, x, y, barWidth, barHeight, mulAlpha(TEXT_COLOR_WHITE, alpha));
    }

    /**
     * 标准化团队名称
     */
    private String getNormalizedTeam() {
        if (lastTargetPlayer != null && lastTargetPlayer.getTeam() != null) {
            String team = normalizeTeam(lastTargetPlayer.getTeam().getName());
            if (!TEAM_NONE.equals(team)) return team;
        }

        Optional<ClientTeam> team = FPSMClient.getGlobalData().getTeamByUUID(lastTargetUuid);
        return team.isPresent() ? normalizeTeam(team.get().name) : TEAM_NONE;
    }

    /**
     * 标准化团队名称格式
     */
    private static String normalizeTeam(String raw) {
        if (raw == null) return TEAM_NONE;

        String trimmed = raw.trim().toLowerCase();
        if (trimmed.contains("ct")) return "ct";
        if (trimmed.contains("t") || trimmed.contains("terror")) return "t";
        if (trimmed.contains("spec")) return TEAM_SPECTATOR;
        return trimmed;
    }

    /**
     * 获取物品的英文名称
     */
    private static String getEnglishItemName(ItemStack stack) {
        String descriptionId = stack.getDescriptionId();
        if (descriptionId.startsWith("item.")) {
            descriptionId = descriptionId.substring(5);
        }

        int lastDotIndex = descriptionId.lastIndexOf('.');
        if (lastDotIndex == -1) return descriptionId;

        String[] parts = descriptionId.substring(lastDotIndex + 1).split("_");
        for (int i = 0; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                parts[i] = Character.toUpperCase(parts[i].charAt(0)) + parts[i].substring(1).toLowerCase();
            }
        }
        return String.join(" ", parts);
    }
}
