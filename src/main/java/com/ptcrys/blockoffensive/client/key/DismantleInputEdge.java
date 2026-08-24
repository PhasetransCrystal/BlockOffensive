package com.ptcrys.blockoffensive.client.key;

import org.lwjgl.glfw.GLFW;

import java.util.Optional;

final class DismantleInputEdge {
    private boolean latched;

    Optional<Boolean> accept(boolean keyMatches, int action, boolean inGame) {
        if (!keyMatches) {
            return Optional.empty();
        }
        if (action == GLFW.GLFW_PRESS && inGame && !latched) {
            latched = true;
            return Optional.of(true);
        }
        if (action == GLFW.GLFW_RELEASE && latched) {
            latched = false;
            return Optional.of(false);
        }
        return Optional.empty();
    }
}
