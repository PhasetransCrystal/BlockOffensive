package net.ptcrys.blockoffensive.client.screen.hud;

import net.ptcrys.blockoffensive.BOConfig;
import net.ptcrys.blockoffensive.client.data.CSClientData;
import net.ptcrys.blockoffensive.client.screen.hud.animation.EnderKillAnimator;
import net.ptcrys.blockoffensive.client.screen.hud.animation.KillAnimator;
import net.ptcrys.blockoffensive.compat.BOImpl;
import net.ptcrys.blockoffensive.compat.HitIndicationCompat;
import net.ptcrys.blockoffensive.data.DeathMessage;
import net.ptcrys.fpsmatch.common.attributes.ammo.BulletproofArmorAttribute;
import net.ptcrys.fpsmatch.common.client.FPSMClient;
import net.ptcrys.fpsmatch.common.client.screen.hud.IHudRenderer;
import net.ptcrys.fpsmatch.compat.gun.GunCompatManager;
import net.ptcrys.fpsmatch.util.RenderUtil;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.fml.ModList;

import com.mojang.blaze3d.systems.RenderSystem;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.index.ClientGunIndex;
import com.tacz.guns.client.resource.pojo.display.gun.AmmoCountStyle;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.util.AttachmentDataUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static net.ptcrys.blockoffensive.client.screen.hud.CSGameTabRenderer.GUI_ICONS_LOCATION;

public class CSGameHud implements IHudRenderer {

    private static final CSGameHud INSTANCE = new CSGameHud();
    private final CSMvpHud mvpHud = new CSMvpHud();
    private final CSDeathMessageHud deathMessageHud = new CSDeathMessageHud();
    private final CSGameOverlay gameOverlay = new CSGameOverlay();
    private final CSDMOverlay dmOverlay = new CSDMOverlay();
    private final CSSpectatorHudOverlay spectatorHudOverlay = new CSSpectatorHudOverlay();
    private static final ResourceLocation SEMI = ResourceLocation.tryBuild("tacz", "textures/hud/fire_mode_semi.png");
    private static final ResourceLocation AUTO = ResourceLocation.tryBuild("tacz", "textures/hud/fire_mode_auto.png");
    private static final ResourceLocation BURST = ResourceLocation.tryBuild("tacz", "textures/hud/fire_mode_burst.png");
    private static final int MOVE_DURATION = 500; // 移动动画时长（毫秒）
    private static final int FADE_DURATION = 500; // 淡出动画时长（毫秒）
    private static final int SELECTED_BG_COLOR = RenderUtil.color(255, 255, 255, 65); // 选中时的背景颜色（半透明白）
    private final Animation[] slotAnimations = new Animation[9]; // 扩展到7个槽位
    private KillAnimator killAnimator = new EnderKillAnimator();
    private boolean isStarted = false;
    private volatile CSHudSafeAreaLayouts.HudGeometry frameGeometry;
    private Boolean lastSpectatorMode;

    /** Scoreboard overlay is drawn whenever CSGameHud is the active game HUD. */
    public boolean isScoreboardOccupying() {
        return true;
    }

    public CSDeathMessageHud deathMessageHud() {
        return deathMessageHud;
    }

    public CSHudSafeAreaLayouts.HudGeometry currentFrameGeometry() {
        return frameGeometry;
    }

    public static CSGameHud getInstance() {
        return INSTANCE;
    }

    public CSGameHud() {
        for (int i = 0; i < 9; i++) {
            slotAnimations[i] = new Animation();
        }
    }

    public CSDeathMessageHud getDeathMessageHud() {
        return deathMessageHud;
    }

    public CSGameOverlay getGameOverlay() {
        return gameOverlay;
    }

    public CSMvpHud getMvpHud() {
        return mvpHud;
    }

    public void setKillAnimator(KillAnimator killAnimator) {
        if (killAnimator == null) return;
        this.killAnimator = killAnimator;
    }

    public void addKill(DeathMessage deathMessage) {
        if (BOImpl.isGD656KillIconLoaded()) return;

        if (killAnimator.isActive() || isStarted) {
            killAnimator.addKill(deathMessage);
        } else {
            killAnimator.start(deathMessage);
            isStarted = true;
        }
    }

    public void stopKillAnim() {
        killAnimator.reset();
        isStarted = false;
    }

    public void reset() {
        mvpHud.resetAnimation();
        stopKillAnim();
        deathMessageHud.reset();
        CSVoteHud.getInstance().reset();
        CSSpectatorRoster.getInstance().reset();
        spectatorHudOverlay.reset();
        lastSpectatorMode = null;
        frameGeometry = null;
    }

    private void syncSpectatorMode(boolean spectator) {
        if (lastSpectatorMode != null && lastSpectatorMode != spectator) {
            spectatorHudOverlay.reset();
            if (!spectator) {
                CSSpectatorRoster.getInstance().reset();
            }
        }
        lastSpectatorMode = spectator;
    }

    private CSHudSafeAreaLayouts.HudGeometry beginFrameGeometry(
                                                                int screenWidth,
                                                                int screenHeight,
                                                                boolean spectator) {
        boolean deathmatch = FPSMClient.getGlobalData().isCurrentGameType("csdm");
        Map<String, List<PlayerInfo>> teams = RenderUtil.getTeamsPlayerInfo();
        int ctPlayers = teams.getOrDefault("ct", List.of()).size();
        int tPlayers = teams.getOrDefault("t", List.of()).size();
        Font font = Minecraft.getInstance().font;
        int moneyWidth = Math.max(64, font.width("$ " + CSClientData.getMoney()) * 2);
        int moneyHeight = Math.max(18, font.lineHeight * 2);
        int combatLeftExtent = Math.max(72, font.width("000") * 2 + 24);
        int combatRightExtent = Math.max(72, font.width("9999") * 2 + 28);
        CSHudSafeAreaLayouts.HudGeometry geometry = CSHudSafeAreaLayouts.geometry(
                deathmatch ? CSHudSafeAreaLayouts.GameType.CSDM : CSHudSafeAreaLayouts.GameType.CS,
                screenWidth,
                screenHeight,
                ctPlayers,
                tPlayers,
                spectator,
                !deathmatch && CSBombFuseHud.getInstance().isRendering(),
                CSVoteHud.getInstance().isRendering(),
                moneyWidth,
                moneyHeight,
                combatLeftExtent,
                combatRightExtent,
                Math.max(20, font.lineHeight * 2 + 4),
                !deathmatch && CSClientData.isWaiting,
                !deathmatch && CSClientData.dismantleBombProgress > 0);
        frameGeometry = geometry;
        return geometry;
    }

    @Override
    public void onSpectatorRender(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        syncSpectatorMode(true);
        CSHudSafeAreaLayouts.HudGeometry geometry = beginFrameGeometry(screenWidth, screenHeight, true);
        if (!FPSMClient.getGlobalData().isCurrentGameType("csdm")) {
            gameOverlay.render(
                    guiGraphics,
                    screenWidth,
                    screenHeight,
                    (CSHudSafeAreaLayouts.CsScoreboardLayout) geometry.scoreboardLayout(),
                    Optional.empty());
        } else {
            dmOverlay.render(guiGraphics, (CSHudSafeAreaLayouts.CsdmScoreboardLayout) geometry.scoreboardLayout());
        }
        deathMessageHud.render(guiGraphics);
        spectatorHudOverlay.render(guiGraphics);
        mvpHud.render(guiGraphics, screenWidth, screenHeight);
        geometry.topStatus().bombFuse().ifPresent(rect -> CSBombFuseHud.getInstance().render(guiGraphics, rect));
        CSSpectatorRoster.getInstance().render(guiGraphics, screenWidth, screenHeight);
        geometry.topStatus().vote().ifPresent(rect -> CSVoteHud.getInstance().render(guiGraphics, rect));
    }

    @Override
    public void onPlayerRender(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        syncSpectatorMode(false);
        Minecraft mc = Minecraft.getInstance();
        CSHudSafeAreaLayouts.HudGeometry geometry = beginFrameGeometry(screenWidth, screenHeight, false);
        if (BOImpl.isHitIndicationLoaded()) {
            HitIndicationCompat.Renderer.render(gui.getMinecraft().getWindow(), guiGraphics);
        }
        if (!FPSMClient.getGlobalData().isCurrentGameType("csdm")) {
            gameOverlay.render(
                    guiGraphics,
                    screenWidth,
                    screenHeight,
                    (CSHudSafeAreaLayouts.CsScoreboardLayout) geometry.scoreboardLayout(),
                    geometry.bottomHud().flatMap(CSHudSafeAreaLayouts.BottomHudLayout::money));
        } else {
            dmOverlay.render(guiGraphics, (CSHudSafeAreaLayouts.CsdmScoreboardLayout) geometry.scoreboardLayout());
        }
        deathMessageHud.render(guiGraphics);
        CSHudSafeAreaLayouts.BottomHudLayout bottomHud = geometry.bottomHud().orElseThrow();
        renderInfoLine(mc, gui, guiGraphics, bottomHud.combatInfo());
        renderItemBar(mc, gui, guiGraphics, bottomHud.itemBar());
        mvpHud.render(guiGraphics, screenWidth, screenHeight);
        PingScreenMarker.render(guiGraphics, screenWidth, screenHeight);
        geometry.topStatus().vote().ifPresent(rect -> CSVoteHud.getInstance().render(guiGraphics, rect));
    }

    public void renderInfoLine(
                               Minecraft mc,
                               ForgeGui gui,
                               GuiGraphics guiGraphics,
                               CSHudSafeAreaLayouts.CombatInfoLayout layout) {
        int lineWidth = layout.lineWidth();
        int lineHeight = 1;
        int fadeWidth = Math.max(1, lineWidth / 10);
        int centerX = layout.centerX();
        int y = layout.baselineY();

        for (int x = -lineWidth / 2; x <= lineWidth / 2; x++) {
            int alpha = 255;
            if (x < -lineWidth / 2 + fadeWidth) {
                alpha = (int) (255 * (x + (float) lineWidth / 2) / (float) fadeWidth);
            } else if (x > lineWidth / 2 - fadeWidth) {
                alpha = (int) (255 * ((float) lineWidth / 2 - x) / (float) fadeWidth);
            }
            int color = (alpha << 24) | 0xFFFFFF;
            guiGraphics.fill(centerX + x, y, centerX + x + 1, y + lineHeight, color);
        }

        renderHealthBar(mc, gui, guiGraphics, centerX, lineWidth, y);
        if (mc.player != null) {
            Inventory inv = mc.player.getInventory();
            ItemStack selectItem = mc.player.getInventory().getItem(inv.selected);
            if (GunCompatManager.isGun(selectItem)) {
                renderGunInfo(mc, gui, guiGraphics, selectItem, centerX, lineWidth, y);
            }
        }

        renderCombatKillTips(mc, gui, guiGraphics, centerX, y);
    }

    public void renderHealthBar(Minecraft mc, ForgeGui gui, GuiGraphics guiGraphics, int centerX, int lineWidth, int y) {
        LocalPlayer player = mc.player;
        if (player != null) {
            int health = (int) player.getHealth();
            int maxHealth = (int) player.getMaxHealth();
            float healthPercent = (float) health / maxHealth;
            Font font = mc.font;

            // Render health number
            int tempWidth = font.width("000") * 2;
            String healthText = String.valueOf((int) (healthPercent * 100));
            int healthTextX = centerX - lineWidth / 2 - 10 - tempWidth;

            int healthTextY = y - font.lineHeight + 1;
            int healthBarY = y + font.lineHeight;
            int healthBarHeight = 3;
            int healthBarFillWidth = (int) (healthPercent * tempWidth);

            renderArmorBar(mc, gui, guiGraphics, healthTextX, healthTextY);

            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(healthTextX + (float) tempWidth / 2 - (float) font.width(healthText), healthTextY, 0);
            guiGraphics.pose().scale(2, 2, 0);
            guiGraphics.drawString(font, healthText, 0, 0, 0xFFFFFFFF, false);
            guiGraphics.pose().popPose();

            // Render health bar
            guiGraphics.fill(healthTextX, healthBarY, healthTextX + tempWidth, healthBarY + healthBarHeight, 0x80000000); // Background
            guiGraphics.fill(healthTextX, healthBarY, healthTextX + healthBarFillWidth, healthBarY + healthBarHeight, 0x8000FF00); // Fill
        }
    }

    public void renderArmorBar(Minecraft mc, ForgeGui gui, GuiGraphics guiGraphics, int healthTextX, int healthTextY) {
        if (BulletproofArmorAttribute.Client.bpAttributeDurability == 0) return;
        Font font = mc.font;
        String text = String.valueOf(BulletproofArmorAttribute.Client.bpAttributeDurability);
        int width = font.width(text);
        guiGraphics.blit(GUI_ICONS_LOCATION, healthTextX - 9, healthTextY, BulletproofArmorAttribute.Client.bpAttributeHasHelmet ? 34 : 25, 9, 9, 9);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(healthTextX - width + 1, healthTextY + 6, 0);
        guiGraphics.drawString(font, text, 0, 0, 0xFFFFFFFF, false);
        guiGraphics.pose().popPose();
    }

    private void renderGunInfo(Minecraft mc, ForgeGui gui, GuiGraphics guiGraphics, ItemStack stack, int centerX, int lineWidth, int y) {
        if (!ModList.get().isLoaded("tacz")) return;
        com.tacz.guns.api.item.IGun iGun = (com.tacz.guns.api.item.IGun) stack.getItem();
        ResourceLocation var27 = iGun.getGunId(stack);
        GunData gunData = TimelessAPI.getClientGunIndex(var27).map(ClientGunIndex::getGunData).orElse(null);
        GunDisplayInstance display = TimelessAPI.getGunDisplay(stack).orElse(null);
        if (gunData == null || display == null || mc.player == null) return;

        int cacheMaxAmmoCount = AttachmentDataUtils.getAmmoCountWithAttachment(stack, gunData);
        int ammoCount = iGun.getCurrentAmmoCount(stack) + (iGun.hasBulletInBarrel(stack) && gunData.getBolt() != Bolt.OPEN_BOLT ? 1 : 0);
        int cacheInventoryAmmoCount = 0;
        String currentAmmoCountText;
        if (display.getAmmoCountStyle() == AmmoCountStyle.PERCENT) {
            currentAmmoCountText = String.valueOf((float) ammoCount / (cacheMaxAmmoCount == 0 ? 1.0F : (float) cacheMaxAmmoCount));
        } else {
            currentAmmoCountText = String.valueOf(ammoCount);
        }

        Inventory inventory = mc.player.getInventory();
        FireMode fireMode = com.tacz.guns.api.item.IGun.getMainhandFireMode(mc.player);
        ResourceLocation fireModeTexture = switch (fireMode) {
            case AUTO -> AUTO;
            case BURST -> BURST;
            default -> SEMI;
        };

        if (IGunOperator.fromLivingEntity(mc.player).needCheckAmmo()) {
            if (iGun.useDummyAmmo(stack)) {
                cacheInventoryAmmoCount = iGun.getDummyAmmoAmount(stack);
            } else {
                for (int i = 0; i < inventory.getContainerSize(); ++i) {
                    ItemStack inventoryItem = inventory.getItem(i);
                    Item var5 = inventoryItem.getItem();
                    if (var5 instanceof IAmmo iAmmo) {
                        if (iAmmo.isAmmoOfGun(stack, inventoryItem)) {
                            cacheInventoryAmmoCount += inventoryItem.getCount();
                        }
                    }

                    var5 = inventoryItem.getItem();
                    if (var5 instanceof IAmmoBox iAmmoBox) {
                        if (iAmmoBox.isAmmoBoxOfGun(stack, inventoryItem)) {
                            if (iAmmoBox.isAllTypeCreative(inventoryItem) || iAmmoBox.isCreative(inventoryItem)) {
                                cacheInventoryAmmoCount = 9999;
                                break;
                            }

                            cacheInventoryAmmoCount += iAmmoBox.getAmmoCount(inventoryItem);
                        }
                    }
                }
            }
        } else {
            cacheInventoryAmmoCount = 9999;
        }
        String inventoryAmmoCountText = String.valueOf(cacheInventoryAmmoCount);
        Font font = mc.font;
        int tempWidth = font.width(currentAmmoCountText) * 2;
        int invAmmoTextX = centerX + lineWidth / 2 + 10;
        int textY = y - font.lineHeight + 1;

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(invAmmoTextX, textY, 0);
        guiGraphics.pose().scale(2, 2, 0);
        guiGraphics.drawString(font, currentAmmoCountText, 0, 0, ammoCount == 0 ? 0xFFFF0000 : 0xFFFFFFFF, false);
        guiGraphics.pose().popPose();

        int sY = y - (font.lineHeight / 2);
        int ttt = invAmmoTextX + tempWidth + 5;

        guiGraphics.fill(ttt, sY - 1, ttt + 1, sY + font.lineHeight + 1, 0xFFFFFFFF);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(ttt + 3.5, y - (font.lineHeight * 1.5F / 2) + 0.5F, 0);
        guiGraphics.pose().scale(1.5F, 1.5F, 0);
        guiGraphics.drawString(font, inventoryAmmoCountText, 0, 0, cacheInventoryAmmoCount == 0 ? 0xFFFF0000 : 0xFFFFFFFF, false);
        guiGraphics.pose().popPose();

        guiGraphics.pose().pushPose();
        RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        guiGraphics.pose().translate(ttt + font.width(inventoryAmmoCountText) * 1.5 + 5.5, y - 4.5F, 0);
        guiGraphics.blit(fireModeTexture, 0, 0, 0.0F, 0.0F, 10, 10, 10, 10);
        guiGraphics.pose().popPose();
    }

    public void renderItemBar(
                              Minecraft mc,
                              ForgeGui gui,
                              GuiGraphics guiGraphics,
                              CSHudSafeAreaLayouts.ItemBarLayout layout) {
        if (mc.player == null) return;

        float scale = layout.scale();
        if (scale <= 0.0f) return;
        final int marginRight = Math.max(1, Math.round(10 * scale));
        final int rectWidth = Math.max(1, Math.round(40 * scale));
        final int rectHeight = Math.max(1, Math.round(20 * scale));
        final int squareSize = Math.max(1, Math.round(20 * scale));
        final int spacing = Math.max(1, Math.round(5 * scale));
        final int outer = Math.max(1, Math.round(3 * scale));
        final int maxOffset = Math.max(1, Math.round(15 * scale));
        final int TEXT_COLOR = 0xFFFFFFFF;
        final int moveDuration = 250;

        Player player = mc.player;
        Font font = mc.font;
        Inventory inv = player.getInventory();
        int selectedSlot = inv.selected;
        int left = layout.bounds().x();
        int top = layout.bounds().y();
        int right = layout.bounds().right();

        for (int i = 0; i < 3; i++) {
            Animation anim = slotAnimations[i];
            boolean isSelected = (selectedSlot == i);
            int baseX = right - marginRight - rectWidth;
            int baseY = top + i * (rectHeight + spacing);

            handleAnimationState(anim, isSelected);
            int offsetX = 0;
            int bgColor = calculateBackgroundColor(anim, isSelected, true);
            if (isSelected && bgColor == SELECTED_BG_COLOR) {
                long moveElapsed = System.currentTimeMillis() - anim.moveStartTime;
                float progress = Math.min(moveElapsed / (float) moveDuration, 1.0f);
                offsetX = Math.round(-maxOffset * (1 - progress));
            }
            renderSlot(guiGraphics, font, inv, i,
                    baseX + offsetX - outer, baseY,
                    rectWidth + outer, rectHeight + outer,
                    bgColor, TEXT_COLOR);
        }

        int squareAreaY = top + 3 * (rectHeight + spacing) + spacing;
        int totalSquareWidth = 6 * squareSize + 3 * outer;
        int squareAnchorX = Math.max(left, right - marginRight - totalSquareWidth);

        for (int i = 3; i < 9; i++) {
            Animation anim = slotAnimations[i];
            boolean isSelected = (selectedSlot == i);
            int indexInRow = i - 3;
            int baseX = squareAnchorX + indexInRow * (squareSize + outer);

            handleAnimationState(anim, isSelected);
            int bgColor = calculateBackgroundColor(anim, isSelected, false);
            renderSlot(guiGraphics, font, inv, i,
                    baseX, squareAreaY,
                    squareSize, squareSize,
                    bgColor, TEXT_COLOR);
        }
    }

    // 通用状态处理方法
    private void handleAnimationState(Animation anim, boolean isSelected) {
        if (isSelected != anim.wasSelected) {
            if (isSelected) {
                anim.moveStartTime = System.currentTimeMillis();
                anim.fadeStartTime = 0;
            } else {
                anim.fadeStartTime = System.currentTimeMillis();
            }
            anim.wasSelected = isSelected;
        }
    }

    // 通用背景颜色计算
    private int calculateBackgroundColor(Animation anim, boolean isSelected, boolean isVerticalSlot) {
        if (isSelected) {
            // 入场动画阶段（仅竖排需要检查动画时间）
            if (isVerticalSlot) {
                long moveElapsed = System.currentTimeMillis() - anim.moveStartTime;
                if (moveElapsed < MOVE_DURATION) {
                    return SELECTED_BG_COLOR;
                }
            }
            return SELECTED_BG_COLOR; // 保持选中状态
        } else if (anim.fadeStartTime > 0) {
            // 淡出动画阶段
            long fadeElapsed = System.currentTimeMillis() - anim.fadeStartTime;
            if (fadeElapsed < FADE_DURATION) {
                float progress = fadeElapsed / (float) FADE_DURATION;
                int alpha = (int) (128 * (1 - progress));
                return (alpha << 24) | 0x00FFFFFF;
            }
            anim.fadeStartTime = 0; // 结束淡出
        }
        return 0x00000000; // 默认透明
    }

    // 通用槽位渲染方法
    private void renderSlot(GuiGraphics guiGraphics, Font font, Inventory inv, int slotIndex,
                            int x, int y, int width, int height,
                            int bgColor, int textColor) {
        // 绘制背景
        guiGraphics.fill(x, y, x + width, y + height, bgColor);

        // 物品渲染
        ItemStack stack = inv.getItem(slotIndex);
        int itemX = x + (width - 16) / 2;
        int itemY = y + (height - 16) / 2;
        guiGraphics.renderItem(inv.player, stack, itemX, itemY, slotIndex);
        guiGraphics.renderItemDecorations(font, stack, itemX, itemY);

        // 槽位编号
        KeyMapping keyMapping = Minecraft.getInstance().options.keyHotbarSlots[slotIndex];
        Component key = keyMapping.getKey().getDisplayName();
        guiGraphics.drawString(font, key, x + width - font.width(key) - 1, y + 1, textColor, true);
        if (slotIndex + 1 <= 3) {
            // 渲染名称
            if (!stack.isEmpty() && inv.selected == slotIndex) {
                String itemName = stack.getHoverName().getString();
                float nameWidth = font.width(itemName) * 0.5F;
                float nameX = x + (width - nameWidth - 2);
                float nameY = y + height - 10 + font.lineHeight * 0.5f;
                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate(nameX, nameY, 0);
                guiGraphics.pose().scale(0.5F, 0.5F, 0);
                guiGraphics.drawString(font, itemName, 0, 0, textColor, true);
                guiGraphics.pose().popPose();
            }
        }
    }

    public void renderCombatKillTips(Minecraft mc, ForgeGui gui, GuiGraphics guiGraphics, int centerX, int y) {
        if (!BOConfig.client.killIconHudEnabled.get()) return;
        killAnimator.render(mc, gui, guiGraphics, centerX, y);
    }

    @Override
    public void onRenderGuiOverlayPre(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay() == VanillaGuiOverlay.PLAYER_HEALTH.type() || event.getOverlay() == VanillaGuiOverlay.ARMOR_LEVEL.type() || event.getOverlay() == VanillaGuiOverlay.FOOD_LEVEL.type() || event.getOverlay() == VanillaGuiOverlay.HOTBAR.type() || event.getOverlay() == VanillaGuiOverlay.EXPERIENCE_BAR.type() || event.getOverlay() == VanillaGuiOverlay.MOUNT_HEALTH.type() || event.getOverlay().id().getPath().equals("tac_gun_hud_overlay")) {
            event.setCanceled(true);
        }
    }

    private static class Animation {

        long moveStartTime = 0;    // 入场动画开始时间
        long fadeStartTime = 0;    // 淡出动画开始时间
        boolean wasSelected = false; // 上次选中状态
    }
}
