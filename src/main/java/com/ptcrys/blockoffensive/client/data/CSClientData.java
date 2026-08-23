package com.ptcrys.blockoffensive.client.data;

import com.ptcrys.blockoffensive.client.screen.hud.CSGameHud;
import com.ptcrys.fpsmatch.common.client.FPSMClient;
import net.minecraft.client.Minecraft;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CSClientData {
    public static boolean currentMapSupportShop = true;
    public static int cTWinnerRounds = 0;
    public static int tWinnerRounds = 0;
    public static int time = 0;
    public static boolean isDebug = false;
    public static boolean isStart = false;
    public static boolean isError = false;
    public static boolean isPause = false;
    public static boolean isWaiting = false;
    public static boolean isWarmTime = false;
    public static boolean isWaitingWinner = false;
    public static boolean canOpenShop = false;
    public static int shopCloseTime = 0;
    public static int nextRoundMoney = 0;
    public static float dismantleBombProgress = 0;
    // 旁观者数据
    public static int bombFuse = 0;
    public static int bombTotalFuse;

    public static final Map<UUID, WeaponData> weaponData = new ConcurrentHashMap<>();


    public static int getMoney() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;

        return FPSMClient.getGlobalData().getPlayerMoney(mc.player.getUUID());
    }

    public static WeaponData getWeaponData(UUID uuid) {
        return weaponData.getOrDefault(uuid, WeaponData.EMPTY);
    }

    public static void reset() {
        currentMapSupportShop = true;
        CSGameHud.getInstance().reset();
        cTWinnerRounds = 0;
        tWinnerRounds = 0;
        time = 0;
        isDebug = false;
        isStart = false;
        isError = false;
        isPause = false;
        isWaiting = false;
        isWarmTime = false;
        isWaitingWinner = false;
        nextRoundMoney = 0;
        canOpenShop = false;
        dismantleBombProgress = 0;
        bombFuse = 0;
        bombTotalFuse = 0;
        weaponData.clear();
    }

    public static int getNextRoundMinMoney() {
        return nextRoundMoney;
    }
}
