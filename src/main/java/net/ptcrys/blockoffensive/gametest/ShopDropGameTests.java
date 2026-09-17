package net.ptcrys.blockoffensive.gametest;

import net.ptcrys.blockoffensive.server.shop.ShopDropPickupService;
import net.ptcrys.fpsmatch.common.entity.MatchDropEntity;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
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
public final class ShopDropGameTests {

    @GameTest(template = "empty")
    public static void shopResultChannelIsRegistered(GameTestHelper helper) {
        helper.assertTrue(net.ptcrys.fpsmatch.common.packet.register.NetworkPacketRegister.getChannelFromCache(
                net.ptcrys.fpsmatch.common.packet.shop.ShopActionResultS2CPacket.class) != null,
                "shop action result must be registered on the server before purchases");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void mixedDropsAndNativePickup(GameTestHelper helper) {
        FakePlayer player = player(helper);
        ItemEntity vanilla = new ItemEntity(helper.getLevel(), player.getX() + 2, player.getY(), player.getZ(), new ItemStack(Items.APPLE));
        vanilla.setThrower(UUID.randomUUID()); // public drop: thrower != restrictive owner
        vanilla.setNoPickUpDelay();
        vanilla.setNoGravity(true);
        helper.getLevel().addFreshEntity(vanilla);
        ItemEntity owned = new ItemEntity(helper.getLevel(), player.getX() + 3, player.getY(), player.getZ(), new ItemStack(Items.GOLD_INGOT));
        owned.setTarget(UUID.randomUUID());
        owned.setNoPickUpDelay();
        owned.setNoGravity(true);
        helper.getLevel().addFreshEntity(owned);
        MatchDropEntity match = new MatchDropEntity(helper.getLevel(), new ItemStack(Items.DIAMOND, 2));
        match.setPos(player.getX() + 4, player.getY(), player.getZ());
        match.setNoGravity(true);
        helper.getLevel().addFreshEntity(match);
        helper.assertTrue(ShopDropPickupService.pickupDetailed(player, match.getUUID(), 8, s -> true, true).reason() == ShopDropPickupService.FailureReason.PICKUP_DELAY, "match pickup delay must be enforced");
        helper.runAfterDelay(22, () -> {
            var nearby = ShopDropPickupService.collectNearby(player, 8, s -> !s.isEmpty(), true);
            helper.assertTrue(nearby.stream().anyMatch(d -> d.entityId().equals(vanilla.getUUID())), "public vanilla drop must be listed");
            helper.assertTrue(nearby.stream().anyMatch(d -> d.entityId().equals(match.getUUID())), "match drop / MISC utility must be listed");
            helper.assertTrue(nearby.stream().noneMatch(d -> d.entityId().equals(owned.getUUID())), "owner restricted drop must be omitted");
            var first = ShopDropPickupService.pickupDetailed(player, match.getUUID(), 8, s -> true, true);
            helper.assertTrue(first.accepted() && first.authoritativeStack().getCount() == 1, "native match pickup takes one");
            helper.assertTrue(match.getItem().getCount() == 1 && !match.isRemoved(), "preserve remainder entity");
            helper.assertTrue(player.getInventory().countItem(Items.DIAMOND) == 1, "exactly one delivered to inventory");
            helper.assertTrue(ShopDropPickupService.collectNearby(player, 8, s -> true, true).stream()
                    .anyMatch(d -> d.entityId().equals(match.getUUID()) && d.stack().getCount() == 1), "snapshot contains updated remainder");
            helper.assertTrue(ShopDropPickupService.pickupDetailed(player, match.getUUID(), 8, s -> true, true).accepted(), "second match pickup succeeds");
            helper.assertTrue(match.isRemoved(), "empty match drop removed");
            helper.assertTrue(!ShopDropPickupService.pickupDetailed(player, match.getUUID(), 8, s -> true, true).accepted(), "stale click cannot duplicate item");
            helper.assertTrue(ShopDropPickupService.pickupDetailed(player, vanilla.getUUID(), 8, s -> true, true).accepted(), "vanilla native pickup succeeds");
            helper.assertTrue(player.getInventory().countItem(Items.APPLE) == 1, "ordinary utility delivered");
            helper.assertTrue(ShopDropPickupService.collectNearby(player, 8, s -> true, false).isEmpty(), "closed purchase phase hides drops");
            owned.discard();
            player.getInventory().clearContent();
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void fullInventoryPreservesMatchDrop(GameTestHelper helper) {
        FakePlayer player = player(helper);
        for (int i = 0; i < player.getInventory().items.size(); i++) player.getInventory().items.set(i, new ItemStack(Items.STONE, 64));
        MatchDropEntity match = new MatchDropEntity(helper.getLevel(), new ItemStack(Items.DIAMOND));
        match.setPos(player.getX() + 2, player.getY(), player.getZ());
        match.setNoGravity(true);
        helper.getLevel().addFreshEntity(match);
        helper.runAfterDelay(22, () -> {
            var result = ShopDropPickupService.pickupDetailed(player, match.getUUID(), 8, s -> true, true);
            helper.assertTrue(result.result() == ShopDropPickupService.Result.INVENTORY_FULL, "reject before native pickup mutates the stack");
            helper.assertTrue(!match.isRemoved() && match.getItem().getCount() == 1, "failed pickup preserves entity and item");
            helper.assertTrue(player.getInventory().countItem(Items.DIAMOND) == 0, "failed pickup grants nothing");
            match.discard();
            player.getInventory().clearContent();
            helper.succeed();
        });
    }

    private static FakePlayer player(GameTestHelper helper) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "ShopTest"));
        var pos = helper.absolutePos(net.minecraft.core.BlockPos.ZERO);
        player.setPos(pos.getX(), pos.getY() + 2, pos.getZ());
        player.getInventory().clearContent();
        return player;
    }
}
