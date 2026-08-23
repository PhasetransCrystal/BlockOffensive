package com.ptcrys.blockoffensive.net.spec;

import com.ptcrys.blockoffensive.spectator.BOSpecManager;
import com.ptcrys.fpsmatch.core.FPSMCore;
import com.ptcrys.fpsmatch.core.map.BaseMap;
import com.ptcrys.fpsmatch.core.team.BaseTeam;
import com.ptcrys.fpsmatch.core.team.MapTeams;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Optional;
import java.util.function.Supplier;

public class SwitchSpectateC2SPacket {

    public enum SwitchDirection { PREV, NEXT }

    private final SwitchDirection direction;

    public SwitchSpectateC2SPacket(SwitchDirection direction) {
        this.direction = direction;
    }

    public static void encode(SwitchSpectateC2SPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.direction);
    }

    public static SwitchSpectateC2SPacket decode(FriendlyByteBuf buf) {
        return new SwitchSpectateC2SPacket(buf.readEnum(SwitchDirection.class));
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sp = ctx.getSender();
            if (sp == null || !sp.isSpectator()) return;

            Optional<BaseMap> mapOpt = FPSMCore.getInstance().getMapByPlayer(sp);
            if (mapOpt.isEmpty()) return;
            BaseMap baseMap = mapOpt.get();

            MapTeams mapTeams = baseMap.getMapTeams();
            if (mapTeams == null) return;

            BaseTeam team = mapTeams.getTeamByPlayer(sp.getUUID()).orElse(null);
            if (team == null) return;

            BOSpecManager.switchTeammate(sp, direction);
        });
        ctx.setPacketHandled(true);
    }
}
