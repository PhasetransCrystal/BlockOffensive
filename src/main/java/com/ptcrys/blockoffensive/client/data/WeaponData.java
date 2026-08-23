package com.ptcrys.blockoffensive.client.data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record WeaponData(Map<String, List<String>> weaponData,
                         boolean bpAttributeHasHelmet,
                         int bpAttributeDurability) {

    public static final WeaponData EMPTY = new WeaponData(new HashMap<>());

    public WeaponData {
        Objects.requireNonNull(weaponData, "weaponData cannot be null");
    }

    public WeaponData(Map<String, List<String>> weaponData) {
        this(weaponData, false, 0);
    }
}