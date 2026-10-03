package net.ptcrys.blockoffensive.gametest;

import net.ptcrys.fpsmatch.common.packet.shop.SaveShopSlotConfigurationC2SPacket;
import net.ptcrys.fpsmatch.common.packet.shop.ShopEditorResultS2CPacket;
import net.ptcrys.fpsmatch.common.shop.editor.ShopEditorResult;
import net.ptcrys.fpsmatch.common.shop.editor.ShopEditorService;
import net.ptcrys.fpsmatch.common.shop.editor.ShopEditorSnapshot;
import net.ptcrys.fpsmatch.core.shop.FPSMShop;
import net.ptcrys.fpsmatch.core.shop.INamedType;
import net.ptcrys.fpsmatch.core.shop.slot.ShopSlot;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import io.netty.buffer.Unpooled;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@GameTestHolder("blockoffensive")
@PrefixGameTestTemplate(false)
public final class ShopEditorGameTests {

    private static final ShopEditorSnapshot.Target TARGET = new ShopEditorSnapshot.Target("test", "map", "team");

    enum Type implements INamedType {

        FIRST,
        SECOND;

        public int slotCount() {
            return 2;
        }

        public boolean dorpUnlock() {
            return true;
        }

        public ArrayList<ShopSlot> defaultSlots() {
            return new ArrayList<>(List.of(new ShopSlot(new ItemStack(Items.APPLE), 50), new ShopSlot(new ItemStack(Items.GOLD_INGOT), 100)));
        }
    }

    private static FPSMShop<Type> shop() {
        Map<Type, ArrayList<ShopSlot>> slots = new LinkedHashMap<>();
        for (Type type : Type.values()) slots.put(type, type.defaultSlots());
        return new FPSMShop<>(Type.class, "test", slots, 800);
    }

    private static ShopEditorSnapshot.Slot draft(ItemStack item, int price, int group) {
        return new ShopEditorSnapshot.Slot(item, price, 0, group, 1, List.of());
    }

    @GameTest(template = "empty")
    public static void commitsWithoutAnyPlayerContainerAndRebindsRefundChecker(GameTestHelper helper) {
        var shop = shop();
        String before = ShopEditorService.revision(shop);
        assertEquals(ShopEditorResult.SUCCESS, ShopEditorService.applySlot(shop, before, "FIRST", 0, draft(new ItemStack(Items.DIAMOND, 2), 250, 7), Map.of()));
        var saved = shop.getDefaultShopSlotListByType("FIRST").get(0);
        assertEquals(250, saved.getDefaultCost());
        assertEquals(7, saved.getGroupId());
        assertEquals(2, saved.process().getCount());
        assertTrue(saved.returningChecker.test(new ItemStack(Items.DIAMOND)));
        assertFalse(saved.returningChecker.test(new ItemStack(Items.APPLE)));
        assertNotEquals(before, ShopEditorService.revision(shop));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void staleDraftCannotOverwriteCommandEditsOrReinitializedShop(GameTestHelper helper) {
        var shop = shop();
        String before = ShopEditorService.revision(shop);
        shop.getDefaultShopSlotListByType("SECOND").get(1).setDefaultCost(999);
        assertEquals(ShopEditorResult.CONFLICT, ShopEditorService.applySlot(shop, before, "FIRST", 0, draft(new ItemStack(Items.DIAMOND), 250, 7), Map.of()));
        assertEquals(50, shop.getDefaultShopSlotListByType("FIRST").get(0).getDefaultCost());
        assertEquals(999, shop.getDefaultShopSlotListByType("SECOND").get(1).getDefaultCost());
        assertEquals(ShopEditorResult.CONFLICT, ShopEditorService.applySlot(shop(), before, "FIRST", 0, draft(new ItemStack(Items.DIAMOND), 250, 7), Map.of()));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void runtimePurchaseStateDoesNotInvalidateConfiguration(GameTestHelper helper) {
        var shop = shop();
        String before = ShopEditorService.revision(shop);
        var slot = shop.getDefaultShopSlotListByType("FIRST").get(0);
        slot.lock(1);
        slot.setCost(13);
        assertEquals(before, ShopEditorService.revision(shop));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void versionIncludesFullItemNbtButNotCompoundInsertionOrder(GameTestHelper helper) {
        var shop = shop();
        ItemStack item = new ItemStack(Items.APPLE);
        CompoundTag first = new CompoundTag();
        first.putInt("Aa", 1);
        first.putInt("BB", 2);
        item.setTag(first);
        shop.getDefaultShopSlotListByType("FIRST").get(0).setItemSupplier(item::copy);
        String before = ShopEditorService.revision(shop);
        CompoundTag second = new CompoundTag();
        second.putInt("BB", 2);
        second.putInt("Aa", 1);
        item.setTag(second);
        assertEquals(before, ShopEditorService.revision(shop));
        second.putInt("Aa", 3);
        assertNotEquals(before, ShopEditorService.revision(shop));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void batchValidatesEntireSelectionBeforeChangingAnything(GameTestHelper helper) {
        var shop = shop();
        String before = ShopEditorService.revision(shop);
        for (int[] indices : List.of(new int[] { 0, 4 }, new int[] { 0, 0 }, new int[] { -1 }, new int[] {})) {
            assertEquals(ShopEditorResult.INVALID_VALUE, ShopEditorService.applyGroups(shop, before, 12, indices));
            assertEquals(before, ShopEditorService.revision(shop));
        }
        assertEquals(ShopEditorResult.SUCCESS, ShopEditorService.applyGroups(shop, before, 12, new int[] { 0, 3 }));
        assertEquals(12, shop.getDefaultShopSlotListByType("FIRST").get(0).getGroupId());
        assertEquals(12, shop.getDefaultShopSlotListByType("SECOND").get(1).getGroupId());
        assertEquals(-1, shop.getDefaultShopSlotListByType("SECOND").get(0).getGroupId());
        assertEquals(ShopEditorResult.CONFLICT, ShopEditorService.applyGroups(shop, before, 13, new int[] { 1 }));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void invalidFieldsItemsModulesAndSlotsLeaveTemplateIntact(GameTestHelper helper) {
        var shop = shop();
        String before = ShopEditorService.revision(shop);
        assertEquals(ShopEditorResult.INVALID_VALUE, ShopEditorService.applySlot(shop, before, "FIRST", 0, draft(new ItemStack(Items.APPLE), -1, 0), Map.of()));
        assertEquals(ShopEditorResult.INVALID_ITEM, ShopEditorService.applySlot(shop, before, "FIRST", 0, draft(ItemStack.EMPTY, 5, 0), Map.of()));
        assertEquals(ShopEditorResult.INVALID_ITEM, ShopEditorService.applySlot(shop, before, "FIRST", 0, draft(new ItemStack(Items.APPLE, 65), 5, 0), Map.of()));
        var unknownModule = new ShopEditorSnapshot.Slot(new ItemStack(Items.APPLE), 5, 0, -1, 1, List.of("server-only"));
        assertEquals(ShopEditorResult.INVALID_MODULE, ShopEditorService.applySlot(shop, before, "FIRST", 0, unknownModule, Map.of()));
        assertEquals(ShopEditorResult.INVALID_SLOT, ShopEditorService.applySlot(shop, before, "missing", 0, draft(new ItemStack(Items.APPLE), 5, 0), Map.of()));
        assertEquals(ShopEditorResult.INVALID_SLOT, ShopEditorService.applySlot(shop, before, "FIRST", 2, draft(new ItemStack(Items.APPLE), 5, 0), Map.of()));
        assertEquals(before, ShopEditorService.revision(shop));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void productSamplesAndSnapshotAccessorsNeverMutateInventoryObjects(GameTestHelper helper) {
        ItemStack inventoryItem = new ItemStack(Items.APPLE, 3);
        var draft = draft(inventoryItem, 5, 0);
        inventoryItem.setCount(4);
        assertEquals(3, draft.item().getCount());
        draft.item().setCount(1);
        assertEquals(3, draft.item().getCount());
        assertEquals(4, inventoryItem.getCount());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void snapshotAndSaveWireFormatsPreserveServerOnlyModuleNamesAndNbt(GameTestHelper helper) {
        ItemStack item = new ItemStack(Items.APPLE, 3);
        item.getOrCreateTag().putString("sample", "full-nbt");
        var slot = new ShopEditorSnapshot.Slot(item, 900_000, 99_999, 50_000, 1, List.of("server-only"));
        var snapshot = new ShopEditorSnapshot(TARGET, "revision", List.of(new ShopEditorSnapshot.Category("FIRST", List.of(slot))), List.of("server-only"));
        var result = new ShopEditorResultS2CPacket(123, ShopEditorResultS2CPacket.Operation.LOAD, TARGET, ShopEditorResult.SUCCESS, snapshot);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ShopEditorResultS2CPacket.encode(result, buf);
            var decoded = ShopEditorResultS2CPacket.decode(buf);
            assertEquals(123, decoded.requestId());
            assertEquals(TARGET, decoded.target());
            var decodedSlot = decoded.snapshot().slots().get(0);
            assertEquals(slot.modules(), decodedSlot.modules());
            assertTrue(ItemStack.matches(item, decodedSlot.item()));
            assertEquals(900_000, decodedSlot.price());
            assertEquals(99_999, decodedSlot.ammo());
            buf.clear();
            var save = new SaveShopSlotConfigurationC2SPacket(456, TARGET, "revision", "FIRST", 0, slot);
            SaveShopSlotConfigurationC2SPacket.encode(save, buf);
            var decodedSave = SaveShopSlotConfigurationC2SPacket.decode(buf);
            assertEquals(456, decodedSave.requestId());
            assertTrue(ItemStack.matches(item, decodedSave.draft().item()));
            assertEquals(slot.modules(), decodedSave.draft().modules());
        } finally {
            buf.release();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void restoringOldContentNeverRevivesAnOldRevision(GameTestHelper helper) {
        var shop = shop();
        String original = ShopEditorService.revision(shop);
        for (int i = 0; i < 132; i++) {
            String revision = ShopEditorService.revision(shop);
            assertEquals(ShopEditorResult.SUCCESS, ShopEditorService.applyGroups(shop, revision, i % 2 == 0 ? 1 : -1, new int[] { 0 }));
        }
        assertEquals(-1, shop.getDefaultShopSlotListByType("FIRST").get(0).getGroupId());
        assertNotEquals(original, ShopEditorService.revision(shop));
        assertEquals(ShopEditorResult.CONFLICT, ShopEditorService.applyGroups(shop, original, 1, new int[] { 0 }));
        helper.succeed();
    }

    private static void assertTrue(boolean value) {
        if (!value) throw new AssertionError("Expected true");
    }

    private static void assertFalse(boolean value) {
        if (value) throw new AssertionError("Expected false");
    }

    private static void assertEquals(long expected, long actual) {
        if (expected != actual) throw new AssertionError("Expected " + expected + ", got " + actual);
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }

    private static void assertNotEquals(Object first, Object second) {
        if (Objects.equals(first, second)) throw new AssertionError("Expected distinct configuration revisions");
    }
}
