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
    public static int[] scoreboardRounds = new int[0];
    public static int scoreboardHalfRounds = 12;
    public static int scoreboardElapsedSeconds = 0;
    public static int scoreboardCtLoss = 0;
    public static int scoreboardTLoss = 0;
    public static boolean scoreboardAdvanced = false;
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

    // ===== 队友 Ping（Z 轮盘 → 世界内光柱标记，TTL 后自动消失） =====
    public static final int PING_MAX = 8;
    public static final java.util.List<PingData> pings = new java.util.concurrent.CopyOnWriteArrayList<>();

    public record PingData(String senderName, int type, double x, double y, double z, long expiresMs) {
        public boolean expired() {
            return System.currentTimeMillis() >= expiresMs;
        }
    }

    public static void pushPing(String senderName, int type, double x, double y, double z) {
        long ttl = com.ptcrys.blockoffensive.BOConfig.common.pingTtlSeconds.get() * 1000L;
        // 同一玩家再次 Ping：旧标记立即消失，只保留新标记
        pings.removeIf(p -> p.senderName().equals(senderName));
        pings.add(new PingData(senderName, type, x, y, z, System.currentTimeMillis() + ttl));
        purgeExpiredPings();
        while (pings.size() > PING_MAX) {
            pings.remove(0);
        }
    }

    public static void purgeExpiredPings() {
        pings.removeIf(PingData::expired);
    }


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
        scoreboardRounds = new int[0];
        scoreboardHalfRounds = 12;
        scoreboardElapsedSeconds = 0;
        scoreboardCtLoss = 0;
        scoreboardTLoss = 0;
        scoreboardAdvanced = false;
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
        pings.clear();
        weaponData.clear();
    }

    public static int getNextRoundMinMoney() {
        return nextRoundMoney;
    }
}
