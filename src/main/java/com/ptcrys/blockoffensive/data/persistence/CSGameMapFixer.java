package com.ptcrys.blockoffensive.data.persistence;

import com.google.gson.*;
import com.ptcrys.fpsmatch.common.capability.map.DemolitionModeCapability;
import com.ptcrys.fpsmatch.common.capability.map.GameEndTeleportCapability;
import com.ptcrys.fpsmatch.common.capability.team.ShopCapability;
import com.ptcrys.fpsmatch.common.capability.team.SpawnPointCapability;
import com.ptcrys.fpsmatch.common.capability.team.StartKitsCapability;
import com.ptcrys.fpsmatch.core.capability.CapabilityMap;
import com.ptcrys.fpsmatch.core.map.BaseMap;
import com.ptcrys.fpsmatch.core.persistence.datafixer.DataFixer;
import com.ptcrys.fpsmatch.core.team.BaseTeam;

import java.util.*;

/**
 * 旧版CSGameMap数据的兼容处理类
 * 负责读取旧格式数据并转换为新版CSGameMap数据格式
 */
public class CSGameMapFixer {
    public static class OldV0 implements DataFixer.JsonFixer {
        @Override
        public JsonElement apply(JsonElement jsonElement) {
            if (!jsonElement.isJsonObject()) {
                throw new JsonParseException("jsonElement is not a JSON object");
            }
            JsonObject old = jsonElement.getAsJsonObject();
            JsonObject root = new JsonObject();

            root.add("mapName", old.get("mapName"));
            root.add("mapArea", old.get("mapArea"));
            root.add("serverLevel", old.get("serverLevel"));

            Map<String, CapabilityMap.Wrapper.Builder<BaseTeam>> teamCapabilities = new HashMap<>();
            if (old.has("spawnpoints") && old.get("spawnpoints").isJsonObject()) {
                old.get("spawnpoints").getAsJsonObject().entrySet().forEach(entry -> {
                    teamCapabilities.computeIfAbsent(entry.getKey(), k -> new CapabilityMap.Wrapper.Builder<>()).add(SpawnPointCapability.class, entry.getValue());
                });
            }

            if (old.has("shops") && old.get("shops").isJsonObject()) {
                old.get("shops").getAsJsonObject().entrySet().forEach(entry -> {
                    teamCapabilities.computeIfAbsent(entry.getKey(), k -> new CapabilityMap.Wrapper.Builder<>()).add(ShopCapability.class, entry.getValue());
                });
            }

            if (old.has("startKits") && old.get("startKits").isJsonObject()) {
                old.get("startKits").getAsJsonObject().entrySet().forEach(entry -> {
                    String[] name = entry.getKey().split("_");
                    teamCapabilities.computeIfAbsent(name[2], k -> new CapabilityMap.Wrapper.Builder<>()).add(StartKitsCapability.class, entry.getValue());
                });
            }

            CapabilityMap.Wrapper.Builder<BaseMap> mapCapabilities = new CapabilityMap.Wrapper.Builder<>();

            if (old.has("bombAreas") && old.has("blastTeam")) {
                JsonObject demolitionCapability = new JsonObject();

                demolitionCapability.add("bombAreas", old.get("bombAreas"));;
                demolitionCapability.add("blastTeam", old.get("blastTeam"));

                mapCapabilities.add(DemolitionModeCapability.class, demolitionCapability);
            }

            if (old.has("matchEndPoint")) {
                mapCapabilities.add(GameEndTeleportCapability.class, old.get("matchEndPoint"));
            }

            root.add("capabilities", mapCapabilities.encode());

            JsonObject teamRoot = new JsonObject();
            teamCapabilities.forEach((team, builder) -> {
                teamRoot.add(team, builder.build().encode());
            });

            root.add("teams", teamRoot);

            return root;
        }
    }
}