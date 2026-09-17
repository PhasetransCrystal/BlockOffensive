package net.ptcrys.blockoffensive.event;

import net.ptcrys.blockoffensive.map.CSGameMap;
import net.ptcrys.fpsmatch.core.team.ServerTeam;

import net.minecraftforge.eventbus.api.Event;

public class CSGameRoundEndEvent extends Event {

    private final CSGameMap map;
    private final ServerTeam winner;
    private final CSGameMap.WinnerReason reason;

    public CSGameRoundEndEvent(CSGameMap map, ServerTeam winner, CSGameMap.WinnerReason reason) {
        this.map = map;
        this.winner = winner;
        this.reason = reason;
    }

    public ServerTeam getWinner() {
        return winner;
    }

    public CSGameMap.WinnerReason getReason() {
        return reason;
    }

    public boolean isCancelable() {
        return false;
    }

    public CSGameMap getMap() {
        return map;
    }
}
