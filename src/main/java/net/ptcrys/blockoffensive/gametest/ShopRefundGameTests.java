package net.ptcrys.blockoffensive.gametest;

import net.ptcrys.fpsmatch.core.shop.slot.ShopSlot;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import com.mojang.authlib.GameProfile;

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
}
