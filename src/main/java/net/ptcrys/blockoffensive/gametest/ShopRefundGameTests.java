package net.ptcrys.blockoffensive.gametest;

import net.ptcrys.blockoffensive.map.CSGameMap;
import net.ptcrys.blockoffensive.map.shop.ItemType;
import net.ptcrys.fpsmatch.common.capability.team.ShopCapability;
import net.ptcrys.fpsmatch.common.packet.shop.ShopDataSlotS2CPacket;
import net.ptcrys.fpsmatch.common.shop.functional.ReturnGoodsModule;
import net.ptcrys.fpsmatch.core.data.AreaData;
import net.ptcrys.fpsmatch.core.shop.FPSMShop;
import net.ptcrys.fpsmatch.core.shop.ShopAction;
import net.ptcrys.fpsmatch.core.shop.ShopData;
import net.ptcrys.fpsmatch.core.shop.slot.ShopSlot;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import com.google.common.collect.ImmutableList;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@GameTestHolder("blockoffensive")
@PrefixGameTestTemplate(false)
public final class ShopRefundGameTests {

    @GameTest(template = "empty")
    @SuppressWarnings("unchecked")
    public static void legacyShopNameUsesOwningTeamForRefundSnapshots(GameTestHelper helper) {
        CSGameMap map = new CSGameMap(helper.getLevel(), "shop_test_" + UUID.randomUUID().toString().substring(0, 8),
                new AreaData(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(8, 8, 8)))) {

            @Override
            public void loadConfig() {}
        };
        FakePlayer player = player(helper, "ShopIdentityTest");
        try {
            FPSMShop<ItemType> legacy = FPSMShop.create(ItemType.class, "test", 800);
            legacy.setDefaultShopDataItemStack(ItemType.EQUIPMENT.name(), 0, new ItemStack(Items.APPLE));
            legacy.setDefaultShopDataCost(ItemType.EQUIPMENT.name(), 0, 100);
            legacy.addArea(new AreaData(BlockPos.ZERO, new BlockPos(8, 8, 8)));
            var saved = legacy.codec.encodeStart(JsonOps.INSTANCE, legacy).getOrThrow(false, message -> {});
            var decoded = legacy.codec.parse(JsonOps.INSTANCE, saved).getOrThrow(false, message -> {});

            for (var team : List.of(map.getCT(), map.getT())) {
                ShopCapability cap = team.getCapabilityMap().get(ShopCapability.class).orElseThrow();
                cap.write(decoded);
                FPSMShop<ItemType> shop = (FPSMShop<ItemType>) cap.getShop();
                helper.assertTrue(shop.getName().equals(team.name), "loaded legacy shop must use the owning team name");
                helper.assertTrue(shop.getStartMoney() == 800 && shop.getAreas().size() == 1,
                        "normalizing a legacy shop must preserve economy and purchase areas");
                var data = shop.getPlayerShopData(player);
                helper.assertTrue(data.handleButton(player, ItemType.EQUIPMENT, 0, ShopAction.BUY).accepted(),
                        "loaded shop must allow buying its configured item");
                ShopSlot slot = data.getShopSlotsByType(ItemType.EQUIPMENT).get(0);
                var bought = new ShopDataSlotS2CPacket(shop.getName(), ItemType.EQUIPMENT, slot);
                helper.assertTrue(bought.shopName.equals(team.name) && bought.boughtCount == 1 && !bought.locked,
                        "purchase snapshot must pass the client team filter and expose refund eligibility");
                helper.assertTrue(data.handleButton(player, ItemType.EQUIPMENT, 0, ShopAction.RETURN).accepted(),
                        "newly purchased item must be refundable");
                var refunded = new ShopDataSlotS2CPacket(shop.getName(), ItemType.EQUIPMENT, slot);
                helper.assertTrue(refunded.shopName.equals(team.name) && refunded.boughtCount == 0 && data.getMoney() == 800,
                        "refund snapshot must clear the marker and restore the balance");

                var replacementData = legacy.getPlayerShopData(player);
                helper.assertTrue(replacementData.handleButton(player, ItemType.EQUIPMENT, 0, ShopAction.BUY).accepted(),
                        "replacement shop must have an existing purchase to preserve");
                cap.setShop(legacy);
                FPSMShop<ItemType> replacement = (FPSMShop<ItemType>) cap.getShop();
                helper.assertTrue(replacement.getName().equals(team.name) && replacement.getPlayerShopData(player) == replacementData && replacementData.getMoney() == 700,
                        "replacing a shop must normalize its team identity without losing player state");
                helper.assertTrue(replacementData.handleButton(player, ItemType.EQUIPMENT, 0, ShopAction.RETURN).accepted(),
                        "preserved purchases must remain refundable after shop replacement");
            }
            helper.assertTrue(decoded.getName().equals("test"), "normalizing one team must not rename another team's source");
        } finally {
            player.getInventory().clearContent();
            map.getMapTeams().shutdown(helper.getLevel().getScoreboard());
        }
        helper.succeed();
    }

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
