package com.ptcrys.blockoffensive.net.spec;

import com.ptcrys.blockoffensive.client.data.CSClientData;
import com.ptcrys.blockoffensive.client.data.WeaponData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.*;
import java.util.function.Supplier;

public record CSGameWeaponDataS2CPacket(Map<UUID, WeaponData> weaponDataMap) {
    public static void encode(CSGameWeaponDataS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeMap(packet.weaponDataMap, FriendlyByteBuf::writeUUID,
                (b, weaponData) -> {
                    b.writeMap(weaponData.weaponData(), FriendlyByteBuf::writeUtf,
                            (b1, list) -> b1.writeCollection(list, FriendlyByteBuf::writeUtf));
                    b.writeBoolean(weaponData.bpAttributeHasHelmet());
                    b.writeInt(weaponData.bpAttributeDurability());
                });
    }

    public static CSGameWeaponDataS2CPacket decode(FriendlyByteBuf buf) {
        Map<UUID, WeaponData> weaponDataMap = buf.readMap(
                FriendlyByteBuf::readUUID,
                b -> {
                    Map<String, List<String>> weaponData = b.readMap(
                            FriendlyByteBuf::readUtf,
                            b1 -> b1.readList(FriendlyByteBuf::readUtf)
                    );
                    boolean hasHelmet = b.readBoolean();
                    int durability = b.readInt();

                    return new WeaponData(weaponData, hasHelmet, durability);
                }
        );
        return new CSGameWeaponDataS2CPacket(weaponDataMap);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            synchronized (CSClientData.weaponData) {
                CSClientData.weaponData.putAll(weaponDataMap);

                Set<UUID> keysToRemove = new HashSet<>();
                for (UUID uuid : CSClientData.weaponData.keySet()) {
                    if (!weaponDataMap.containsKey(uuid)) {
                        keysToRemove.add(uuid);
                    }
                }
                for (UUID uuid : keysToRemove) {
                    CSClientData.weaponData.remove(uuid);
                }
            }
        });
        context.setPacketHandled(true);
    }
}