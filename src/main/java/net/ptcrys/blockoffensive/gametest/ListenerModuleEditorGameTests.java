package net.ptcrys.blockoffensive.gametest;

import net.ptcrys.fpsmatch.common.packet.shop.ListenerModuleActionC2SPacket;
import net.ptcrys.fpsmatch.common.packet.shop.ListenerModuleActionC2SPacket.Action;
import net.ptcrys.fpsmatch.common.packet.shop.ListenerModuleResultS2CPacket;
import net.ptcrys.fpsmatch.common.shop.editor.*;
import net.ptcrys.fpsmatch.common.shop.editor.ListenerModuleSnapshot.Definition;
import net.ptcrys.fpsmatch.common.shop.functional.ChangeShopItemModule;
import net.ptcrys.fpsmatch.core.FPSMCore;
import net.ptcrys.fpsmatch.core.shop.FPSMShop;
import net.ptcrys.fpsmatch.core.shop.INamedType;
import net.ptcrys.fpsmatch.core.shop.functional.LMManager;
import net.ptcrys.fpsmatch.core.shop.functional.ListenerModule;
import net.ptcrys.fpsmatch.core.shop.slot.ShopSlot;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;

import java.nio.file.Files;
import java.util.*;

@GameTestHolder("blockoffensive")
@PrefixGameTestTemplate(false)
public final class ListenerModuleEditorGameTests {

    private static final ShopEditorSnapshot.Target TARGET = new ShopEditorSnapshot.Target("test", "map", "team");

    enum Type implements INamedType {

        EQUIPMENT;

        public int slotCount() {
            return 1;
        }

        public boolean dorpUnlock() {
            return true;
        }

        public ArrayList<ShopSlot> defaultSlots() {
            return new ArrayList<>(List.of(new ShopSlot(new ItemStack(Items.APPLE), 50, 1, 7)));
        }
    }

    private static FPSMShop<Type> shop(ListenerModule module) {
        var slot = Type.EQUIPMENT.defaultSlots().get(0);
        slot.addListener(module);
        return new FPSMShop<>(Type.class, "test", Map.of(Type.EQUIPMENT, new ArrayList<>(List.of(slot))), 800);
    }

    private static Definition definition(String name, int price) {
        return new Definition(name, new ItemStack(Items.APPLE), 50, new ItemStack(Items.GOLDEN_APPLE), price);
    }

    private static void equal(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }

    private static String revision(LMManager manager, List<ListenerModuleService.ShopRef> refs) {
        return ListenerModuleService.snapshot(manager, refs).revision();
    }

    @GameTest(template = "empty")
    public static void updatesEveryShopReferenceAndKeepsStableIdentity(GameTestHelper helper) {
        LMManager manager = new LMManager();
        var original = definition("changeItem_shared_test", 300).create();
        manager.addListenerType(original);
        var first = shop(original);
        var second = shop(original);
        var refs = List.of(new ListenerModuleService.ShopRef(TARGET, first), new ListenerModuleService.ShopRef(new ShopEditorSnapshot.Target("test", "other", "other"), second));
        var changed = new Definition(original.getName(), new ItemStack(Items.DIAMOND), 100, new ItemStack(Items.EMERALD), 400);
        equal(2, ListenerModuleService.snapshot(manager, refs).modules().stream().filter(m -> m.name().equals(original.getName())).findFirst().orElseThrow().references().size());
        equal(ShopEditorResult.SUCCESS, ListenerModuleService.apply(manager, refs, revision(manager, refs), Action.UPDATE, changed, (action, module) -> {}));
        for (var shop : List.of(first, second)) {
            var slot = shop.getDefaultShopSlotListByType("EQUIPMENT").get(0);
            equal(List.of(original.getName()), slot.getListenerNames());
            slot.reset();
            equal(Items.DIAMOND, slot.process().getItem());
            equal(true, slot.returningChecker.test(new ItemStack(Items.DIAMOND)));
            equal(false, slot.returningChecker.test(new ItemStack(Items.APPLE)));
            var playerSlot = slot.copy();
            playerSlot.itemSupplier = () -> new ItemStack(Items.EMERALD);
            equal(true, playerSlot.returningChecker.test(new ItemStack(Items.EMERALD)));
            equal(false, slot.returningChecker.test(new ItemStack(Items.EMERALD)));
            equal(100, slot.getCost());
            equal(7, slot.getGroupId());
        }
        equal(original.getName(), manager.getListenerModule(original.getName()).getName());
        equal(300, original.changedCost());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void preventsReferencedDeletionAndChecksNewReferencesBeforeWrites(GameTestHelper helper) {
        LMManager manager = new LMManager();
        var module = definition("changeItem_dependency_test", 300).create();
        manager.addListenerType(module);
        var shop = shop(module);
        var refs = List.of(new ListenerModuleService.ShopRef(TARGET, shop));
        equal(ShopEditorResult.MODULE_IN_USE, ListenerModuleService.apply(manager, refs, revision(manager, refs), Action.DELETE, definition(module.getName(), 300), (action, value) -> { throw new AssertionError("Must not persist referenced deletion"); }));
        String stale = revision(manager, List.of());
        equal(ShopEditorResult.CONFLICT, ListenerModuleService.apply(manager, refs, stale, Action.DELETE, definition(module.getName(), 300), (action, value) -> { throw new AssertionError("Must not persist stale deletion"); }));
        shop.getDefaultShopSlotListByType("EQUIPMENT").get(0).removeListenerModule(module.getName());
        equal(ShopEditorResult.SUCCESS, ListenerModuleService.apply(manager, refs, revision(manager, refs), Action.DELETE, definition(module.getName(), 300), (action, value) -> {}));
        equal(null, manager.getListenerModule(module.getName()));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rejectsCollisionsInvalidItemsAndCodeRegisteredModules(GameTestHelper helper) {
        LMManager manager = new LMManager();
        var draft = definition("changeItem_validation_test", 300);
        equal(ShopEditorResult.SUCCESS, ListenerModuleService.apply(manager, List.of(), revision(manager, List.of()), Action.CREATE, draft, (action, module) -> {}));
        equal(ShopEditorResult.MODULE_EXISTS, ListenerModuleService.apply(manager, List.of(), revision(manager, List.of()), Action.CREATE, draft, (action, module) -> { throw new AssertionError(); }));
        equal(ShopEditorResult.MODULE_EXISTS, ListenerModuleService.apply(manager, List.of(), revision(manager, List.of()), Action.CREATE, definition("changeItem_VALIDATION_TEST", 300), (action, module) -> { throw new AssertionError(); }));
        equal(ShopEditorResult.INVALID_VALUE, ListenerModuleService.apply(manager, List.of(), revision(manager, List.of()), Action.CREATE, definition("changeItem_../escape", 300), (action, module) -> { throw new AssertionError(); }));
        equal(ShopEditorResult.INVALID_ITEM, ListenerModuleService.apply(manager, List.of(), revision(manager, List.of()), Action.CREATE, new Definition("changeItem_empty_test", ItemStack.EMPTY, 1, new ItemStack(Items.APPLE), 2), (action, module) -> { throw new AssertionError(); }));
        equal(ShopEditorResult.MODULE_READ_ONLY, ListenerModuleService.apply(manager, List.of(), revision(manager, List.of()), Action.DELETE, definition("returnGoods", 300), (action, module) -> { throw new AssertionError(); }));
        equal(ShopEditorResult.MODULE_READ_ONLY, ListenerModuleService.apply(manager, List.of(), revision(manager, List.of()), Action.DELETE, definition("changeItem_minecraft_apple", 300), (action, module) -> { throw new AssertionError(); }));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void persistenceFailureLeavesDefinitionsAndAllSlotsIntact(GameTestHelper helper) {
        LMManager manager = new LMManager();
        var original = definition("changeItem_failure_test", 300).create();
        manager.addListenerType(original);
        var shop = shop(original);
        var refs = List.of(new ListenerModuleService.ShopRef(TARGET, shop));
        var slot = shop.getDefaultShopSlotListByType("EQUIPMENT").get(0);
        String before = revision(manager, refs);
        try {
            ListenerModuleService.apply(manager, refs, before, Action.UPDATE, definition(original.getName(), 999), (action, module) -> { throw new IllegalStateException("disk unavailable"); });
            throw new AssertionError("Expected persistence failure");
        } catch (IllegalStateException expected) {
            equal("disk unavailable", expected.getMessage());
        }
        equal(original, manager.getListenerModule(original.getName()));
        equal(slot, shop.getDefaultShopSlotListByType("EQUIPMENT").get(0));
        equal(before, revision(manager, refs));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void supportsLegacyCodecAndCopiesFullItemData(GameTestHelper helper) {
        var legacy = new ChangeShopItemModule(new ItemStack(Items.APPLE), 50, new ItemStack(Items.GOLDEN_APPLE), 300);
        var json = ChangeShopItemModule.CODEC.encodeStart(JsonOps.INSTANCE, legacy).result().orElseThrow().getAsJsonObject();
        json.remove("moduleName");
        var decoded = ChangeShopItemModule.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow();
        equal("changeItem_minecraft_apple", decoded.getName());
        var named = definition("changeItem_stable_test", 300).create();
        equal(named.getName(), ChangeShopItemModule.CODEC.parse(JsonOps.INSTANCE, ChangeShopItemModule.CODEC.encodeStart(JsonOps.INSTANCE, named).result().orElseThrow()).result().orElseThrow().getName());
        named.defaultItem().setCount(42);
        equal(1, named.defaultItem().getCount());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void catalogAndActionPacketsRoundTripDefinitionsAndReferences(GameTestHelper helper) {
        LMManager manager = new LMManager();
        var definition = definition("changeItem_wire_test", 300);
        definition.changedItem().getOrCreateTag().putString("lost", "copy");
        var item = new ItemStack(Items.GOLDEN_APPLE);
        item.getOrCreateTag().putString("rule", "retained");
        definition = new Definition(definition.name(), definition.defaultItem(), 50, item, 300);
        manager.addListenerType(definition.create());
        var refs = List.of(new ListenerModuleService.ShopRef(TARGET, shop(definition.create())));
        var catalog = ListenerModuleService.snapshot(manager, refs);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var request = new ListenerModuleActionC2SPacket(13, TARGET, Action.UPDATE, catalog.revision(), definition);
            ListenerModuleActionC2SPacket.encode(request, buf);
            var decoded = ListenerModuleActionC2SPacket.decode(buf);
            equal("retained", decoded.draft().changedItem().getTag().getString("rule"));
            buf.clear();
            ListenerModuleResultS2CPacket.encode(new ListenerModuleResultS2CPacket(13, TARGET, Action.UPDATE, ShopEditorResult.SUCCESS, catalog, null), buf);
            var reply = ListenerModuleResultS2CPacket.decode(buf);
            var module = reply.catalog().modules().stream().filter(m -> m.name().equals(request.draft().name())).findFirst().orElseThrow();
            equal(TARGET, module.references().get(0).target());
            equal(0, module.references().get(0).index());
            equal("retained", module.definition().changedItem().getTag().getString("rule"));
        } finally {
            buf.release();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void writesDefinitionsAtomicallyAndDeletesSavedFiles(GameTestHelper helper) throws Exception {
        var data = FPSMCore.getInstance().getFPSMDataManager();
        var module = definition("changeItem_disk_test_" + UUID.randomUUID().toString().replace("-", ""), 300).create();
        var path = data.getSaveFolder(module).toPath().resolve(module.getName() + ".json");
        try {
            data.saveDataAtomic(module, module.getName());
            var json = JsonParser.parseString(Files.readString(path)).getAsJsonObject().get("data");
            var loaded = ChangeShopItemModule.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow();
            equal(module.getName(), loaded.getName());
            equal(300, loaded.changedCost());
            data.deleteData(ChangeShopItemModule.class, module.getName());
            equal(false, Files.exists(path));
        } finally {
            Files.deleteIfExists(path);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void definitionEditsInvalidateOpenSlotDrafts(GameTestHelper helper) {
        var manager = FPSMCore.getInstance().getListenerModuleManager();
        var module = definition("changeItem_revision_test", 300).create();
        var previous = manager.getListenerModule(module.getName());
        manager.addListenerType(module);
        try {
            var shop = shop(module);
            String before = ShopEditorService.revision(shop);
            manager.addListenerType(definition(module.getName(), 999).create());
            var draft = new ShopEditorSnapshot.Slot(new ItemStack(Items.APPLE), 50, 0, 7, 1, List.of(module.getName()));
            equal(ShopEditorResult.CONFLICT, ShopEditorService.applySlot(shop, before, "EQUIPMENT", 0, draft, manager.getRegistry()));
        } finally {
            if (previous == null) manager.getRegistry().remove(module.getName());
            else manager.addListenerType(previous);
        }
        helper.succeed();
    }
}
