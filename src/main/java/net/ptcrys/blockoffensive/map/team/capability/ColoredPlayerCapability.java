package net.ptcrys.blockoffensive.map.team.capability;

import net.ptcrys.blockoffensive.util.PlayerColorAssignments;
import net.ptcrys.blockoffensive.util.TeamPlayerColor;
import net.ptcrys.fpsmatch.common.event.FPSMTeamEvent;
import net.ptcrys.fpsmatch.core.capability.FPSMCapability;
import net.ptcrys.fpsmatch.core.capability.FPSMCapabilityManager;
import net.ptcrys.fpsmatch.core.capability.team.TeamCapability;
import net.ptcrys.fpsmatch.core.team.BaseTeam;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.UUID;

public class ColoredPlayerCapability extends TeamCapability implements FPSMCapability.CapabilitySynchronizable {

    private boolean dirty = true;
    private final PlayerColorAssignments colors = new PlayerColorAssignments();

    public ColoredPlayerCapability(BaseTeam team) {
        super(team);
    }

    public static void register() {
        FPSMCapabilityManager.register(FPSMCapabilityManager.CapabilityType.TEAM, ColoredPlayerCapability.class, ColoredPlayerCapability::new);
    }

    @Override
    public void tick() {
        if (!team.isClientSide()) dirty |= colors.reconcile(team.getPlayerList());
    }

    public int getPlayerColor(UUID uuid) {
        if (!team.isClientSide()) tick();
        return colors.get(uuid);
    }

    public java.util.Map<UUID, Integer> snapshot() {
        tick();
        return colors.snapshot();
    }

    public void restore(java.util.Map<UUID, Integer> snapshot) {
        if (team.isClientSide()) return;
        colors.replace(snapshot);
        tick();
        dirty = true;
    }

    /** Legacy palette accessor; extended rosters should use getPlayerColor. */
    public TeamPlayerColor getColor(UUID uuid) {
        int color = getPlayerColor(uuid);
        for (TeamPlayerColor entry : TeamPlayerColor.values()) if (entry.getRGBA() == color) return entry;
        return null;
    }

    @SubscribeEvent
    public void onJoin(FPSMTeamEvent.JoinEvent event) {
        if (team.isClientSide() || event.getTeam() != team) return;
        colors.assign(event.getPlayer().getUUID());
        dirty = true;
    }

    @SubscribeEvent
    public void onLeave(FPSMTeamEvent.LeaveEvent event) {
        if (team.isClientSide() || event.getTeam() != team) return;
        dirty |= colors.remove(event.getPlayer().getUUID());
    }

    @Override
    public boolean isDirty() {
        tick();
        return dirty;
    }

    @Override
    public void readFromBuf(FriendlyByteBuf buf) {
        colors.replace(buf.readMap(FriendlyByteBuf::readUUID, FriendlyByteBuf::readInt));
        dirty = false;
    }

    @Override
    public void writeToBuf(FriendlyByteBuf buf) {
        tick();
        buf.writeMap(colors.snapshot(), FriendlyByteBuf::writeUUID, FriendlyByteBuf::writeInt);
    }

    @Override
    public void onBroadcast() {
        dirty = false;
    }

    @Override
    public void destroy() {
        colors.clear();
        dirty = true;
    }

    @Override
    public boolean isImmutable() {
        return true;
    }
}
