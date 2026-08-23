package com.ptcrys.blockoffensive.event;

import com.ptcrys.blockoffensive.entity.CompositionC4Entity;
import com.ptcrys.blockoffensive.map.CSGameMap;
import com.ptcrys.fpsmatch.core.team.ServerTeam;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

public class CSGameMapEvent extends Event {
    private final CSGameMap map;

    public CSGameMapEvent(CSGameMap map) {
        this.map = map;
    }

    public CSGameMap getMap() {
        return map;
    }

    public static class TeamSwitchEvent extends CSGameMapEvent {
        public TeamSwitchEvent(CSGameMap map) {
            super(map);
        }
    }

    public static class PlayerEvent extends CSGameMapEvent {
        public final ServerTeam team;
        public final ServerPlayer player;
        public PlayerEvent(CSGameMap map, ServerTeam team, ServerPlayer player) {
            super(map);
            this.team = team;
            this.player = player;
        }

        public ServerTeam getTeam() {
            return team;
        }

        public ServerPlayer getPlayer() {
            return player;
        }

        public static class PlacedC4Event extends PlayerEvent {
            private final CompositionC4Entity c4Entity;

            public PlacedC4Event(CSGameMap map, ServerTeam team, ServerPlayer player, CompositionC4Entity c4Entity) {
                super(map, team, player);
                this.c4Entity = c4Entity;
            }

            public CompositionC4Entity getC4Entity() {
                return c4Entity;
            }
        }
    }
}
