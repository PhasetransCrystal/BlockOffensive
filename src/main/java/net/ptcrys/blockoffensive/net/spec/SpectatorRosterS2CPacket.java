package net.ptcrys.blockoffensive.net.spec;

import net.ptcrys.blockoffensive.client.screen.hud.CSSpectatorRoster;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 观战者名单同步（服务端 -> 观战客户端）。用于游戏内观战者列表面板。
 */
public record SpectatorRosterS2CPacket(List<String> names) {

    public static void encode(SpectatorRosterS2CPacket p, FriendlyByteBuf buf) {
        buf.writeCollection(p.names(), (b, s) -> b.writeUtf(s, 64));
    }

    public static SpectatorRosterS2CPacket decode(FriendlyByteBuf buf) {
        return new SpectatorRosterS2CPacket(buf.readCollection(ArrayList::new, b -> b.readUtf(64)));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> CSSpectatorRoster.getInstance().accept(this)));
        ctx.get().setPacketHandled(true);
    }
}
