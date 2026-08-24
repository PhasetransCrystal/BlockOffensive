package com.ptcrys.blockoffensive.minimap.acceptance;

import com.ptcrys.blockoffensive.BlockOffensive;
import com.ptcrys.blockoffensive.net.acceptance.BOMinimapAcceptanceSceneS2CPacket;
import com.ptcrys.blockoffensive.net.acceptance.BOMinimapAcceptanceSceneSignal;
import com.ptcrys.blockoffensive.net.acceptance.BOMinimapAcceptanceServerLedger;
import com.ptcrys.fpsmatch.FPSMatch;
import com.ptcrys.fpsmatch.common.mapselect.MapRoomQueryService;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomDetail;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomDetailS2CPacket;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomToastS2CPacket;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapSelectionSnapshotS2CPacket;
import com.ptcrys.fpsmatch.config.FPSMConfig;
import com.ptcrys.fpsmatch.core.map.BaseMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.Objects;
import java.util.Optional;

/** Orders server-derived prerequisites and the correlated acceptance scene send. */
final class BOMinimapAcceptanceCompositeSender {
    private final ServerPlayer actor;
    private final BaseMap map;
    private final BOMinimapAcceptanceServerLedger acceptanceLedger;

    BOMinimapAcceptanceCompositeSender(
            ServerPlayer actor,
            BaseMap map,
            BOMinimapAcceptanceServerLedger acceptanceLedger
    ) {
        this.actor = Objects.requireNonNull(actor, "actor");
        this.map = Objects.requireNonNull(map, "map");
        this.acceptanceLedger = Objects.requireNonNull(
                acceptanceLedger, "acceptanceLedger"
        );
    }

    void dispatch(
            BOMinimapAcceptanceFixture.Scene scene,
            BOMinimapAcceptanceSceneSignal signal,
            BOMinimapAcceptanceServerLedger.ScenePolicy policy,
            long deadlineTick
    ) {
        Objects.requireNonNull(scene, "scene");
        Objects.requireNonNull(signal, "signal");
        Objects.requireNonNull(policy, "policy");
        acceptanceLedger.expect(
                signal.epoch(), policy, Optional.empty(), deadlineTick
        );

        if (BOMinimapAcceptanceScenePlan.requiresPassiveDetail(scene)) {
            try {
                sendPassiveDetails(scene, signal);
            } catch (RuntimeException failure) {
                acceptanceLedger.abort(signal.epoch());
                throw new IllegalStateException(
                        "Acceptance prerequisite dispatch failed", failure
                );
            }
        }

        try {
            sendScene(signal);
            acceptanceLedger.markDispatched(signal.epoch());
        } catch (RuntimeException failure) {
            BOMinimapAcceptanceServerLedger.Status status = acceptanceLedger.status();
            if (status == BOMinimapAcceptanceServerLedger.Status.RESERVED
                    || status == BOMinimapAcceptanceServerLedger.Status.DISPATCHED) {
                acceptanceLedger.markSendUnknown(signal.epoch());
            }
            throw new IllegalStateException(
                    "Acceptance scene delivery is unknown; cleanup is required",
                    failure
            );
        }
    }

    private void sendPassiveDetails(
            BOMinimapAcceptanceFixture.Scene scene,
            BOMinimapAcceptanceSceneSignal signal
    ) {
        boolean viewerOp = MapRoomQueryService.isMapOperator(actor);
        boolean nonOpButtonEnabled =
                FPSMConfig.Server.enableMapSelectionButtonForNonOps.get();
        MapSelectionSnapshotS2CPacket snapshot =
                new MapSelectionSnapshotS2CPacket(
                        MapRoomQueryService.summaries(actor),
                        viewerOp,
                        nonOpButtonEnabled,
                        true
                );
        MapRoomDetail detail = MapRoomQueryService.detail(actor, map);
        BOMinimapAcceptanceScenePlan.requireMatchingDetail(detail, signal);
        FPSMatch.sendToPlayer(actor, snapshot);
        FPSMatch.sendToPlayer(actor, new MapRoomDetailS2CPacket(detail, true));
        if (scene == BOMinimapAcceptanceFixture.Scene.MAP_ROOM_ERROR) {
            FPSMatch.sendToPlayer(actor, new MapRoomToastS2CPacket(
                    Component.translatable(
                            "gui.fpsm.map_select.action.map_not_found"
                    ),
                    true
            ));
        }
    }

    private void sendScene(BOMinimapAcceptanceSceneSignal signal) {
        BlockOffensive.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> actor),
                new BOMinimapAcceptanceSceneS2CPacket(signal)
        );
    }
}
