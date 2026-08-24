package com.ptcrys.blockoffensive.net.mvp;

import com.ptcrys.blockoffensive.client.screen.hud.CSGameHud;
import com.ptcrys.blockoffensive.client.screen.hud.CSMvpHud;

import com.ptcrys.blockoffensive.data.MvpReason;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class MvpMessageS2CPacket {
    private final MvpReason mvpReason;

    public MvpMessageS2CPacket(MvpReason mvpReason) {
        this.mvpReason = mvpReason;
    }
    public static void encode(MvpMessageS2CPacket packet, FriendlyByteBuf buf) {
        // A round can legitimately end without an MVP player; preserve the banner without inventing an identity.
        buf.writeBoolean(packet.mvpReason.uuid != null);
        if (packet.mvpReason.uuid != null) {
            buf.writeUUID(packet.mvpReason.uuid);
        }
        buf.writeBoolean(packet.mvpReason.isCtWinner());
        buf.writeComponent(packet.mvpReason.getTeamName());
        buf.writeComponent(packet.mvpReason.getPlayerName());
        buf.writeComponent(packet.mvpReason.getMvpReason());
        buf.writeComponent(packet.mvpReason.getExtraInfo1());
        buf.writeComponent(packet.mvpReason.getExtraInfo2());
    }

    public static MvpMessageS2CPacket decode(FriendlyByteBuf buf) {
        UUID uuid = buf.readBoolean() ? buf.readUUID() : null;
        return new MvpMessageS2CPacket(new MvpReason.Builder(uuid)
                .setCtWinner(buf.readBoolean())
                .setTeamName(buf.readComponent().copy())
                .setPlayerName(buf.readComponent().copy())
                .setMvpReason(buf.readComponent().copy())
                .setExtraInfo1(buf.readComponent().copy())
                .setExtraInfo2(buf.readComponent().copy())
                .build());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(()-> CSGameHud.getInstance().getMvpHud().triggerAnimation(this.mvpReason));
        ctx.get().setPacketHandled(true);
    }
}
