package com.ptcrys.blockoffensive.client.key;

import java.util.Objects;
import java.util.function.BooleanSupplier;

final class TeamChatInputEdge {
    private TeamChatInputEdge() {
    }

    static boolean consumeAndShouldOpen(BooleanSupplier consumeClick, BooleanSupplier screenOpen) {
        Objects.requireNonNull(consumeClick, "consumeClick");
        Objects.requireNonNull(screenOpen, "screenOpen");
        return consumeClick.getAsBoolean() && !screenOpen.getAsBoolean();
    }
}
