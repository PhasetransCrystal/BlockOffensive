package net.ptcrys.blockoffensive.map;

import net.ptcrys.blockoffensive.BlockOffensive;
import net.ptcrys.blockoffensive.data.persistence.CSGameMapFixer;
import net.ptcrys.blockoffensive.map.shop.ItemType;
import net.ptcrys.fpsmatch.common.event.register.RegisterFPSMSaveDataEvent;
import net.ptcrys.fpsmatch.common.event.register.RegisterFPSMapEvent;
import net.ptcrys.fpsmatch.core.persistence.SaveHolder;
import net.ptcrys.fpsmatch.core.persistence.datafixer.DataFixer;
import net.ptcrys.fpsmatch.core.shop.FPSMShop;

import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = BlockOffensive.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MapRegister {

    @SubscribeEvent
    public static void onMapRegister(RegisterFPSMapEvent event) {
        FPSMShop.registerShopType("cs", ItemType.class);
        FPSMShop.registerShopType("csdm", ItemType.class);

        event.registerGameType("cs", CSGameMap::new);
        event.registerGameType("csdm", CSDeathMatchMap::new);
    }

    @SubscribeEvent
    public static void onDataRegister(RegisterFPSMSaveDataEvent event) {
        DataFixer.getInstance().registerJsonFixer(CSGameMap.class, 0, new CSGameMapFixer.OldV0());

        event.registerData(CSGameMap.class, "CSGameMaps",
                new SaveHolder.Builder<>(CSGameMap.CODEC)
                        .withLoadHandler(CSGameMap::load)
                        .withSaveHandler(CSGameMap::save)
                        .build());

        event.registerData(CSDeathMatchMap.class, "CSDeathMatchMaps",
                new SaveHolder.Builder<>(CSDeathMatchMap.CODEC)
                        .withLoadHandler(CSDeathMatchMap::load)
                        .withSaveHandler(CSDeathMatchMap::save)
                        .build());
    }
}
