package com.ptcrys.blockoffensive.net.shop;

import com.ptcrys.blockoffensive.map.CSMap;
import com.ptcrys.blockoffensive.server.shop.ShopDropPickupService;
import com.ptcrys.fpsmatch.FPSMatch;
import com.ptcrys.fpsmatch.common.capability.team.ShopCapability;
import com.ptcrys.fpsmatch.core.FPSMCore;
import com.ptcrys.fpsmatch.core.map.BaseMap;
import com.ptcrys.fpsmatch.core.team.ServerTeam;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Client request to pick up one nearby server-owned drop entity. */
public record ShopDropPickupC2SPacket(UUID requestId, UUID entityId) {
    public static void encode(ShopDropPickupC2SPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.requestId());
        buffer.writeUUID(packet.entityId());
    }

    public static ShopDropPickupC2SPacket decode(FriendlyByteBuf buffer) {
        return new ShopDropPickupC2SPacket(buffer.readUUID(), buffer.readUUID());
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !ShopDropPickupService.acceptRequest(player, requestId())) {
                if (player != null) {
                    sendResult(player, ShopDropPickupService.Result.REJECTED,
                            ShopDropPickupService.FailureReason.DUPLICATE_REQUEST,
                            ItemStack.EMPTY);
                }
                return;
            }

            Phase phase = phase(player);
            ShopDropPickupService.Outcome outcome;
            if (!phase.allowed()) {
                outcome = new ShopDropPickupService.Outcome(
                        ShopDropPickupService.Result.REJECTED,
                        phase.reason(),
                        ItemStack.EMPTY);
            } else {
                outcome = ShopDropPickupService.pickupDetailed(
                        player,
                        entityId(),
                        ShopDropPickupService.DEFAULT_RADIUS,
                        ShopDropPickupC2SPacket::isAllowedDrop,
                        true);
            }
            sendResult(player, outcome.result(), outcome.reason(), outcome.authoritativeStack());
        });
        context.setPacketHandled(true);
    }

    private void sendResult(ServerPlayer player, ShopDropPickupService.Result result,
                            ShopDropPickupService.FailureReason reason,
                            ItemStack authoritativeStack) {
        FPSMatch.sendToPlayer(player, new ShopDropPickupResultS2CPacket(
                requestId(), entityId(), result, reason, authoritativeStack));
        // Refresh the authoritative nearby snapshot after every request, including
        // failures, so stale client rows cannot remain actionable.
        sendNearby(player);
    }

    private static void sendNearby(ServerPlayer player) {
        Phase phase = phase(player);
        FPSMatch.sendToPlayer(player, ShopNearbyDropsS2CPacket.fromService(
                ShopDropPickupService.collectNearby(
                        player,
                        ShopDropPickupService.DEFAULT_RADIUS,
                        ShopDropPickupC2SPacket::isAllowedDrop,
                        phase.allowed())));
    }

    private static Phase phase(ServerPlayer player) {
        BaseMap map = FPSMCore.getInstance().getMapByPlayer(player).orElse(null);
        if (!(map instanceof CSMap)) {
            return new Phase(false, ShopDropPickupService.FailureReason.NOT_PURCHASE_PHASE);
        }
        ServerTeam team = map.getMapTeams().getTeamByPlayer(player).orElse(null);
        ShopCapability capability = team == null
                ? null
                : team.getCapabilityMap().get(ShopCapability.class).orElse(null);
        if (capability == null || !map.canUseShop(capability, player)) {
            return new Phase(false, ShopDropPickupService.FailureReason.NOT_PURCHASE_PHASE);
        }
        // A spectator/dead player can still have a living Minecraft entity.  The
        // match's PlayerData is the authoritative living flag.
        boolean living = map.getMapTeams().getPlayerData(player)
                .map(data -> data.isLivingOnServer())
                .orElse(false);
        return living
                ? new Phase(true, ShopDropPickupService.FailureReason.NONE)
                : new Phase(false, ShopDropPickupService.FailureReason.PLAYER_NOT_ALIVE);
    }

    private static boolean isAllowedDrop(ItemStack stack) {
        return !stack.isEmpty();
    }

    private record Phase(boolean allowed, ShopDropPickupService.FailureReason reason) {
    }

}
