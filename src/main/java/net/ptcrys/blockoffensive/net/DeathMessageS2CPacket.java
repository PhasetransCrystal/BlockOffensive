package net.ptcrys.blockoffensive.net;

import net.ptcrys.blockoffensive.client.screen.hud.CSGameHud;
import net.ptcrys.blockoffensive.data.DeathMessage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class DeathMessageS2CPacket {

    private final DeathMessage deathMessage;

    public DeathMessageS2CPacket(DeathMessage deathMessage) {
        this.deathMessage = deathMessage;
    }

    public DeathMessage deathMessage() {
        return deathMessage;
    }

    public static void encode(DeathMessageS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeComponent(packet.deathMessage.getKiller());
        buf.writeUUID(packet.deathMessage.getKillerUUID());
        if (packet.deathMessage.getAssist() != null) {
            buf.writeComponent(packet.deathMessage.getAssist());
            buf.writeUUID(packet.deathMessage.getAssistUUID());
        } else {
            buf.writeComponent(packet.deathMessage.getKiller());
            buf.writeUUID(packet.deathMessage.getKillerUUID());
        }
        buf.writeComponent(packet.deathMessage.getDead());
        buf.writeUUID(packet.deathMessage.getDeadUUID());
        buf.writeItem(packet.deathMessage.getWeapon());
        buf.writeUtf(packet.deathMessage.getArg());

        byte flags = 0;
        flags |= (byte) (packet.deathMessage.isHeadShot() ? 1 : 0);
        flags |= (byte) (packet.deathMessage.isBlinded() ? 2 : 0);
        flags |= (byte) (packet.deathMessage.isThroughSmoke() ? 4 : 0);
        flags |= (byte) (packet.deathMessage.isThroughWall() ? 8 : 0);
        flags |= (byte) (packet.deathMessage.isNoScope() ? 16 : 0);
        flags |= (byte) (packet.deathMessage.isFlying() ? 32 : 0);
        buf.writeByte(flags);
    }

    public static DeathMessageS2CPacket decode(FriendlyByteBuf buf) {
        Component killer = buf.readComponent();
        UUID killerUUID = buf.readUUID();
        Component assist = buf.readComponent();
        UUID assistUUID = buf.readUUID();
        Component dead = buf.readComponent();
        UUID deadUUID = buf.readUUID();
        ItemStack weapon = buf.readItem();
        String arg = buf.readUtf();
        byte flags = buf.readByte();

        return new DeathMessageS2CPacket(new DeathMessage.Builder(killer, killerUUID, dead, deadUUID, weapon)
                .setAssist(assist, assistUUID)
                .setArg(arg)
                .setHeadShot((flags & 1) != 0)
                .setBlinded((flags & 2) != 0)
                .setThroughSmoke((flags & 4) != 0)
                .setThroughWall((flags & 8) != 0)
                .setNoScope((flags & 16) != 0)
                .setFlying((flags & 32) != 0)
                .build());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            CSGameHud.getInstance().getDeathMessageHud().addKillMessage(deathMessage);
            LocalPlayer localPlayer = Minecraft.getInstance().player;
            if (localPlayer == null) {
                return;
            }
            boolean isLocalKill = deathMessage.getKillerUUID().equals(localPlayer.getUUID());
            boolean isLocalDead = deathMessage.getDeadUUID().equals(localPlayer.getUUID());
            if (isLocalKill && !isLocalDead) {
                CSGameHud.getInstance().addKill(deathMessage);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
