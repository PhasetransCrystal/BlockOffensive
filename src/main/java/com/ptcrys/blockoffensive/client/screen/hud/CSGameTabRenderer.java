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

    // ===== 表格布局常量（表头与数据行共用同一来源，保证列对齐且避免重复定义）=====
    protected static final int COL_PADDING = 5;
    protected static final int COL_PING = 40;
    protected static final int AVATAR_SIZE = 12;
    protected static final int COL_NAME = 110;
    protected static final int COL_MONEY = 40;
    protected static final int COL_KILL = 35;
    protected static final int COL_DEATH = 35;
    protected static final int COL_ASSIST = 35;
    protected static final int COL_HEADSHOT = 40;
    protected static final int COL_DAMAGE = 48;
    protected static final int ROW_HEIGHT = 12;
    protected static final int ROW_GAP = 2;
    protected static final int BG_PADDING = 10;
    protected static final int HEADER_HEIGHT = 12;
    protected static final int PLAYER_AREA_WIDTH = 400;

    // ===== 通用行布局：x 起始 = 头像+昵称之后，行内各列 x 偏移 =====
    protected static final int ROW_NAME_AREA = COL_PING + AVATAR_SIZE + COL_PADDING + COL_NAME;

    @Override
    public String getGameType() {
        return "cs";
    }

    @Override
    public void render(GuiGraphics guiGraphics, int windowWidth, List<PlayerInfo> playerInfoList, Scoreboard scoreboard, Objective objective) {
        // 玩家信息区域布局
        int playerAreaWidth = PLAYER_AREA_WIDTH;
        int playerRowHeight = ROW_HEIGHT;
        int playerGap = ROW_GAP;
        int headerHeight = HEADER_HEIGHT;
        int bgPadding = BG_PADDING;
        int teamGap = 10; // 队伍之间的间隔

        // 每帧只拉取一次玩家数据，供排序与行渲染复用，避免在比较器中反复查表
        Map<UUID, PlayerData> playerDataById = indexPlayerData(playerInfoList);

        // 过滤并排序玩家
        Map<String, List<PlayerInfo>> teamPlayers = RenderUtil.getTeamsPlayerInfo(playerInfoList);

        // 按伤害排序
        Comparator<PlayerInfo> damageComparator = (p1, p2) -> Float.compare(
                damageOf(playerDataById, p1), damageOf(playerDataById, p2));

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
        guiGraphics.blit(GUI_ICONS_LOCATION, currentHeaderX + (COL_PING - 10) / 2, headerY + 2, 0, 176, 10, 8);
        guiGraphics.pose().popPose();
        currentHeaderX += COL_PING;

        // 占位（头像+昵称）
        currentHeaderX += AVATAR_SIZE + COL_NAME + COL_PADDING;

        // 金钱
        Component moneyText = Component.translatable("blockoffensive.tab.header.money").withStyle(ChatFormatting.BOLD);
        guiGraphics.drawString(minecraft.font, moneyText,
                currentHeaderX + (COL_MONEY - minecraft.font.width(moneyText)) / 2, headerY, 0xFFFFFFFF);
        currentHeaderX += COL_MONEY;

        // K/D/A
        Component killsText = Component.translatable("blockoffensive.tab.header.kills").withStyle(ChatFormatting.BOLD);
        guiGraphics.drawString(minecraft.font, killsText,
                currentHeaderX + (COL_KILL - minecraft.font.width(killsText)) / 2, headerY, 0xFFFFFFFF);
        currentHeaderX += COL_KILL;

        Component deathsText = Component.translatable("blockoffensive.tab.header.deaths").withStyle(ChatFormatting.BOLD);
        guiGraphics.drawString(minecraft.font, deathsText,
                currentHeaderX + (COL_DEATH - minecraft.font.width(deathsText)) / 2, headerY, 0xFFFFFFFF);
        currentHeaderX += COL_DEATH;

        Component assistsText = Component.translatable("blockoffensive.tab.header.assists").withStyle(ChatFormatting.BOLD);
        guiGraphics.drawString(minecraft.font, assistsText,
                currentHeaderX + (COL_ASSIST - minecraft.font.width(assistsText)) / 2, headerY, 0xFFFFFFFF);
        currentHeaderX += COL_ASSIST;

        // 爆头率
        Component headshotText = Component.translatable("blockoffensive.tab.header.headshot").withStyle(ChatFormatting.BOLD);
        guiGraphics.drawString(minecraft.font, headshotText,
                currentHeaderX + (COL_HEADSHOT - minecraft.font.width(headshotText)) / 2, headerY, 0xFFFFFFFF);
        currentHeaderX += COL_HEADSHOT;

        // 伤害（直接跟在爆头率后面）
        Component damageText = Component.translatable("blockoffensive.tab.header.damage").withStyle(ChatFormatting.BOLD);
        guiGraphics.drawString(minecraft.font, damageText,
                currentHeaderX + (COL_DAMAGE - minecraft.font.width(damageText)) / 2, headerY, 0xFFFFFFFF);

        // 渲染CT玩家（从顶部开始）
        renderRows(guiGraphics, ctPlayers, ctStartY, playerAreaWidth, playerRowHeight, playerGap, bgX + bgPadding, BOUtil.CT_COLOR, playerDataById);

        // 渲染T玩家（从中间往下）
        renderRows(guiGraphics, tPlayers, tStartY, playerAreaWidth, playerRowHeight, playerGap, bgX + bgPadding, BOUtil.T_COLOR, playerDataById);
    }

    private void renderRows(GuiGraphics guiGraphics, List<PlayerInfo> players, int startY,
                                   int width, int rowHeight, int rowGap, int x, int textColor, Map<UUID, PlayerData> playerDataById) {
        int currentY = startY;
        for (PlayerInfo player : players) {
            renderPlayerRow(guiGraphics, player, x, currentY, width, rowHeight, textColor, playerDataById);
            currentY += rowHeight + rowGap;
        }
    }

    private void renderPlayerRow(GuiGraphics guiGraphics, PlayerInfo player, int x, int y,
                                        int width, int height, int textColor, Map<UUID, PlayerData> playerDataById) {
        UUID uuid = player.getProfile().getId();
        PlayerData tabData = playerDataById.get(uuid);
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

        int textY = y + (height - 8) / 2;
        int currentX = x;

        // Ping值
        String pingText = String.valueOf(player.getLatency());
        guiGraphics.drawString(minecraft.font, pingText,
                currentX + (COL_PING - minecraft.font.width(pingText)) / 2, textY, RenderUtil.color(25,180,60));
        currentX += COL_PING;

        // 头像
        PlayerFaceRenderer.draw(guiGraphics, player.getSkinLocation(), currentX, y, AVATAR_SIZE);
        currentX += AVATAR_SIZE + COL_PADDING;

        // 玩家名（左对齐）
        guiGraphics.drawString(minecraft.font, getNameForDisplay(player), currentX, textY, textColor);

        // 金钱（与表头对齐，默认从表头高度下方开始）
        int moneyX = x + ROW_NAME_AREA;
        // 始终显示高亮背景
        guiGraphics.fill(moneyX, y, moneyX + COL_MONEY, y + height, 0x20FFFFFF);
        // 只在同队时显示金钱数值
        if (isSameTeam) {
            String money = "$" + FPSMClient.getGlobalData().getPlayerMoney(player.getProfile().getId());
            guiGraphics.drawString(minecraft.font, money,
                    moneyX + (COL_MONEY - minecraft.font.width(money)) / 2, textY, textColor);
        }

        // K/D/A（与表头对齐）
        int kdaX = moneyX + COL_MONEY;
        Component kills = Component.literal(String.valueOf(tabData.getKills())).withStyle(ChatFormatting.BOLD);
        guiGraphics.drawString(minecraft.font, kills,
                kdaX + (COL_KILL - minecraft.font.width(kills)) / 2, textY, textColor);

        int deathsX = kdaX + COL_KILL;
        guiGraphics.fill(deathsX, y, deathsX + COL_DEATH, y + height, 0x20FFFFFF);
        Component deaths = Component.literal(String.valueOf(tabData.getDeaths())).withStyle(ChatFormatting.BOLD);
        guiGraphics.drawString(minecraft.font, deaths,
                deathsX + (COL_DEATH - minecraft.font.width(deaths)) / 2, textY, textColor);

        int assistsX = deathsX + COL_DEATH;
        Component assists = Component.literal(String.valueOf(tabData.getAssists())).withStyle(ChatFormatting.BOLD);
        guiGraphics.drawString(minecraft.font, assists,
                assistsX + (COL_ASSIST - minecraft.font.width(assists)) / 2, textY, textColor);

        // 爆头率（与表头对齐）
        int headshotX = assistsX + COL_ASSIST;
        guiGraphics.fill(headshotX, y, headshotX + COL_HEADSHOT, y + height, 0x20FFFFFF);
        float headShotRate = tabData.getHeadshotRate();
        String headshotStr = ((int) (headShotRate * 100)) + "%";
        Component headshotPercentage = Component.literal(headshotStr).withStyle(ChatFormatting.BOLD);
        guiGraphics.drawString(minecraft.font, headshotPercentage,
                headshotX + (COL_HEADSHOT - minecraft.font.width(headshotPercentage)) / 2, textY, textColor);

        // 伤害（与表头对齐）
        int damageX = x + width - COL_DAMAGE;
        guiGraphics.fill(damageX, y, damageX + COL_DAMAGE, y + height, 0x40FFFFFF);
        Component damage = Component.literal(String.valueOf(Math.round(tabData.getDamage()))).withStyle(ChatFormatting.BOLD);
        guiGraphics.drawString(minecraft.font, damage,
                damageX + (COL_DAMAGE - minecraft.font.width(damage)) / 2, textY, textColor);

        if(!tabData.isLiving()){
            //渲染一层半透明灰色
            guiGraphics.fill(x, y, x + width, y + height, 0x40000000);
        }
    }

    /** 一次性索引所有玩家数据，供排序与行渲染复用，避免在比较器中逐次查表。 */
    protected static Map<UUID, PlayerData> indexPlayerData(List<PlayerInfo> playerInfoList) {
        Map<UUID, PlayerData> map = new HashMap<>();
        for (PlayerInfo info : playerInfoList) {
            UUID id = info.getProfile().getId();
            FPSMClient.getGlobalData().getPlayerData(id).ifPresent(pd -> map.put(id, pd));
        }
        return map;
    }

    private static float damageOf(Map<UUID, PlayerData> playerDataById, PlayerInfo info) {
        PlayerData pd = playerDataById.get(info.getProfile().getId());
        return pd == null ? 0.0F : pd.getDamage();
    }

    protected Component getNameForDisplay(PlayerInfo info) {
        return info.getTabListDisplayName() != null ? info.getTabListDisplayName() : Component.literal(info.getProfile().getName());
    }
}
