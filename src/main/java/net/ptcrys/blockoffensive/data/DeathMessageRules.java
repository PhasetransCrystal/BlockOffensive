package net.ptcrys.blockoffensive.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class DeathMessageRules {

    private DeathMessageRules() {}

    public static boolean resolveNoScopeFlag(boolean isSniper, boolean isScopedKill, boolean attackerIsDeadPlayer) {
        return isSniper && !isScopedKill && !attackerIsDeadPlayer;
    }

    /** Compatibility overload for older callers; new code must provide weapon category explicitly. */
    public static boolean resolveNoScopeFlag(boolean isScopedKill, boolean attackerIsDeadPlayer) {
        return !isScopedKill && !attackerIsDeadPlayer;
    }

    public static boolean hasDistinctAssist(UUID assistUUID, UUID killerUUID) {
        return assistUUID != null && !Objects.equals(assistUUID, killerUUID);
    }

    public static boolean isSuicide(UUID deadUUID, UUID killerUUID) {
        return Objects.equals(deadUUID, killerUUID);
    }

    public static List<String> getPostWeaponIconKeys(boolean flying, boolean headShot, boolean throughSmoke,
                                                     boolean throughWall, boolean noScope) {
        List<String> icons = new ArrayList<>();
        if (flying) icons.add("fly");
        if (headShot) icons.add("headshot");
        if (throughSmoke) icons.add("throw_smoke");
        if (throughWall) icons.add("throw_wall");
        if (noScope) icons.add("no_zoom");
        return icons;
    }
}
