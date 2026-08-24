package com.ptcrys.blockoffensive.minimap.acceptance;

import com.ptcrys.blockoffensive.net.acceptance.BOMinimapAcceptanceSceneSignal;
import com.ptcrys.blockoffensive.net.acceptance.BOMinimapAcceptanceServerLedger;
import com.ptcrys.fpsmatch.common.capability.map.MinimapCapability;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomDetail;
import com.ptcrys.fpsmatch.core.minimap.model.MapKey;
import com.ptcrys.fpsmatch.core.minimap.model.NamespacedId;

import java.util.Objects;
import java.util.UUID;

/** Pure scene identity and policy rules shared by fixture dispatch and cleanup. */
final class BOMinimapAcceptanceScenePlan {
    private BOMinimapAcceptanceScenePlan() {
    }

    static BOMinimapAcceptanceSceneSignal show(
            BOMinimapAcceptanceFixture.Scene scene,
            UUID ownerId,
            UUID runId,
            long epoch,
            MapKey mapKey,
            NamespacedId dimension,
            NamespacedId documentId,
            MinimapCapability.Binding binding
    ) {
        return new BOMinimapAcceptanceSceneSignal(
                BOMinimapAcceptanceSceneSignal.Action.SHOW,
                wireScene(scene), ownerId, runId, epoch, mapKey, dimension, documentId,
                binding.revision(), binding.sourceHash(), binding.runtimeHash()
        );
    }

    static BOMinimapAcceptanceServerLedger.ScenePolicy policy(
            BOMinimapAcceptanceFixture.Scene scene
    ) {
        return switch (scene) {
            case EDITOR_DIRTY, EDITOR_PUBLISHING, EDITOR_ERROR ->
                    BOMinimapAcceptanceServerLedger.ScenePolicy.EDITOR_REQUIRES_CONTEXT;
            case HUD_CROWDED, TACTICAL, MAP_ROOM_ERROR ->
                    BOMinimapAcceptanceServerLedger.ScenePolicy.STANDARD;
        };
    }

    static boolean requiresPassiveDetail(BOMinimapAcceptanceFixture.Scene scene) {
        return switch (scene) {
            case EDITOR_DIRTY, EDITOR_PUBLISHING, EDITOR_ERROR, MAP_ROOM_ERROR -> true;
            case HUD_CROWDED, TACTICAL -> false;
        };
    }

    static void requireMatchingDetail(
            MapRoomDetail detail,
            BOMinimapAcceptanceSceneSignal signal
    ) {
        var summary = Objects.requireNonNull(detail, "detail").summary();
        var identity = summary.minimapIdentity().orElseThrow(() ->
                new IllegalStateException("Passive map detail has no minimap identity"));
        if (!summary.gameType().equals(signal.mapKey().gameType())
                || !summary.mapName().equals(signal.mapKey().mapName())
                || !summary.dimension().equals(signal.dimension().toString())
                || !summary.currentPlayerOp()
                || !identity.dimension().equals(signal.dimension())
                || !identity.documentId().equals(signal.documentId())
                || identity.revision() != signal.revision()
                || !identity.sourceHash().equals(signal.sourceHash())
                || !identity.runtimeHash().equals(signal.runtimeHash())) {
            throw new IllegalStateException(
                    "Passive map detail does not match the acceptance scene identity"
            );
        }
    }

    static BOMinimapAcceptanceSceneSignal clear(BOMinimapAcceptanceSceneSignal signal) {
        return new BOMinimapAcceptanceSceneSignal(
                BOMinimapAcceptanceSceneSignal.Action.CLEAR,
                signal.scene(), signal.ownerId(), signal.fixtureRunId(), signal.epoch(),
                signal.mapKey(), signal.dimension(), signal.documentId(), signal.revision(),
                signal.sourceHash(), signal.runtimeHash()
        );
    }

    private static BOMinimapAcceptanceSceneSignal.Scene wireScene(
            BOMinimapAcceptanceFixture.Scene scene
    ) {
        return switch (scene) {
            case HUD_CROWDED -> BOMinimapAcceptanceSceneSignal.Scene.HUD_CROWDED;
            case TACTICAL -> BOMinimapAcceptanceSceneSignal.Scene.TACTICAL;
            case EDITOR_DIRTY -> BOMinimapAcceptanceSceneSignal.Scene.EDITOR_DIRTY;
            case EDITOR_PUBLISHING -> BOMinimapAcceptanceSceneSignal.Scene.EDITOR_PUBLISHING;
            case EDITOR_ERROR -> BOMinimapAcceptanceSceneSignal.Scene.EDITOR_ERROR;
            case MAP_ROOM_ERROR -> BOMinimapAcceptanceSceneSignal.Scene.MAP_ROOM_ERROR;
        };
    }
}
