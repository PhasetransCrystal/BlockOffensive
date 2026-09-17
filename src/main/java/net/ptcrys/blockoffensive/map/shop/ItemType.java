package net.ptcrys.blockoffensive.map.shop;

import net.ptcrys.fpsmatch.core.shop.INamedType;
import net.ptcrys.fpsmatch.core.shop.slot.ShopSlot;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;

public enum ItemType implements INamedType {

    EQUIPMENT(0),
    PISTOL(1),
    MID_RANK(2),
    RIFLE(3),
    THROWABLE(4, false);

    public final int typeIndex;
    public final boolean dropUnlock;

    ItemType(int typeIndex) {
        this(typeIndex, true);
    }

    ItemType(int typeIndex, boolean dropUnlock) {
        this.typeIndex = typeIndex;
        this.dropUnlock = dropUnlock;
    }

    @Override
    public int slotCount() {
        return 5;
    }

    @Override
    public boolean dorpUnlock() {
        return dropUnlock;
    }

    @Override
    public ArrayList<ShopSlot> defaultSlots() {
        ArrayList<ShopSlot> list = new ArrayList<>();
        for (int i = 0; i < this.slotCount(); i++) {
            list.add(new ShopSlot(ItemStack.EMPTY, 0));
        }
        return list;
    }
}
