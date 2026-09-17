package net.ptcrys.blockoffensive.client.shop;

import java.util.ArrayList;
import java.util.List;
import static net.ptcrys.blockoffensive.client.shop.ShopPreviewSelection.Category.*;

/** Standalone inventory/hover regression checks; no client or screenshots needed. */
public final class ShopPreviewSelectionCheck {
    private record Item(String name, ShopPreviewSelection.Category category) {}
    private static final Item AIR = new Item("air", EMPTY);
    private static final Item TOOL = new Item("grenade", UTILITY);
    private static final Item KNIFE_ITEM = new Item("knife", KNIFE);
    private static final Item SIDEARM = new Item("pistol", PISTOL);
    private static final Item RIFLE = new Item("rifle", PRIMARY);

    public static void main(String[] args) {
        var inventory = new ArrayList<>(List.of(TOOL, KNIFE_ITEM, SIDEARM));
        check(select(inventory, null) == SIDEARM, "pistol beats knife and utility regardless of selected slot");
        check(select(inventory, RIFLE) == RIFLE, "hover can preview an unowned or unaffordable primary");
        check(select(inventory, TOOL) == TOOL, "hover utility overrides owned pistol");
        check(select(inventory, AIR) == SIDEARM, "empty hover falls back to inventory");
        inventory.add(RIFLE); // authoritative inventory sync following successful purchase
        check(select(inventory, null) == RIFLE, "purchased primary immediately becomes default");
        check(select(inventory, SIDEARM) == SIDEARM, "new hover still overrides primary after purchase");
        inventory.remove(RIFLE); // return/drop
        check(select(inventory, null) == SIDEARM, "refund falls back to pistol");
        inventory.remove(SIDEARM);
        check(select(inventory, null) == KNIFE_ITEM, "knife beats utility without guns");
        inventory.remove(KNIFE_ITEM);
        check(select(inventory, null) == TOOL, "utility shown without weapons");
        inventory.clear();
        check(select(inventory, null) == AIR, "empty inventory shows empty hands");
        var anotherRifle = new Item("sniper", PRIMARY);
        check(select(List.of(AIR, RIFLE, anotherRifle), null) == RIFLE, "equal priority is stable across frames");
        check(select(List.of(SIDEARM), null) == SIDEARM, "failed purchase adds no phantom preview weapon");
        System.out.println("Shop preview selection checks passed (12 scenarios)");
    }

    private static Item select(List<Item> inventory, Item hover) {
        return ShopPreviewSelection.choose(inventory, hover, AIR, Item::category);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
