package com.ptcrys.blockoffensive.net.ping;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端 → 同队客户端：队友 Ping 广播（含类型、坐标、发送者名）。
 * <p>
 * 客户端在 HUD 上渲染屏幕边缘方向指示器（CSGO 风格），10 秒后过期。
 */
public class PingS2CPacket {

    private final String senderName;
    private final int type;
    private final double x;
    private final double y;
    private final double z;

    public PingS2CPacket(String senderName, int type, double x, double y, double z) {
        this.senderName = senderName;
        this.type = type;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static void encode(PingS2CPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.senderName == null ? "" : msg.senderName, 64);
        buf.writeVarInt(msg.type);
        buf.writeDouble(msg.x);
        buf.writeDouble(msg.y);
        buf.writeDouble(msg.z);
    }

    public static PingS2CPacket decode(FriendlyByteBuf buf) {
        return new PingS2CPacket(buf.readUtf(64), buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            com.mojang.logging.LogUtils.getLogger().info("[PingS2C] sender={} type={} pos=({},{},{})",
                    senderName, type,
                    String.format(java.util.Locale.ROOT, "%.2f", x),
                    String.format(java.util.Locale.ROOT, "%.2f", y),
                    String.format(java.util.Locale.ROOT, "%.2f", z));
            com.ptcrys.blockoffensive.client.data.CSClientData.pushPing(senderName, type, x, y, z);
        });
        ctx.setPacketHandled(true);
    }
}