package net.ptcrys.blockoffensive.item;

import net.ptcrys.blockoffensive.BlockOffensive;
import net.ptcrys.blockoffensive.item.test.TestItem;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class BOItemRegister {

    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BlockOffensive.MODID);
    public static RegistryObject<CreativeModeTab> BO_TAB;

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, BlockOffensive.MODID);
    public static final RegistryObject<Item> C4 = ITEMS.register("c4", () -> new CompositionC4(new Item.Properties()));
    public static final RegistryObject<Item> OPEN_TEST_SHOP = ITEMS.register("open_test_shop", () -> new TestItem(new Item.Properties()));
    public static final RegistryObject<BombDisposalKit> BOMB_DISPOSAL_KIT = ITEMS.register("bomb_disposal_kit",
            () -> new BombDisposalKit(new Item.Properties()));

    static {
        BO_TAB = TABS.register("other", () -> CreativeModeTab.builder().title(Component.translatable("itemGroup.tab.blockoffensive"))
                .icon(() -> C4.get().getDefaultInstance()).displayItems((parameters, output) -> {
                    ITEMS.getEntries().forEach((entry) -> {
                        output.accept(entry.get());
                    });
                }).build());
    }
}
