package com.ptcrys.blockoffensive.client.screen.hud;

import com.ptcrys.blockoffensive.util.BOUtil;
import com.ptcrys.fpsmatch.common.client.FPSMClient;
import com.ptcrys.fpsmatch.common.client.tab.TabRenderer;
import com.ptcrys.fpsmatch.core.data.PlayerData;
import com.ptcrys.fpsmatch.core.team.BaseTeam;
import com.ptcrys.fpsmatch.util.RenderUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;

import java.util.*;

public class CSGameTabRenderer implements TabRenderer {
    public static final ResourceLocation GUI_ICONS_LOCATION = ResourceLocation.tryBuild("minecraft","textures/gui/icons.png");
    protected final Minecraft minecraft = Minecraft.getInstance();

    @Override
    public String getGameType() {
        return "cs";
    }

    @Override
    public void render(GuiGraphics guiGraphics, int windowWidth, List<PlayerInfo> playerInfoList, Scoreboard scoreboard, Objective objective) {
        // 列宽定义（总宽度400px）
        int padding = 5;
        int pingWidth = 40;
        int avatarSize = 12;
        int nameWidth = 110;
        int moneyWidth = 40;
        int killWidth = 35;
        int deathWidth = 35;
        int assistWidth = 35;
        int headshotWidth = 40;
        int damageWidth = 48;

        // 玩家信息区域固定尺寸
        int playerAreaWidth = 400;
        int playerRowHeight = 12;
        int playerGap = 2;
        // 减小队伍间隔
        int headerHeight = 12;
        int teamGap = 10; // 队伍之间的间隔

        // 过滤并排序玩家
        Map<String, List<PlayerInfo>> teamPlayers = RenderUtil.getTeamsPlayerInfo(playerInfoList);

        // 按伤害排序
        Comparator<PlayerInfo> damageComparator = (p1, p2) -> {
            Optional<PlayerData> t1 = FPSMClient.getGlobalData().getPlayerData(p1.getProfile().getId());
            Optional<PlayerData> t2 = FPSMClient.getGlobalData().getPlayerData(p2.getProfile().getId());
            return Float.compare(t1.map(PlayerData::getDamage).orElse(0F), t2.map(PlayerData::getDamage).orElse(0F));
        };

        List<PlayerInfo> ctPlayers = new ArrayList<>(teamPlayers.getOrDefault("ct", Collections.emptyList()));
        List<PlayerInfo> tPlayers = new ArrayList<>(teamPlayers.getOrDefault("t", Collections.emptyList()));
        ctPlayers.sort(damageComparator.reversed());
        tPlayers.sort(damageComparator.reversed());

        // 计算实际玩家数量
        int ctPlayerCount = ctPlayers.size();
        int tPlayerCount = tPlayers.size();

        // 计算每个队伍的内容高度
        int ctContentHeight = ctPlayerCount > 0 ? (playerRowHeight + playerGap) * ctPlayerCount - playerGap : 0;
        int tContentHeight = tPlayerCount > 0 ? (playerRowHeight + playerGap) * tPlayerCount - playerGap : 0;

        // 计算总内容高度
        int totalContentHeight = headerHeight + ctContentHeight + teamGap + tContentHeight;

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

        // 计算CT玩家起始Y坐标（表头下方）
        int ctStartY = headerY + headerHeight + 2;

        // 添加中间分割线（CT和T队伍之间）
        int dividerY = ctStartY + ctContentHeight + teamGap / 2;
        guiGraphics.fill(bgX, dividerY, bgX + bgWidth, dividerY + 1, 0x40FFFFFF);

        // 计算T玩家起始Y坐标（分割线下方）
        int tStartY = ctStartY + ctContentHeight + teamGap;

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

        // 金钱
        Component moneyText = Component.translatable("blockoffensive.tab.header.money").withStyle(ChatFormatting.BOLD);
        guiGraphics.drawString(minecraft.font, moneyText,
                currentHeaderX + (moneyWidth - minecraft.font.width(moneyText)) / 2, headerY, 0xFFFFFFFF);
        currentHeaderX += moneyWidth;

        // K/D/A
        Component killsText = Component.translatable("blockoffensive.tab.header.kills").withStyle(ChatFormatting.BOLD);
        guiGraphics.drawString(minecraft.font, killsText,
                currentHeaderX + (killWidth - minecraft.font.width(killsText)) / 2, headerY, 0xFFFFFFFF);
        currentHeaderX += killWidth;

        Component deathsText = Component.translatable("blockoffensive.tab.header.deaths").withStyle(ChatFormatting.BOLD);
        guiGraphics.drawString(minecraft.font, deathsText,
                currentHeaderX + (deathWidth - minecraft.font.width(deathsText)) / 2, headerY, 0xFFFFFFFF);
        currentHeaderX += deathWidth;

        Component assistsText = Component.translatable("blockoffensive.tab.header.assists").withStyle(ChatFormatting.BOLD);
        guiGraphics.drawString(minecraft.font, assistsText,
                currentHeaderX + (assistWidth - minecraft.font.width(assistsText)) / 2, headerY, 0xFFFFFFFF);
        currentHeaderX += assistWidth;

        // 爆头率
        Component headshotText = Component.translatable("blockoffensive.tab.header.headshot").withStyle(ChatFormatting.BOLD);
        guiGraphics.drawString(minecraft.font, headshotText,
                currentHeaderX + (headshotWidth - minecraft.font.width(headshotText)) / 2, headerY, 0xFFFFFFFF);
        currentHeaderX += headshotWidth;

        // 伤害（直接跟在爆头率后面）
        Component damageText = Component.translatable("blockoffensive.tab.header.damage").withStyle(ChatFormatting.BOLD);
        guiGraphics.drawString(minecraft.font, damageText,
                currentHeaderX + (damageWidth - minecraft.font.width(damageText)) / 2, headerY, 0xFFFFFFFF);

        // 渲染CT玩家（从顶部开始）
        int currentY = ctStartY;
        for (PlayerInfo ctPlayer : ctPlayers) {
            renderPlayerRow(guiGraphics, ctPlayer, bgX + bgPadding, currentY, playerAreaWidth, playerRowHeight, BOUtil.CT_COLOR);
            currentY += playerRowHeight + playerGap;
        }

        // 渲染T玩家（从中间往下）
        currentY = tStartY;
        for (PlayerInfo tPlayer : tPlayers) {
            renderPlayerRow(guiGraphics, tPlayer, bgX + bgPadding, currentY, playerAreaWidth, playerRowHeight, BOUtil.T_COLOR);
            currentY += playerRowHeight + playerGap;
        }
    }

    private void renderPlayerRow(GuiGraphics guiGraphics, PlayerInfo player, int x, int y, int width, int height, int textColor) {
        UUID uuid = player.getProfile().getId();
        PlayerData tabData = FPSMClient.getGlobalData().getPlayerData(uuid).orElse(null);
        if (tabData == null) {
            return;
        }
        String playerTeam = FPSMClient.getGlobalData().getTeamByUUID(uuid).map(BaseTeam::getName).orElse("");
        String localTeam = FPSMClient.getGlobalData().getCurrentTeam();
        boolean isSameTeam = Objects.equals(playerTeam, localTeam);
        boolean isLocalPlayer = player.getProfile().getId().equals(minecraft.player.getUUID());

        // 背景 - 如果是本地玩家，使用对应队伍颜色的背景
        int bgColor = isLocalPlayer ? (textColor & 0xFFFFFF) | 0x40000000 : 0x40000000;
        guiGraphics.fill(x, y, x + width, y + height, bgColor);

        // 列宽定义（总宽度400px）
        int padding = 5;
        int pingWidth = 40;
        int avatarSize = 12;
        int nameWidth = 110;
        int moneyWidth = 40;
        int killWidth = 35;
        int deathWidth = 35;
        int assistWidth = 35;
        int headshotWidth = 40;
        int damageWidth = 48;

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
        guiGraphics.drawString(minecraft.font, getNameForDisplay(player), currentX, textY, textColor);

        // 金钱（与表头对齐）
        int moneyX = x + pingWidth + avatarSize + padding + nameWidth;
        // 始终显示高亮背景
        guiGraphics.fill(moneyX, y, moneyX + moneyWidth, y + height, 0x20FFFFFF);
        // 只在同队时显示金钱数值
        if (isSameTeam) {
            String money = "$" + FPSMClient.getGlobalData().getPlayerMoney(player.getProfile().getId());
            guiGraphics.drawString(minecraft.font, money,
                    moneyX + (moneyWidth - minecraft.font.width(money)) / 2, textY, textColor);
        }

        // K/D/A（与表头对齐）
        int kdaX = moneyX + moneyWidth;
        Component kills = Component.literal(String.valueOf(tabData.getKills())).withStyle(ChatFormatting.BOLD);
        guiGraphics.drawString(minecraft.font, kills,
                kdaX + (killWidth - minecraft.font.width(kills)) / 2, textY, textColor);

        int deathsX = kdaX + killWidth;
        guiGraphics.fill(deathsX, y, deathsX + deathWidth, y + height, 0x20FFFFFF);
        Component deaths = Component.literal(String.valueOf(tabData.getDeaths())).withStyle(ChatFormatting.BOLD);
        guiGraphics.drawString(minecraft.font, deaths,
                deathsX + (deathWidth - minecraft.font.width(deaths)) / 2, textY, textColor);

        int assistsX = deathsX + deathWidth;
        Component assists = Component.literal(String.valueOf(tabData.getAssists())).withStyle(ChatFormatting.BOLD);
        guiGraphics.drawString(minecraft.font, assists,
                assistsX + (assistWidth - minecraft.font.width(assists)) / 2, textY, textColor);

        // 爆头率（与表头对齐）
        int headshotX = assistsX + assistWidth;
        guiGraphics.fill(headshotX, y, headshotX + headshotWidth, y + height, 0x20FFFFFF);
        float headShotRate = tabData.getHeadshotRate();
        Component headshotPercentage = Component.literal(String.format("%.0f%%", headShotRate * 100)).withStyle(ChatFormatting.BOLD);
        guiGraphics.drawString(minecraft.font, headshotPercentage,
                headshotX + (headshotWidth - minecraft.font.width(headshotPercentage)) / 2, textY, textColor);

        // 伤害（与表头对齐）
        int damageX = x + width - damageWidth;
        guiGraphics.fill(damageX, y, damageX + damageWidth, y + height, 0x40FFFFFF);
        Component damage = Component.literal(String.valueOf(Math.round(tabData.getDamage()))).withStyle(ChatFormatting.BOLD);
        guiGraphics.drawString(minecraft.font, damage,
                damageX + (damageWidth - minecraft.font.width(damage)) / 2, textY, textColor);

        if(!tabData.isLiving()){
            //渲染一层半透明灰色
            guiGraphics.fill(x, y, x + width, y + height, 0x40000000);
        }
    }

    protected Component getNameForDisplay(PlayerInfo info) {
        return info.getTabListDisplayName() != null ? info.getTabListDisplayName() : Component.literal(info.getProfile().getName());
    }
}
