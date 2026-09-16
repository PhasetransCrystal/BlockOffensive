package com.ptcrys.blockoffensive.client.shop;

import com.ptcrys.blockoffensive.net.shop.ShopDropPickupC2SPacket;
import com.ptcrys.blockoffensive.net.shop.ShopDropPickupResultS2CPacket;
import com.ptcrys.blockoffensive.net.shop.ShopNearbyDropsRequestC2SPacket;
import com.ptcrys.blockoffensive.net.shop.ShopNearbyDropsS2CPacket;
import com.ptcrys.fpsmatch.common.packet.register.NetworkPacketRegister;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** Client cache only; it never mutates world entities or inventory. */
@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ShopDropClientState {
    private static final List<ShopNearbyDropsS2CPacket.Drop> DROPS = new CopyOnWriteArrayList<>();
    private static long revision;
    private ShopDropClientState() {}

    public static void acceptNearby(ShopNearbyDropsS2CPacket packet) {
        boolean changed = DROPS.size() != packet.drops().size();
        if (!changed) for (int i = 0; i < DROPS.size(); i++) {
            var previous = DROPS.get(i);
            var next = packet.drops().get(i);
            if (!previous.entityId().equals(next.entityId()) || previous.type() != next.type()
                    || !net.minecraft.world.item.ItemStack.matches(previous.stack(), next.stack())) {
                changed = true;
                break;
            }
        }
        DROPS.clear();
        DROPS.addAll(packet.drops());
        if (changed) revision++;
    }

    public static void acceptResult(ShopDropPickupResultS2CPacket packet) {
        if (packet.result() == com.ptcrys.blockoffensive.server.shop.ShopDropPickupService.Result.ACCEPTED) {
            DROPS.removeIf(drop -> drop.entityId().equals(packet.entityId()));
            revision++;
        } else if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(packet.message(), true);
        }
    }

    public static List<ShopNearbyDropsS2CPacket.Drop> nearby() { return List.copyOf(DROPS); }
    public static long revision() { return revision; }

    public static void clear() {
        DROPS.clear();
        revision++;
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    public static void requestRefresh() {
        NetworkPacketRegister.getChannelFromCache(ShopNearbyDropsRequestC2SPacket.class)
                .sendToServer(new ShopNearbyDropsRequestC2SPacket());
    }

    public static void requestPickup(UUID entityId) {
        if (entityId == null) return;
        NetworkPacketRegister.getChannelFromCache(ShopDropPickupC2SPacket.class)
                .sendToServer(new ShopDropPickupC2SPacket(UUID.randomUUID(), entityId));
    }
}
