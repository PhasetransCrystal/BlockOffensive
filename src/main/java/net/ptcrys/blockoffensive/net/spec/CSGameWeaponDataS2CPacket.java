package net.ptcrys.blockoffensive.net.spec;

import net.ptcrys.blockoffensive.client.data.CSClientData;
import net.ptcrys.blockoffensive.client.data.WeaponData;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.*;
import java.util.function.Supplier;

public record CSGameWeaponDataS2CPacket(Map<UUID, WeaponData> weaponDataMap) {
    private static final int MAX_ENTRIES = 64;
    private static final int MAX_WEAPONS_PER_PLAYER = 64;
    private static final int MAX_VALUES_PER_WEAPON = 32;

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
        int outerSize = buf.readVarInt();
        if (outerSize < 0 || outerSize > MAX_ENTRIES) throw new IllegalArgumentException("weapon player count");
        Map<UUID, WeaponData> weaponDataMap = new LinkedHashMap<>();
        for (int i = 0; i < outerSize; i++) {
            UUID owner = buf.readUUID();
            int weaponCount = buf.readVarInt();
            if (weaponCount < 0 || weaponCount > MAX_WEAPONS_PER_PLAYER) throw new IllegalArgumentException("weapon count");
            Map<String, List<String>> weaponData = new LinkedHashMap<>();
            for (int j = 0; j < weaponCount; j++) {
                String weapon = buf.readUtf();
                int valueCount = buf.readVarInt();
                if (valueCount < 0 || valueCount > MAX_VALUES_PER_WEAPON) throw new IllegalArgumentException("weapon values");
                List<String> values = new ArrayList<>(valueCount);
                for (int k = 0; k < valueCount; k++) values.add(buf.readUtf());
                weaponData.put(weapon, values);
            }
            weaponDataMap.put(owner, new WeaponData(weaponData, buf.readBoolean(), buf.readInt()));
        }
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
