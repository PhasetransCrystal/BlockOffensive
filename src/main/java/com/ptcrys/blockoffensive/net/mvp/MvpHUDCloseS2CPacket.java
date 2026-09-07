package com.ptcrys.blockoffensive.net.mvp;

import com.ptcrys.blockoffensive.client.screen.hud.CSGameHud;
import com.ptcrys.blockoffensive.client.spec.KillCamManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class MvpHUDCloseS2CPacket {
    public MvpHUDCloseS2CPacket() {
    }
    public static void encode(MvpHUDCloseS2CPacket packet, FriendlyByteBuf buf) {
    }

    public static MvpHUDCloseS2CPacket decode(FriendlyByteBuf buf) {
        return new MvpHUDCloseS2CPacket();
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            CSGameHud.getInstance().getMvpHud().triggerCloseAnimation();
            // 新回合开始：清理 KillCam 残留视角/灰度，防止回合切换时的相机残留
            KillCamManager.resetForLifecycleBoundary();
        });
        ctx.get().setPacketHandled(true);
    }
}
