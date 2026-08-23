package com.ptcrys.blockoffensive.net.shop;

import com.ptcrys.blockoffensive.client.data.CSClientData;
import com.ptcrys.blockoffensive.client.screen.CSGameShopScreen;
import icyllis.modernui.mc.MuiScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ShopStatesS2CPacket {
    boolean canOpenShop;
    int nextRoundMoney;
    int closeTime;

    public ShopStatesS2CPacket(boolean canOpenShop, int nextRoundMoney, int closeTime) {
        this.canOpenShop = canOpenShop;
        this.nextRoundMoney = nextRoundMoney;
        this.closeTime = closeTime;
    }
    public static void encode(ShopStatesS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.canOpenShop);
        buf.writeInt(packet.nextRoundMoney);
        buf.writeInt(packet.closeTime);
    }

    public static ShopStatesS2CPacket decode(FriendlyByteBuf buf) {
        return new ShopStatesS2CPacket(
                buf.readBoolean(),
                buf.readInt(),
                buf.readInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            boolean wasOpen = CSClientData.canOpenShop;
            CSClientData.canOpenShop = this.canOpenShop;
            CSClientData.nextRoundMoney = this.nextRoundMoney;
            CSClientData.shopCloseTime = this.closeTime;

            if (wasOpen && !this.canOpenShop && Minecraft.getInstance().player != null) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.screen instanceof MuiScreen muiScreen && muiScreen.getFragment() instanceof CSGameShopScreen) {
                    mc.setScreen(null);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
