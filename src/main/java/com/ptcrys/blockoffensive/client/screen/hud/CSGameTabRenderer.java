package com.ptcrys.blockoffensive.client.screen.hud;

import com.ptcrys.fpsmatch.common.client.FPSMClient;
import com.ptcrys.fpsmatch.common.client.tab.TabRenderer;
import com.ptcrys.fpsmatch.core.data.PlayerData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;

import java.util.*;

public class CSGameTabRenderer implements TabRenderer {
    public static final ResourceLocation GUI_ICONS_LOCATION = ResourceLocation.tryBuild("minecraft","textures/gui/icons.png");
    protected final Minecraft minecraft = Minecraft.getInstance();

    // Legacy dimensions inherited by CSDMTabRenderer. CS uses its own independent panel geometry.
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
        CSCompetitiveTabPanel.render(guiGraphics, windowWidth, playerInfoList, indexPlayerData(playerInfoList));
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

    protected Component getNameForDisplay(PlayerInfo info) {
        return info.getTabListDisplayName() != null ? info.getTabListDisplayName() : Component.literal(info.getProfile().getName());
    }
}
