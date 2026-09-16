package com.ptcrys.blockoffensive.client.shop;

import java.util.function.Function;

/** Inventory order breaks ties; hover overrides ownership, including unaffordable items. */
public final class ShopPreviewSelection {
    public enum Category { PRIMARY, PISTOL, KNIFE, UTILITY, EMPTY }

    private ShopPreviewSelection() {}

    public static <T> T choose(Iterable<T> inventory, T hovered, T empty, Function<T, Category> category) {
        if (hovered != null && category.apply(hovered) != Category.EMPTY) return hovered;
        T best = empty;
        Category bestCategory = Category.EMPTY;
        for (T item : inventory) {
            Category candidate = category.apply(item);
            if (candidate.ordinal() < bestCategory.ordinal()) {
                best = item;
                bestCategory = candidate;
            }
        }
        return best;
    }
}
