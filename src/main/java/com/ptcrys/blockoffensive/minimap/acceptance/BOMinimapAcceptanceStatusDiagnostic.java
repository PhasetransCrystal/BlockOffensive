package com.ptcrys.blockoffensive.minimap.acceptance;

import com.ptcrys.blockoffensive.net.acceptance.BOMinimapAcceptanceAck;
import com.ptcrys.blockoffensive.net.acceptance.BOMinimapAcceptanceServerLedger;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Loader-neutral projection of the client-produced editor timeout token. */
final class BOMinimapAcceptanceStatusDiagnostic {
    private static final String EDITOR_TIMEOUT_PREFIX =
            "surface-readiness-timeout:editor:";
    private static final Set<String> EDITOR_TIMEOUT_STAGES = Set.of(
            "detail_missing",
            "detail_mismatch",
            "runtime_generation_missing",
            "screen_not_owned",
            "gateway_not_ready",
            "open_empty",
            "unknown"
    );

    private BOMinimapAcceptanceStatusDiagnostic() {
    }

    static Optional<String> format(
            Optional<BOMinimapAcceptanceAck> acknowledgement,
            boolean editorScene
    ) {
        Objects.requireNonNull(acknowledgement, "acknowledgement");
        if (!editorScene) {
            return Optional.empty();
        }
        return acknowledgement
                .filter(value -> value.outcome()
                        == BOMinimapAcceptanceAck.Outcome.TIMED_OUT)
                .map(BOMinimapAcceptanceAck::detail)
                .filter(BOMinimapAcceptanceStatusDiagnostic::isCanonicalEditorTimeout);
    }

    static String render(
            String mode,
            String map,
            String document,
            String binding,
            String scene,
            BOMinimapAcceptanceServerLedger.Status acceptance,
            Optional<BOMinimapAcceptanceAck> acknowledgement,
            boolean editorScene
    ) {
        return render(
                mode, map, document, binding, scene, acceptance,
                acknowledgement, editorScene, ""
        );
    }

    static String render(
            String mode,
            String map,
            String document,
            String binding,
            String scene,
            BOMinimapAcceptanceServerLedger.Status acceptance,
            Optional<BOMinimapAcceptanceAck> acknowledgement,
            boolean editorScene,
            long sceneRevision,
            long currentRevision
    ) {
        if (sceneRevision < 0L || currentRevision < 0L) {
            throw new IllegalArgumentException(
                    "Acceptance status revisions must be non-negative"
            );
        }
        return render(
                mode, map, document, binding, scene, acceptance,
                acknowledgement, editorScene,
                ", sceneRevision=" + sceneRevision
                        + ", currentRevision=" + currentRevision
        );
    }

    private static String render(
            String mode,
            String map,
            String document,
            String binding,
            String scene,
            BOMinimapAcceptanceServerLedger.Status acceptance,
            Optional<BOMinimapAcceptanceAck> acknowledgement,
            boolean editorScene,
            String revisionProjection
    ) {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(map, "map");
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(scene, "scene");
        Objects.requireNonNull(acceptance, "acceptance");
        Objects.requireNonNull(revisionProjection, "revisionProjection");
        StringBuilder status = new StringBuilder()
                .append("mode=").append(mode)
                .append(", map=").append(map)
                .append(", document=").append(document)
                .append(", ").append(binding)
                .append(revisionProjection)
                .append(", scene=").append(scene)
                .append(", acceptance=")
                .append(acceptance.name().toLowerCase(Locale.ROOT));
        format(acknowledgement, editorScene)
                .ifPresent(value -> status.append(", diagnostic=").append(value));
        return status.toString();
    }

    private static boolean isCanonicalEditorTimeout(String detail) {
        if (detail == null || !detail.startsWith(EDITOR_TIMEOUT_PREFIX)) {
            return false;
        }
        String stage = detail.substring(EDITOR_TIMEOUT_PREFIX.length());
        if (EDITOR_TIMEOUT_STAGES.contains(stage)) {
            return true;
        }
        return false;
    }
}
