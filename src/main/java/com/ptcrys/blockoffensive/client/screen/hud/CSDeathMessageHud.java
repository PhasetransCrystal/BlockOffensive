package com.ptcrys.blockoffensive.client.screen.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ptcrys.blockoffensive.BOConfig;
import com.ptcrys.blockoffensive.BlockOffensive;
import com.ptcrys.blockoffensive.compat.BOImpl;
import com.ptcrys.blockoffensive.compat.CSGrenadeCompat;
import com.ptcrys.blockoffensive.data.DeathMessage;
import com.ptcrys.blockoffensive.data.DeathMessageRules;
import com.ptcrys.blockoffensive.item.BOItemRegister;
import com.ptcrys.fpsmatch.common.item.FPSMItemRegister;
import com.ptcrys.fpsmatch.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

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
        int showTimeMs = BOConfig.client.messageShowTime.get() * 1000;
        int yOffset = getHudPositionYOffset();

        synchronized(queueLock) {
            messageQueue.removeIf(messageData ->
                    currentTime - messageData.displayStartTime >= showTimeMs);

            for (MessageData messageData : messageQueue) {
                DeathMessage message = messageData.message;
                // 宽度只计算一次，避免 renderKillMessage 内部重复计算
                int width = calculateMessageWidth(message);
                int x = getHudPositionXOffset(width);

                renderKillMessage(guiGraphics, message, x, yOffset, width);

                yOffset += 16;
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
            int max = 1;
            for (MessageData data : messageQueue) {
                max = Math.max(max, calculateMessageWidth(data.message));
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
            int showTimeMs = BOConfig.client.messageShowTime.get() * 1000;

            messageQueue.removeIf(messageData ->
                    currentTime - messageData.displayStartTime >= showTimeMs);

            if (messageQueue.size() >= BOConfig.client.maxShowCount.get()) {
                messageQueue.removeFirst();
            }

            messageQueue.add(new MessageData(message, currentTime));
        }
    }

    private int getHudPositionYOffset() {
        return switch (BOConfig.client.killMessageHudPosition.get()) {
            case 1, 2 -> 10;
            default -> minecraft.getWindow().getGuiScaledHeight() - 10 * 5;
        };
    }

    private int getHudPositionXOffset(int stringWidth) {
        return switch (BOConfig.client.killMessageHudPosition.get()) {
            case 2, 4 -> minecraft.getWindow().getGuiScaledWidth() - 10 - stringWidth;
            default -> 10;
        };
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

    private void renderKillMessage(GuiGraphics guiGraphics, DeathMessage message, int x, int y, int width) {
        PoseStack poseStack = guiGraphics.pose();
        Font font = minecraft.font;
        UUID local = minecraft.player.getUUID();
        boolean isLocalPlayer = message.getKillerUUID().equals(local) || Objects.equals(message.getAssistUUID(), local);

        int height = 16;
        int bgColor = 0x80000000;

        guiGraphics.fill(x, y, x + width, y + height, bgColor);

        if (isLocalPlayer) {
            guiGraphics.fill(x, y, x + width, y + 1, 0xFFFF0000);
            guiGraphics.fill(x, y + height - 1, x + width, y + height, 0xFFFF0000);
            guiGraphics.fill(x, y, x + 1, y + height, 0xFFFF0000);
            guiGraphics.fill(x + width - 1, y, x + width, y + height, 0xFFFF0000);
        }

        boolean isSuicide = DeathMessageRules.isSuicide(message.getDeadUUID(), message.getKillerUUID());

        int currentX = x + 5;
        int rightPadding = x + width - 5;

        if (message.isBlinded()) {
            currentX = renderConditionalIcon(guiGraphics, "blindness", currentX, y);
        }

        MutableComponent component = isSuicide ? message.getDead().copy() : message.getKiller().copy();
        if(hasDistinctAssist(message.getAssistUUID(), message.getKillerUUID())){
            component.append(" + ");
            component.append(message.getAssist());
        }

        guiGraphics.drawString(font, component, currentX, y + 4, -1, true);
        currentX += font.width(component) + 2;

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

        int deadNameWidth = font.width(message.getDead());
        currentX = Math.min(currentX, rightPadding - deadNameWidth);
        guiGraphics.drawString(font, message.getDead(), currentX, y + 4, -1, true);
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

    private int calculateMessageWidth(DeathMessage message) {
        Font font = minecraft.font;
        int width = 10;

        boolean isSuicide = DeathMessageRules.isSuicide(message.getDeadUUID(), message.getKillerUUID());

        if (message.isBlinded()) {
            width += 14;
        }

        MutableComponent killerComponent = isSuicide ? message.getDead().copy() : message.getKiller().copy();
        if (hasDistinctAssist(message.getAssistUUID(), message.getKillerUUID())) {
            killerComponent.append(" + ").append(message.getAssist());
        }
        width += font.width(killerComponent) + 2;

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

        width += font.width(message.getDead());

        return width;
    }

    public void reset(){
        messageQueue.clear();
    }

    public record MessageData(DeathMessage message, long displayStartTime) {
    }
}
