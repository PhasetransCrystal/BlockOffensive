package com.ptcrys.blockoffensive.net.acceptance;

import com.ptcrys.fpsmatch.core.minimap.contract.MinimapHardLimits;

import java.text.Normalizer;
import java.util.Objects;
import java.util.UUID;

public record BOMinimapAcceptanceAck(
        UUID ownerId,
        UUID fixtureRunId,
        long epoch,
        Outcome outcome,
        String detail
) {
    public BOMinimapAcceptanceAck {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(fixtureRunId, "fixtureRunId");
        if (epoch <= 0 || epoch > BOMinimapAcceptanceEpochGate.MAX_EPOCH) {
            throw new IllegalArgumentException("epoch is outside the supported range");
        }
        Objects.requireNonNull(outcome, "outcome");
        requireDetail(detail);
    }

    public enum Outcome {
        APPLIED(0),
        IGNORED(1),
        FAILED(2),
        TIMED_OUT(3),
        CLOSED(4);

        private final int code;

        Outcome(int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }

        public static Outcome fromCode(int code) {
            for (Outcome value : values()) {
                if (value.code == code) {
                    return value;
                }
            }
            throw new IllegalArgumentException("Unknown acceptance outcome: " + code);
        }
    }

    private static void requireDetail(String detail) {
        if (detail == null || detail.isEmpty()
                || !Normalizer.isNormalized(detail, Normalizer.Form.NFC)
                || detail.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                > MinimapHardLimits.MAX_ERROR_DETAIL_UTF8_BYTES) {
            throw new IllegalArgumentException("detail is invalid or too long");
        }
        for (int i = 0; i < detail.length(); i++) {
            if (Character.isISOControl(detail.charAt(i))) {
                throw new IllegalArgumentException("detail contains a control character");
            }
        }
    }
}
