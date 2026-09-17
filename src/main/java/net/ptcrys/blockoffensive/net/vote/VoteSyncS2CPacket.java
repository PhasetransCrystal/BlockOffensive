package net.ptcrys.blockoffensive.net.vote;

import net.ptcrys.blockoffensive.client.screen.hud.CSVoteHud;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 投票状态同步（服务端 -> 有资格投票的客户端）。驱动全局可复用的投票 HUD。
 * <p>
 * result: 0=进行中, 1=通过, 2=否决。active=false 表示清除 HUD。
 */
public record VoteSyncS2CPacket(boolean active, String titleKey, int remainingSeconds,
                                int agree, int disagree, int notVoted, int eligible,
                                float threshold, int result) {

    public static void encode(VoteSyncS2CPacket p, FriendlyByteBuf buf) {
        buf.writeBoolean(p.active());
        buf.writeUtf(p.titleKey(), 128);
        buf.writeVarInt(p.remainingSeconds());
        buf.writeVarInt(p.agree());
        buf.writeVarInt(p.disagree());
        buf.writeVarInt(p.notVoted());
        buf.writeVarInt(p.eligible());
        buf.writeFloat(p.threshold());
        buf.writeVarInt(p.result());
    }

    public static VoteSyncS2CPacket decode(FriendlyByteBuf buf) {
        return new VoteSyncS2CPacket(
                buf.readBoolean(),
                buf.readUtf(128),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readFloat(),
                buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> CSVoteHud.getInstance().accept(this)));
        ctx.get().setPacketHandled(true);
    }
}
