package net.ptcrys.blockoffensive.net.shop;

import net.ptcrys.blockoffensive.map.CSMap;
import net.ptcrys.blockoffensive.server.shop.ShopDropPickupService;
import net.ptcrys.fpsmatch.FPSMatch;
import net.ptcrys.fpsmatch.common.capability.team.ShopCapability;
import net.ptcrys.fpsmatch.core.FPSMCore;
import net.ptcrys.fpsmatch.core.map.BaseMap;
import net.ptcrys.fpsmatch.core.team.ServerTeam;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client request to refresh the nearby-drop snapshot. */
public final class ShopNearbyDropsRequestC2SPacket {

    private final long requestId;

    public ShopNearbyDropsRequestC2SPacket() {
        this(0L);
    }

    public ShopNearbyDropsRequestC2SPacket(long requestId) {
        this.requestId = requestId;
    }

    public long requestId() {
        return requestId;
    }

    public static void encode(ShopNearbyDropsRequestC2SPacket packet, FriendlyByteBuf buffer) {
        buffer.writeLong(packet.requestId);
    }

    public static ShopNearbyDropsRequestC2SPacket decode(FriendlyByteBuf buffer) {
        return new ShopNearbyDropsRequestC2SPacket(buffer.readLong());
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !ShopDropPickupService.acceptListRequest(player)) {
                return;
            }
            BaseMap map = FPSMCore.getInstance().getMapByPlayer(player).orElse(null);
            ServerTeam team = map == null ? null : map.getMapTeams().getTeamByPlayer(player).orElse(null);
            ShopCapability capability = team == null ? null : team.getCapabilityMap().get(ShopCapability.class).orElse(null);
            boolean allowed = map instanceof CSMap && capability != null && map.canUseShop(capability, player) && map.getMapTeams().getPlayerData(player)
                    .map(data -> data.isLivingOnServer()).orElse(false);
            FPSMatch.sendToPlayer(player, ShopNearbyDropsS2CPacket.fromService(requestId(),
                    ShopDropPickupService.collectNearby(
                            player,
                            ShopDropPickupService.DEFAULT_RADIUS,
                            ShopNearbyDropsRequestC2SPacket::isAllowedDrop,
                            allowed)));
        });
        context.setPacketHandled(true);
    }

    private static boolean isAllowedDrop(net.minecraft.world.item.ItemStack stack) {
        return !stack.isEmpty();
    }
}
