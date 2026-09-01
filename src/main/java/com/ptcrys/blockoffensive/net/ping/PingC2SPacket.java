package com.ptcrys.blockoffensive.net.ping;

import com.ptcrys.blockoffensive.map.CSGameMap;
import com.ptcrys.fpsmatch.core.FPSMCore;
import com.ptcrys.fpsmatch.core.map.BaseMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端：准星 Ping（在指定世界坐标放置团队标记）。
 * <p>
 * 服务端校验后在图中后广播给同队玩家（PingS2CPacket）。
 */
public class PingC2SPacket {

    /** Ping 类型（与服务端/客户端渲染共用）。 */
    public static final int TYPE_NORMAL = 0;
    public static final int TYPE_ENEMY = 1;
    public static final int TYPE_DANGER = 2;
    public static final int TYPE_ATTACK = 3;
    public static final int TYPE_DEFEND = 4;
    public static final int TYPE_HELP = 5;

    private final int type;
    private final double x;
    private final double y;
    private final double z;

    public PingC2SPacket(int type, double x, double y, double z) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static void encode(PingC2SPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.type);
        buf.writeDouble(msg.x);
        buf.writeDouble(msg.y);
        buf.writeDouble(msg.z);
    }

    public static PingC2SPacket decode(FriendlyByteBuf buf) {
        return new PingC2SPacket(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sp = ctx.getSender();
            if (sp == null) return;
            java.util.Optional<BaseMap> mapOpt = FPSMCore.getInstance().getMapByPlayer(sp);
            if (mapOpt.isEmpty() || !(mapOpt.get() instanceof CSGameMap cs)) return;
            cs.handlePing(sp, type, x, y, z);
        });
        ctx.setPacketHandled(true);
    }
}