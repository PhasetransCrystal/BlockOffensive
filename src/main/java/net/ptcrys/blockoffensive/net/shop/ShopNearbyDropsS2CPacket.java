package net.ptcrys.blockoffensive.net.shop;

import net.ptcrys.blockoffensive.client.shop.ShopDropClientState;
import net.ptcrys.blockoffensive.server.shop.ShopDropPickupService;
import net.ptcrys.fpsmatch.common.drop.DropType;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Server-authenticated nearby vanilla and match-drop snapshot for the shop UI. */
public record ShopNearbyDropsS2CPacket(List<Drop> drops) {

    public static final int MAX_ENTRIES = ShopDropPickupService.MAX_NEARBY_ENTRIES;

    public ShopNearbyDropsS2CPacket {
        drops = drops == null ? List.of() : List.copyOf(drops);
        if (drops.size() > MAX_ENTRIES) {
            drops = List.copyOf(drops.subList(0, MAX_ENTRIES));
        }
    }

    public static ShopNearbyDropsS2CPacket fromService(List<ShopDropPickupService.NearbyDrop> drops) {
        return new ShopNearbyDropsS2CPacket(drops == null ? List.of() : drops.stream()
                .map(drop -> new Drop(drop.entityId(), drop.stack(), drop.type(),
                        drop.x(), drop.y(), drop.z(), drop.pickupDelay()))
                .toList());
    }

    public static void encode(ShopNearbyDropsS2CPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.drops().size());
        for (Drop drop : packet.drops()) {
            buffer.writeUUID(drop.entityId());
            buffer.writeItem(drop.stack());
            buffer.writeEnum(drop.type());
            buffer.writeDouble(drop.x());
            buffer.writeDouble(drop.y());
            buffer.writeDouble(drop.z());
            buffer.writeVarInt(Math.max(0, drop.pickupDelay()));
        }
    }

    public static ShopNearbyDropsS2CPacket decode(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_ENTRIES) {
            throw new IllegalArgumentException("Invalid nearby shop drop count: " + size);
        }
        List<Drop> drops = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            drops.add(new Drop(
                    buffer.readUUID(),
                    buffer.readItem(),
                    buffer.readEnum(DropType.class),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readVarInt()));
        }
        return new ShopNearbyDropsS2CPacket(drops);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT, () -> () -> ShopDropClientState.acceptNearby(this)));
        context.setPacketHandled(true);
    }

    public record Drop(UUID entityId, ItemStack stack, DropType type,
                       double x, double y, double z, int pickupDelay) {

        public Drop {
            if (stack == null) {
                stack = ItemStack.EMPTY;
            }
            if (type == null) {
                type = DropType.MISC;
            }
            pickupDelay = Math.max(0, pickupDelay);
        }
    }
}
