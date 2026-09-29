package net.ptcrys.blockoffensive.gametest;

import net.ptcrys.blockoffensive.map.shop.ItemType;
import net.ptcrys.fpsmatch.common.shop.functional.ReturnGoodsModule;
import net.ptcrys.fpsmatch.core.shop.ShopAction;
import net.ptcrys.fpsmatch.core.shop.ShopData;
import net.ptcrys.fpsmatch.core.shop.slot.ShopSlot;

import net.minecraft.network.chat.Component;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import com.mojang.authlib.GameProfile;
import com.google.common.collect.ImmutableList;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

@GameTestHolder("blockoffensive")
@PrefixGameTestTemplate(false)
public final class ShopRefundGameTests {

    @GameTest(template = "empty")
    public static void copiedSlotRefundsChangedItem(GameTestHelper helper) {
        ShopSlot template = new ShopSlot(new ItemStack(Items.APPLE), 100);
        ShopSlot playerSlot = template.copy();
        playerSlot.setItemSupplier(() -> new ItemStack(Items.DIAMOND));
        playerSlot.lock(1);
        playerSlot.unlock();

        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "RefundTest"));
        player.getInventory().clearContent();
        player.getInventory().add(new ItemStack(Items.DIAMOND));

        helper.assertTrue(playerSlot.canReturn(player), "the copied slot must match its current item");
        playerSlot.returnItem(player);
        helper.assertTrue(player.getInventory().countItem(Items.DIAMOND) == 0, "refund must remove the purchased item");
        helper.assertTrue(playerSlot.getBoughtCount() == 0, "refund must clear the purchased count");
        helper.assertTrue(template.process().is(Items.APPLE), "player item changes must not affect the template");
        player.getInventory().clearContent();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void groupedRefundFundsReplacement(GameTestHelper helper) {
        ShopSlot oldSlot = new ShopSlot(new ItemStack(Items.APPLE), 400, 1, 1);
        oldSlot.addListener(new ReturnGoodsModule());
        oldSlot.lock(1);
        oldSlot.unlock();
        ShopSlot newSlot = new ShopSlot(new ItemStack(Items.DIAMOND), 500, 1, 1);
        ShopData<ItemType> data = shopData(200, oldSlot, newSlot);
        FakePlayer player = player(helper, "GroupRefundTest");
        player.getInventory().add(new ItemStack(Items.APPLE));

        helper.assertTrue(data.handleButton(player, ItemType.EQUIPMENT, 1, ShopAction.BUY).accepted(),
                "cash plus grouped refund must fund the replacement");
        helper.assertTrue(data.getMoney() == 100, "replacement must charge the net amount");
        helper.assertTrue(player.getInventory().countItem(Items.APPLE) == 0, "old item must be returned");
        helper.assertTrue(player.getInventory().countItem(Items.DIAMOND) == 1, "new item must be delivered");
        player.getInventory().clearContent();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void carriedItemsCountTowardPurchaseLimit(GameTestHelper helper) {
        ShopSlot slot = new ShopSlot(new ItemStack(Items.APPLE), 100, 2);
        ShopData<ItemType> data = shopData(800, slot);
        FakePlayer player = player(helper, "CarryLimitTest");
        player.getInventory().add(new ItemStack(Items.APPLE));

        data.lockShopSlots(player);
        helper.assertTrue(slot.getBoughtCount() == 0 && slot.getCountAgainstLimit() == 1,
                "carried item must occupy a slot without becoming refundable");
        helper.assertTrue(slot.canBuy(800), "one remaining purchase must be allowed");
        helper.assertTrue(data.handleButton(player, ItemType.EQUIPMENT, 0, ShopAction.BUY).accepted(),
                "the second item must be purchasable");
        helper.assertTrue(slot.getBoughtCount() == 1 && slot.getCountAgainstLimit() == 2 && !slot.canBuy(700),
                "carried and newly bought items must share the limit");
        data.lockShopSlots(player);
        helper.assertTrue(slot.getBoughtCount() == 0 && slot.getCountAgainstLimit() == 2 && !slot.canBuy(700),
                "next round must count both items without allowing a refund");
        slot.unlock(1);
        helper.assertTrue(slot.getCountAgainstLimit() == 1 && slot.canBuy(700),
                "dropping one carried item must free one purchase slot");
        slot.lockPickedUp(1);
        helper.assertTrue(slot.getBoughtCount() == 0 && slot.getCountAgainstLimit() == 2 && !slot.canReturn(player),
                "picking a carried item back up must not make it refundable");
        player.getInventory().clearContent();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sameNameDifferentItemsKeepTheirSlots(GameTestHelper helper) {
        ItemStack apple = new ItemStack(Items.APPLE);
        apple.setHoverName(Component.literal("Same name"));
        ItemStack diamond = new ItemStack(Items.DIAMOND);
        diamond.setHoverName(Component.literal("Same name"));
        ShopSlot appleSlot = new ShopSlot(apple, 100);
        ShopSlot diamondSlot = new ShopSlot(diamond, 100);
        ShopData<ItemType> data = shopData(800, appleSlot, diamondSlot);

        helper.assertTrue(data.checkItemStackIsInData(apple).getSecond() == appleSlot,
                "apple must not match the same-named diamond slot");
        helper.assertTrue(data.checkItemStackIsInData(diamond).getSecond() == diamondSlot,
                "diamond must keep its own slot");
        helper.succeed();
    }

    private static FakePlayer player(GameTestHelper helper, String name) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        player.getInventory().clearContent();
        return player;
    }

    private static ShopData<ItemType> shopData(int money, ShopSlot... equipmentSlots) {
        Map<ItemType, ImmutableList<ShopSlot>> slots = new EnumMap<>(ItemType.class);
        for (ItemType type : ItemType.values()) {
            var category = type.defaultSlots();
            if (type == ItemType.EQUIPMENT) {
                for (int i = 0; i < equipmentSlots.length; i++) category.set(i, equipmentSlots[i]);
            }
            slots.put(type, ImmutableList.copyOf(category));
        }
        return new ShopData<>(slots, ItemType.values().length, money);
    }
}
