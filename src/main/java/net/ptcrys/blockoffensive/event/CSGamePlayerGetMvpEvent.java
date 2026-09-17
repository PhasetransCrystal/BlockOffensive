package net.ptcrys.blockoffensive.event;

import net.ptcrys.blockoffensive.data.MvpReason;
import net.ptcrys.fpsmatch.core.map.BaseMap;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.Event;

public class CSGamePlayerGetMvpEvent extends Event {

    Player player;
    BaseMap map;
    MvpReason reason;

    public CSGamePlayerGetMvpEvent(Player player, BaseMap map, MvpReason reason) {
        this.player = player;
        this.map = map;
        this.reason = reason;
    }

    public MvpReason getReason() {
        return reason;
    }

    public BaseMap getMap() {
        return map;
    }

    public Player getPlayer() {
        return player;
    }

    public boolean isCancelable() {
        return false;
    }
}
