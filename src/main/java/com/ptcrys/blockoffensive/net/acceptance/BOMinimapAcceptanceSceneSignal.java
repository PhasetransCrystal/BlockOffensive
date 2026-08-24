package com.ptcrys.blockoffensive.net.acceptance;

import com.ptcrys.fpsmatch.core.minimap.model.MapKey;
import com.ptcrys.fpsmatch.core.minimap.model.NamespacedId;
import com.ptcrys.fpsmatch.core.minimap.model.Sha256;

import java.util.Objects;
import java.util.UUID;

public record BOMinimapAcceptanceSceneSignal(
        Action action,
        Scene scene,
        UUID ownerId,
        UUID fixtureRunId,
        long epoch,
        MapKey mapKey,
        NamespacedId dimension,
        NamespacedId documentId,
        long revision,
        Sha256 sourceHash,
        Sha256 runtimeHash
) {
    public BOMinimapAcceptanceSceneSignal {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(scene, "scene");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(fixtureRunId, "fixtureRunId");
        requirePositive(epoch, "epoch");
        Objects.requireNonNull(mapKey, "mapKey");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(documentId, "documentId");
        requirePositive(revision, "revision");
        Objects.requireNonNull(sourceHash, "sourceHash");
        Objects.requireNonNull(runtimeHash, "runtimeHash");
    }

    public enum Action {
        SHOW(0),
        CLEAR(1);

        private final int code;

        Action(int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }

        public static Action fromCode(int code) {
            for (Action value : values()) {
                if (value.code == code) {
                    return value;
                }
            }
            throw new IllegalArgumentException("Unknown acceptance scene action: " + code);
        }
    }

    public enum Scene {
        HUD_CROWDED(0, "hud_crowded"),
        TACTICAL(1, "tactical"),
        EDITOR_DIRTY(2, "editor_dirty"),
        EDITOR_PUBLISHING(3, "editor_publishing"),
        EDITOR_ERROR(4, "editor_error"),
        MAP_ROOM_ERROR(5, "map_room_error");

        private final int code;
        private final String commandName;

        Scene(int code, String commandName) {
            this.code = code;
            this.commandName = commandName;
        }

        public int code() {
            return code;
        }

        public String commandName() {
            return commandName;
        }

        public static Scene fromCode(int code) {
            for (Scene value : values()) {
                if (value.code == code) {
                    return value;
                }
            }
            throw new IllegalArgumentException("Unknown acceptance scene: " + code);
        }
    }

    private static void requirePositive(long value, String name) {
        if (value <= 0 || value > BOMinimapAcceptanceEpochGate.MAX_EPOCH) {
            throw new IllegalArgumentException(name + " is outside the supported range");
        }
    }
}
