package com.ptcrys.blockoffensive.net.acceptance;

import com.ptcrys.fpsmatch.core.minimap.wire.WireIdentity;

import java.util.Objects;
import java.util.UUID;

public record BOMinimapAcceptanceEditorContext(
        UUID ownerId,
        UUID fixtureRunId,
        long epoch,
        WireIdentity.EditorContext context
) {
    public BOMinimapAcceptanceEditorContext {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(fixtureRunId, "fixtureRunId");
        if (epoch <= 0 || epoch > BOMinimapAcceptanceEpochGate.MAX_EPOCH) {
            throw new IllegalArgumentException("epoch is outside the supported range");
        }
        Objects.requireNonNull(context, "context");
    }
}
