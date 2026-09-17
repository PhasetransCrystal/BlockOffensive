package net.ptcrys.blockoffensive.web;

import net.ptcrys.blockoffensive.BOConfig;
import net.ptcrys.blockoffensive.client.data.CSClientData;
import net.ptcrys.blockoffensive.client.data.WeaponData;
import net.ptcrys.fpsmatch.FPSMatch;
import net.ptcrys.fpsmatch.common.client.FPSMClient;
import net.ptcrys.fpsmatch.common.client.data.FPSMClientGlobalData;
import net.ptcrys.fpsmatch.core.data.PlayerData;
import net.ptcrys.fpsmatch.core.team.ClientTeam;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BOClientWebServer {

    private static HttpServer server;
    private static final Gson gson = new Gson();

    /**
     * 主线程构建的不可变响应快照。HTTP 工作线程只读取该 volatile 引用，
     * 不再直接访问由游戏主线程每 tick 写入的 {@link CSClientData} 等可变状态，
     * 消除跨线程数据竞争。
     */
    private static volatile Map<String, Object> snapshot;

    // TODO Server side logic

    public static boolean isRunning() {
        return server != null;
    }

    public static void start() {
        // 检查端口是否被占用
        if (server != null) return;

        int port = BOConfig.common.webServerPort.get();
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/api/data", new CSDataHandler());
            server.setExecutor(null);
            server.start();
            FPSMatch.LOGGER.info("BlockOffensive Web Server started on port {}", port);
        } catch (IOException e) {
            FPSMatch.LOGGER.info("BlockOffensive Web Server failed to start on port {} {}", port, e.getMessage());
            server = null;
        }
    }

    public static void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
            FPSMatch.LOGGER.info("BlockOffensive Web Server stopped");
        }
    }

    /**
     * 在游戏主线程每 tick 调用，重建不可变响应快照。
     * <p>
     * 所有对客户端游戏状态(CSClientData/玩家数据/武器数据)的读取都在主线程完成，
     * HTTP 线程只读取 {@link #snapshot} 这一单个 volatile 引用。
     */
    public static void refreshSnapshot() {
        FPSMClientGlobalData globalData = FPSMClient.getGlobalData();

        Map<String, Object> response = new HashMap<>();

        response.put("currentMapSupportShop", CSClientData.currentMapSupportShop);
        response.put("cTWinnerRounds", CSClientData.cTWinnerRounds);
        response.put("tWinnerRounds", CSClientData.tWinnerRounds);
        response.put("time", CSClientData.time);
        response.put("isDebug", CSClientData.isDebug);
        response.put("isStart", CSClientData.isStart);
        response.put("isError", CSClientData.isError);
        response.put("isPause", CSClientData.isPause);
        response.put("isWaiting", CSClientData.isWaiting);
        response.put("isWarmTime", CSClientData.isWarmTime);
        response.put("isWaitingWinner", CSClientData.isWaitingWinner);
        response.put("canOpenShop", CSClientData.canOpenShop);
        response.put("shopCloseTime", CSClientData.shopCloseTime);
        response.put("nextRoundMoney", CSClientData.nextRoundMoney);
        response.put("dismantleBombProgress", CSClientData.dismantleBombProgress);
        response.put("bombFuse", CSClientData.bombFuse);
        response.put("bombTotalFuse", CSClientData.bombTotalFuse);

        Map<String, Object> tabData = new HashMap<>();
        for (ClientTeam clientTeam : globalData.getTeams()) {

            String team = clientTeam.name;
            if (team.equals("spectator")) continue;
            for (PlayerData data : clientTeam.players.values()) {
                UUID uuid = data.getOwner();
                Map<String, Object> playerData = new HashMap<>();
                playerData.put("name", data.name().getString());
                playerData.put("team", team);
                playerData.putAll(data.mappedInfo());
                playerData.put("money", globalData.getPlayerMoney(uuid));
                playerData.put("health", data.getHealthPercent() * 100);
                WeaponData weaponData = CSClientData.getWeaponData(uuid);
                playerData.put("items", weaponData.weaponData());
                playerData.put("bpAttributeHasHelmet", weaponData.bpAttributeHasHelmet());
                playerData.put("bpAttributeDurability", weaponData.bpAttributeDurability());
                tabData.put(uuid.toString(), playerData);
            }
        }
        response.put("tabData", tabData);

        snapshot = response;
    }

    static class CSDataHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            // 只读取主线程写好的不可变快照引用；若尚未生成返回空映射
            Map<String, Object> snap = snapshot;
            sendResponse(exchange, snap == null ? new HashMap<>() : snap);
        }
    }

    private static void sendResponse(HttpExchange exchange, Map<String, Object> response) throws IOException {
        String jsonResponse = gson.toJson(response);
        byte[] responseBytes = jsonResponse.getBytes();
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, responseBytes.length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }
}
