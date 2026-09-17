package net.ptcrys.blockoffensive.map;

import net.ptcrys.blockoffensive.net.spec.SpectatorRosterS2CPacket;
import net.ptcrys.fpsmatch.FPSMatch;
import net.ptcrys.fpsmatch.core.FPSMCore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Owns the spectator-roster delta so CSMap remains a round-map orchestrator. */
final class CSSpectatorRosterSync {

    private static final int SYNC_INTERVAL_TICKS = 20;

    private int syncTick;
    private int lastRosterSig;
    private Set<UUID> lastRosterRecipients = Set.of();

    void tick(CSMap map) {
        if (++syncTick < SYNC_INTERVAL_TICKS) {
            return;
        }
        syncTick = 0;

        Set<UUID> spectators = new HashSet<>(map.getMapTeams().getSpecPlayers());
        List<String> names = new ArrayList<>();
        for (UUID uuid : spectators) {
            FPSMCore.getInstance().getPlayerByUUID(uuid).ifPresent(player -> names.add(player.getGameProfile().getName()));
        }
        names.sort(String::compareToIgnoreCase);

        Set<UUID> departed = new HashSet<>(lastRosterRecipients);
        departed.removeAll(spectators);
        int signature = names.hashCode();
        boolean recipientsChanged = !spectators.equals(lastRosterRecipients);

        if (spectators.isEmpty()) {
            if (!lastRosterRecipients.isEmpty()) {
                send(departed, List.of());
            }
            lastRosterRecipients = Set.of();
            lastRosterSig = 0;
            return;
        }
        if (signature == lastRosterSig && !recipientsChanged) {
            return;
        }

        send(spectators, names);
        if (!departed.isEmpty()) {
            send(departed, List.of());
        }
        lastRosterRecipients = Set.copyOf(spectators);
        lastRosterSig = signature;
    }

    void reset() {
        syncTick = 0;
        lastRosterSig = 0;
        lastRosterRecipients = Set.of();
    }

    private static void send(Collection<UUID> recipients, List<String> names) {
        if (recipients.isEmpty()) {
            return;
        }
        SpectatorRosterS2CPacket packet = new SpectatorRosterS2CPacket(List.copyOf(names));
        for (UUID uuid : recipients) {
            FPSMCore.getInstance().getPlayerByUUID(uuid).ifPresent(player -> FPSMatch.sendToPlayer(player, packet));
        }
    }
}
