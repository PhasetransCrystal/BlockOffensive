package net.ptcrys.blockoffensive.net.vote;

import net.ptcrys.blockoffensive.map.CSMap;
import net.ptcrys.fpsmatch.core.FPSMCore;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端投票操作（Y=同意 / N=反对），服务端权威处理。
 * 与聊天命令 .a/.da 等价，复用 {@link CSMap#handleAgreeCommand}/{@link CSMap#handleDisagreeCommand}。
 */
public record VoteCastC2SPacket(boolean agree) {

    public static void encode(VoteCastC2SPacket p, FriendlyByteBuf buf) {
        buf.writeBoolean(p.agree());
    }

    public static VoteCastC2SPacket decode(FriendlyByteBuf buf) {
        return new VoteCastC2SPacket(buf.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                return;
            }
            FPSMCore.getInstance().getMapByPlayer(player).ifPresent(map -> {
                if (map instanceof CSMap csMap && csMap.getVote() != null) {
                    if (agree) {
                        csMap.handleAgreeCommand(player);
                    } else {
                        csMap.handleDisagreeCommand(player);
                    }
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
