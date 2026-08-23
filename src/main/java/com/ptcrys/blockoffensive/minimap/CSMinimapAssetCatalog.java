package com.ptcrys.blockoffensive.minimap;

import com.ptcrys.fpsmatch.core.minimap.model.NamespacedId;

import java.util.List;
import java.util.Objects;

/**
 * Declares BO minimap style/texture/translation IDs. Pure catalog for tests and resource wiring.
 * FPSMatch fallback markers remain usable when BO assets are absent.
 */
public final class CSMinimapAssetCatalog {
    public record MarkerAsset(NamespacedId styleId, NamespacedId typeId, String texturePath, String translationKey) {
        public MarkerAsset {
            Objects.requireNonNull(styleId, "styleId");
            Objects.requireNonNull(typeId, "typeId");
            Objects.requireNonNull(texturePath, "texturePath");
            Objects.requireNonNull(translationKey, "translationKey");
            if (!texturePath.startsWith("assets/blockoffensive/textures/minimap/markers/")
                    || !texturePath.endsWith(".png")) {
                throw new IllegalArgumentException("texturePath must be under BO minimap markers PNG tree");
            }
            if (!translationKey.startsWith("blockoffensive.minimap.")) {
                throw new IllegalArgumentException("translationKey must be BO minimap key");
            }
        }
    }

    public static final List<MarkerAsset> MARKERS = List.of(
            asset("blockoffensive:style/self", "fpsmatch:type/player", "self", "marker.self"),
            asset("blockoffensive:style/ally", "fpsmatch:type/player", "ally", "marker.ally"),
            asset("blockoffensive:style/enemy", "fpsmatch:type/player", "enemy", "marker.enemy"),
            asset("blockoffensive:style/death", "fpsmatch:type/death", "death", "marker.death"),
            asset("blockoffensive:style/c4_carried", "blockoffensive:type/c4", "c4_carried", "marker.c4_carried"),
            asset("blockoffensive:style/c4_dropped", "blockoffensive:type/c4", "c4_dropped", "marker.c4_dropped"),
            asset("blockoffensive:style/c4_planted", "blockoffensive:type/c4", "c4_planted", "marker.c4_planted"),
            asset("blockoffensive:style/c4_defusing", "blockoffensive:type/c4", "c4_defusing", "marker.c4_defusing"),
            asset("blockoffensive:style/site", "fpsmatch:region/bomb_site", "site", "marker.site"),
            asset("blockoffensive:style/spawn", "fpsmatch:region/spawn", "spawn", "marker.spawn"),
            asset("blockoffensive:style/shop", "fpsmatch:region/shop", "shop", "marker.shop")
    );

    public static final List<String> EXTRA_TRANSLATION_KEYS = List.of(
            "blockoffensive.minimap.region.map_boundary",
            "blockoffensive.minimap.region.bomb_site",
            "blockoffensive.minimap.region.spawn",
            "blockoffensive.minimap.region.shop",
            "blockoffensive.minimap.hud.tactical",
            "blockoffensive.minimap.hud.minimap"
    );

    public static final int MAX_MARKER_ICON_EDGE = 64;
    public static final int MAX_MARKER_ICON_BYTES = 16_384;

    private CSMinimapAssetCatalog() {
    }

    private static MarkerAsset asset(String style, String type, String fileStem, String keySuffix) {
        return new MarkerAsset(
                NamespacedId.parse(style),
                NamespacedId.parse(type),
                "assets/blockoffensive/textures/minimap/markers/" + fileStem + ".png",
                "blockoffensive.minimap." + keySuffix
        );
    }

    public static List<String> allTranslationKeys() {
        return java.util.stream.Stream.concat(
                MARKERS.stream().map(MarkerAsset::translationKey),
                EXTRA_TRANSLATION_KEYS.stream()
        ).toList();
    }
}