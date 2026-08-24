package com.ptcrys.blockoffensive.client.screen.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ptcrys.blockoffensive.BOConfig;
import com.ptcrys.blockoffensive.BlockOffensive;
import com.ptcrys.blockoffensive.compat.BOImpl;
import com.ptcrys.blockoffensive.compat.CSGrenadeCompat;
import com.ptcrys.blockoffensive.data.DeathMessage;
import com.ptcrys.blockoffensive.data.DeathMessageRules;
import com.ptcrys.blockoffensive.item.BOItemRegister;
import com.ptcrys.blockoffensive.minimap.CSHudSafeAreaLayouts;
import com.ptcrys.blockoffensive.minimap.CSKillFeedGeometry;
import com.ptcrys.fpsmatch.common.item.FPSMItemRegister;
import com.ptcrys.fpsmatch.core.minimap.hud.ScreenRect;
import com.ptcrys.fpsmatch.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@SuppressWarnings("all")
public class CSDeathMessageHud{
    private final Object queueLock = new Object();
    private final LinkedList<MessageData> messageQueue = new LinkedList<>();
    public final Minecraft minecraft;
    private final Map<String, ResourceLocation> specialKillIcons = new HashMap<>();
    private final Map<ResourceLocation, String> itemToIcon = new HashMap<>();

    public CSDeathMessageHud() {
        minecraft = Minecraft.getInstance();
        // 注册特殊击杀图标
        registerSpecialKillIcon("headshot", ResourceLocation.tryBuild(BlockOffensive.MODID, "textures/ui/cs/message/headshot.png"));
        registerSpecialKillIcon("throw_wall", ResourceLocation.tryBuild(BlockOffensive.MODID, "textures/ui/cs/message/throw_wall.png"));
        registerSpecialKillIcon("throw_smoke", ResourceLocation.tryBuild(BlockOffensive.MODID, "textures/ui/cs/message/throw_smoke.png"));
        registerSpecialKillIcon("explode", ResourceLocation.tryBuild(BlockOffensive.MODID, "textures/ui/cs/message/explode.png"));
        registerSpecialKillIcon("suicide", ResourceLocation.tryBuild(BlockOffensive.MODID, "textures/ui/cs/message/suicide.png"));
        registerSpecialKillIcon("fire", ResourceLocation.tryBuild(BlockOffensive.MODID, "textures/ui/cs/message/fire.png"));
        registerSpecialKillIcon("blindness", ResourceLocation.tryBuild(BlockOffensive.MODID, "textures/ui/cs/message/blindness.png"));
        registerSpecialKillIcon("no_zoom", ResourceLocation.tryBuild(BlockOffensive.MODID, "textures/ui/cs/message/no_zoom.png"));
        registerSpecialKillIcon("ct_incendiary_grenade", ResourceLocation.tryBuild(BlockOffensive.MODID, "textures/ui/cs/message/ct_incendiary_grenade.png"));
        registerSpecialKillIcon("grenade", ResourceLocation.tryBuild(BlockOffensive.MODID, "textures/ui/cs/message/grenade.png"));
        registerSpecialKillIcon("t_incendiary_grenade", ResourceLocation.tryBuild(BlockOffensive.MODID, "textures/ui/cs/message/t_incendiary_grenade.png"));
        registerSpecialKillIcon("flash_bomb", ResourceLocation.tryBuild(BlockOffensive.MODID, "textures/ui/cs/message/flash_bomb.png"));
        registerSpecialKillIcon("smoke_shell", ResourceLocation.tryBuild(BlockOffensive.MODID, "textures/ui/cs/message/smoke_shell.png"));
        registerSpecialKillIcon("fly", ResourceLocation.tryBuild(BlockOffensive.MODID, "textures/ui/cs/message/fly.png"));
        registerSpecialKillIcon("hand", ResourceLocation.tryBuild(BlockOffensive.MODID, "textures/ui/cs/message/hand.png"));

        registerSpecialKillIcon(ForgeRegistries.ITEMS.getKey(Items.AIR),"hand");
        registerSpecialKillIcon(ForgeRegistries.ITEMS.getKey(FPSMItemRegister.CT_INCENDIARY_GRENADE.get()),"ct_incendiary_grenade");
        registerSpecialKillIcon(ForgeRegistries.ITEMS.getKey(FPSMItemRegister.T_INCENDIARY_GRENADE.get()),"t_incendiary_grenade");
        registerSpecialKillIcon(ForgeRegistries.ITEMS.getKey(FPSMItemRegister.GRENADE.get()),"grenade");
        registerSpecialKillIcon(ForgeRegistries.ITEMS.getKey(FPSMItemRegister.FLASH_BOMB.get()),"flash_bomb");
        registerSpecialKillIcon(ForgeRegistries.ITEMS.getKey(FPSMItemRegister.SMOKE_SHELL.get()),"smoke_shell");
        registerSpecialKillIcon(ForgeRegistries.ITEMS.getKey(BOItemRegister.C4.get()),"explode");

        if(BOImpl.isCounterStrikeGrenadesLoaded()){
            CSGrenadeCompat.registerKillIcon(itemToIcon);
        }
    }

    public void render(GuiGraphics guiGraphics) {
        if (BOConfig.client.killMessageHudEnabled.get() && !messageQueue.isEmpty()) {
            if (minecraft.player != null) {
                renderKillTips(guiGraphics);
            }
        }
    }

    private void renderKillTips(GuiGraphics guiGraphics) {
        long currentTime = System.currentTimeMillis();
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int maxRowWidth = maximumRowWidth(screenWidth);

        synchronized(queueLock) {
            messageQueue.removeIf(messageData ->
                    currentTime - messageData.displayStartTime >= BOConfig.client.messageShowTime.get() * 1000);

            if (messageQueue.isEmpty()) {
                return;
            }

            List<RowRenderData> rows = new ArrayList<>(messageQueue.size());
            int widestRow = 1;
            for (MessageData messageData : messageQueue) {
                RowRenderData row = layoutMessage(messageData.message(), maxRowWidth);
                rows.add(row);
                widestRow = Math.max(widestRow, row.width());
            }

            CSKillFeedGeometry.StackGeometry stack = CSKillFeedGeometry.stack(
                    screenWidth,
                    screenHeight,
                    configuredPosition(),
                    rows.size(),
                    widestRow
            );
            int firstVisible = stack.firstVisibleIndex(rows.size());
            for (int rowIndex = 0; rowIndex < stack.visibleRows(); rowIndex++) {
                RowRenderData row = rows.get(firstVisible + rowIndex);
                ScreenRect rect = stack.row(rowIndex, row.width());
                renderKillMessage(guiGraphics, row, rect.x(), rect.y());
            }
        }
    }

    public boolean isRendering() {
        return BOConfig.client.killMessageHudEnabled.get() && !messageQueue.isEmpty();
    }

    public int visibleMessageCount() {
        synchronized (queueLock) {
            return messageQueue.size();
        }
    }

    public int maxVisibleMessageWidth() {
        synchronized (queueLock) {
            if (messageQueue.isEmpty()) {
                return 1;
            }
            int screenWidth = minecraft.getWindow().getGuiScaledWidth();
            int screenHeight = minecraft.getWindow().getGuiScaledHeight();
            int maxRowWidth = maximumRowWidth(screenWidth);
            CSKillFeedGeometry.StackGeometry stack = CSKillFeedGeometry.stack(
                    screenWidth,
                    screenHeight,
                    configuredPosition(),
                    messageQueue.size(),
                    1
            );
            int firstVisible = stack.firstVisibleIndex(messageQueue.size());
            int max = 1;
            for (int index = firstVisible; index < messageQueue.size(); index++) {
                max = Math.max(max, layoutMessage(messageQueue.get(index).message(), maxRowWidth).width());
            }
            return max;
        }
    }

    public int configuredPosition() {
        return BOConfig.client.killMessageHudPosition.get();
    }

    public void addKillMessage(DeathMessage message) {
        synchronized(queueLock) {
            long currentTime = System.currentTimeMillis();

            messageQueue.removeIf(messageData ->
                    currentTime - messageData.displayStartTime >= BOConfig.client.messageShowTime.get() * 1000);

            if (messageQueue.size() >= BOConfig.client.maxShowCount.get()) {
                messageQueue.removeFirst();
            }

            messageQueue.add(new MessageData(message, currentTime));
        }
    }

    private static int maximumRowWidth(int screenWidth) {
        return Math.max(1, screenWidth - CSHudSafeAreaLayouts.KILL_FEED_MARGIN * 2);
    }

    public void registerSpecialKillIcon(String id, ResourceLocation texture) {
        specialKillIcons.put(id, texture);
    }

    public void registerSpecialKillIcon(ResourceLocation item, String id) {
        itemToIcon.put(item, id);
    }

    public static boolean hasDistinctAssist(UUID assistUUID, UUID killerUUID) {
        return DeathMessageRules.hasDistinctAssist(assistUUID, killerUUID);
    }

    private void renderKillMessage(GuiGraphics guiGraphics, RowRenderData row, int x, int y) {
        PoseStack poseStack = guiGraphics.pose();
        Font font = minecraft.font;
        DeathMessage message = row.message();
        UUID local = minecraft.player.getUUID();
        boolean isLocalPlayer = message.getKillerUUID().equals(local) || Objects.equals(message.getAssistUUID(), local);

        int width = row.width();
        int height = 16;
        int bgColor = 0x80000000;

        guiGraphics.fill(x, y, x + width, y + height, bgColor);

        if (isLocalPlayer) {
            guiGraphics.fill(x, y, x + width, y + 1, 0xFFFF0000);
            guiGraphics.fill(x, y + height - 1, x + width, y + height, 0xFFFF0000);
            guiGraphics.fill(x, y, x + 1, y + height, 0xFFFF0000);
            guiGraphics.fill(x + width - 1, y, x + width, y + height, 0xFFFF0000);
        }

        boolean isSuicide = row.suicide();

        int currentX = x + 5;

        if (message.isBlinded()) {
            currentX = renderConditionalIcon(guiGraphics, "blindness", currentX, y);
        }

        guiGraphics.drawString(font, row.firstName().text(), currentX, y + 4, -1, true);
        currentX += row.firstName().width() + 2;

        if(!isSuicide){
            ResourceLocation weaponIcon = message.getWeaponIcon();
            poseStack.pushPose();
            poseStack.translate(currentX, y + 1, 0);
            if (weaponIcon != null) {
                poseStack.scale(0.32f, 0.32f, 1.0f);
                renderWeaponIcon(guiGraphics, weaponIcon);
                currentX += 39;
            }else{
                if(!this.itemToIcon.containsKey(message.getItemRL())){
                    guiGraphics.renderItem(message.getWeapon(),0,0);
                    currentX += 16;
                }
            }
            poseStack.popPose();

            String icon = this.itemToIcon.getOrDefault(message.getItemRL(),null);
            if(icon != null){
                weaponIcon = this.specialKillIcons.getOrDefault(icon,null);
                if(weaponIcon != null) {
                    renderIcon(guiGraphics, weaponIcon, currentX, y + 2, 12, 12);
                    currentX += 14;
                }
            }
        }else{
            renderIcon(guiGraphics, this.specialKillIcons.get("suicide"), currentX, y + 2, 12, 12);
            currentX += 14;
        }

        for (String iconKey : getPostWeaponIconKeys(message)) {
            currentX = renderConditionalIcon(guiGraphics, iconKey, currentX, y);
        }

        guiGraphics.drawString(font, row.secondName().text(), currentX, y + 4, -1, true);
    }

    private int renderConditionalIcon(GuiGraphics guiGraphics, String iconKey, int currentX, int y) {
        ResourceLocation icon = specialKillIcons.get(iconKey);
        if (icon != null) {
            renderIcon(guiGraphics, icon, currentX, y + 2, 12, 12);
        }
        return currentX + 14;
    }

    private static List<String> getPostWeaponIconKeys(DeathMessage message) {
        return DeathMessageRules.getPostWeaponIconKeys(
                message.isFlying(),
                message.isHeadShot(),
                message.isThroughSmoke(),
                message.isThroughWall(),
                message.isNoScope()
        );
    }

    private void renderIcon(GuiGraphics guiGraphics, ResourceLocation icon, int x, int y, int width, int height) {
        guiGraphics.blit(icon, x, y, 0, 0, width, height, width, height);
    }

    private void renderWeaponIcon(GuiGraphics guiGraphics, ResourceLocation icon) {
        RenderUtil.renderReverseTexture(guiGraphics,icon, 0, 0, 117, 44);
    }

    private RowRenderData layoutMessage(DeathMessage message, int maxRowWidth) {
        Font font = minecraft.font;
        boolean isSuicide = DeathMessageRules.isSuicide(message.getDeadUUID(), message.getKillerUUID());
        MutableComponent firstName = isSuicide ? message.getDead().copy() : message.getKiller().copy();
        if (hasDistinctAssist(message.getAssistUUID(), message.getKillerUUID())) {
            firstName.append(" + ").append(message.getAssist());
        }

        int fixedWidth = calculateFixedWidth(message, isSuicide);
        int availableNameWidth = Math.max(0, maxRowWidth - fixedWidth);
        CSKillFeedGeometry.NameBudgets nameBudgets = CSKillFeedGeometry.fitNameBudgets(
                font.width(firstName),
                font.width(message.getDead()),
                availableNameWidth
        );
        FittedText fittedFirst = fitText(font, firstName, nameBudgets.first());
        FittedText fittedSecond = fitText(font, message.getDead(), nameBudgets.second());
        int width = Math.min(maxRowWidth, fixedWidth + fittedFirst.width() + fittedSecond.width());
        return new RowRenderData(message, isSuicide, fittedFirst, fittedSecond, Math.max(1, width));
    }

    private int calculateFixedWidth(DeathMessage message, boolean isSuicide) {
        int width = 12;
        if (message.isBlinded()) {
            width += 14;
        }

        if(!isSuicide){
            ResourceLocation weaponIcon = message.getWeaponIcon();
            if (weaponIcon != null) {
                width += 39;
            } else {
                if (!this.itemToIcon.containsKey(message.getItemRL())) {
                    width += 16;
                }
            }

            String specialIcon = this.itemToIcon.getOrDefault(message.getItemRL(), null);
            if (specialIcon != null && this.specialKillIcons.containsKey(specialIcon)) {
                width += 14;
            }
        }else{
            width += 14;
        }

        width += getPostWeaponIconKeys(message).size() * 14;
        return width;
    }

    private static FittedText fitText(Font font, FormattedText text, int maxWidth) {
        if (maxWidth <= 0) {
            return new FittedText(FormattedCharSequence.EMPTY, 0);
        }

        FormattedText fitted = text;
        if (font.width(text) > maxWidth) {
            int ellipsisWidth = font.width("...");
            if (ellipsisWidth <= maxWidth) {
                fitted = FormattedText.composite(
                        font.substrByWidth(text, maxWidth - ellipsisWidth),
                        FormattedText.of("...")
                );
            } else {
                fitted = font.substrByWidth(text, maxWidth);
            }
        }

        FormattedCharSequence visual = Language.getInstance().getVisualOrder(fitted);
        return new FittedText(visual, font.width(visual));
    }

    public void reset(){
        messageQueue.clear();
    }

    private record FittedText(FormattedCharSequence text, int width) {
    }

    private record RowRenderData(
            DeathMessage message,
            boolean suicide,
            FittedText firstName,
            FittedText secondName,
            int width
    ) {
    }

    public record MessageData(DeathMessage message, long displayStartTime) {
    }
}
