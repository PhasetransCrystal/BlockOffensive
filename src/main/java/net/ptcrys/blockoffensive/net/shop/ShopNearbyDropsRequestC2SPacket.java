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

    public static void encode(ShopNearbyDropsRequestC2SPacket packet, FriendlyByteBuf buffer) {
        // Keep this packet extensible while ensuring there is no client supplied
        // radius, category or entity state to trust.
    }

    public static ShopNearbyDropsRequestC2SPacket decode(FriendlyByteBuf buffer) {
        return new ShopNearbyDropsRequestC2SPacket();
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
            FPSMatch.sendToPlayer(player, ShopNearbyDropsS2CPacket.fromService(
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
