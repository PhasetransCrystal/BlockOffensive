package com.ptcrys.blockoffensive.map.team.capability;

import com.ptcrys.blockoffensive.util.TeamPlayerColor;
import com.ptcrys.fpsmatch.core.capability.FPSMCapability;
import com.ptcrys.fpsmatch.core.capability.FPSMCapabilityManager;
import com.ptcrys.fpsmatch.core.capability.team.TeamCapability;
import com.ptcrys.fpsmatch.common.event.FPSMTeamEvent;
import com.ptcrys.fpsmatch.core.team.BaseTeam;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ColoredPlayerCapability extends TeamCapability implements FPSMCapability.CapabilitySynchronizable{
    private boolean dirty;

    private final Map<UUID, TeamPlayerColor> colored = new ConcurrentHashMap<>();

    private final Random random = new Random();

    public ColoredPlayerCapability(BaseTeam team) {
        super(team);
    }

    /**
     * 注册能力到全局管理器
     */
    public static void register() {
        FPSMCapabilityManager.register(FPSMCapabilityManager.CapabilityType.TEAM, ColoredPlayerCapability.class, ColoredPlayerCapability::new);
    }

    public TeamPlayerColor getEmpty(){
        Collection<TeamPlayerColor> colors = colored.values();
        for (TeamPlayerColor color : TeamPlayerColor.values()){
            if(!colors.contains(color)){
                return color;
            };
        }

        return TeamPlayerColor.values()[random.nextInt(TeamPlayerColor.values().length)];
    }

    public TeamPlayerColor getColor(UUID uuid){
        TeamPlayerColor color = colored.get(uuid);
        // 玩家中途加入队伍、或存档重载(fpsm save)后队伍能力会被重建且不会再触发 onJoin，
        // 此时若该玩家已是本队成员则补发颜色，避免名称回退为默认白色。
        if (color == null && team.hasPlayer(uuid)) {
            color = getEmpty();
            colored.put(uuid, color);
            dirty = true;
        }
        return color;
    }

    @SubscribeEvent
    public void onJoin(FPSMTeamEvent.JoinEvent event) {
        colored.put(event.getPlayer().getUUID(),getEmpty());
        dirty = true;
    }

    @SubscribeEvent
    public void onLeave(FPSMTeamEvent.LeaveEvent event) {
        colored.remove(event.getPlayer().getUUID());
        dirty = true;
    }

    @Override
    public boolean isDirty() {
        return dirty;
    }

    @Override
    public void readFromBuf(FriendlyByteBuf buf) {
        colored.clear();
        Map<UUID, TeamPlayerColor> map = buf.readMap(FriendlyByteBuf::readUUID,(friendlyByteBuf)-> friendlyByteBuf.readEnum(TeamPlayerColor.class));
        colored.putAll(map);
    }

    @Override
    public void writeToBuf(FriendlyByteBuf buf) {
        buf.writeMap(colored,FriendlyByteBuf::writeUUID,FriendlyByteBuf::writeEnum);
        dirty = false;
    }

    @Override
    public void destroy() {
        colored.clear();
        dirty = true;
    }

    @Override
    public boolean isImmutable(){
        return true;
    };

}
