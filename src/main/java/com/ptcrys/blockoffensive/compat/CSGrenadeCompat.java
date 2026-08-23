package com.ptcrys.blockoffensive.compat;

import club.pisquad.minecraft.csgrenades.entity.CounterStrikeGrenadeEntity;
import club.pisquad.minecraft.csgrenades.event.GrenadeThrowEvent;
import com.ptcrys.blockoffensive.client.BOClientEvent;
import com.ptcrys.blockoffensive.util.BOUtil;
import com.ptcrys.blockoffensive.util.ThrowableType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;

public class CSGrenadeCompat {
    private static final ResourceLocation HE_GRENADE = id("hegrenade");
    private static final ResourceLocation HE_GRENADE_T = id("hegrenade_t");
    private static final ResourceLocation INCENDIARY = id("incendiary");
    private static final ResourceLocation INCENDIARY_T = id("incendiary_t");
    private static final ResourceLocation MOLOTOV = id("molotov");
    private static final ResourceLocation MOLOTOV_T = id("molotov_t");
    private static final ResourceLocation SMOKE_GRENADE = id("smokegrenade");
    private static final ResourceLocation SMOKE_GRENADE_T = id("smokegrenade_t");
    private static final ResourceLocation FLASH_BANG = id("flashbang");
    private static final ResourceLocation FLASH_BANG_T = id("flashbang_t");
    private static final ResourceLocation DECOY = id("decoy");
    private static final ResourceLocation DECOY_T = id("decoy_t");

    public static void registerKillIcon(Map<ResourceLocation, String> registry){
        registerKillIcon(registry, HE_GRENADE, "grenade");
        registerKillIcon(registry, HE_GRENADE_T, "grenade");
        registerKillIcon(registry, INCENDIARY, "ct_incendiary_grenade");
        registerKillIcon(registry, INCENDIARY_T, "ct_incendiary_grenade");
        registerKillIcon(registry, MOLOTOV, "t_incendiary_grenade");
        registerKillIcon(registry, MOLOTOV_T, "t_incendiary_grenade");
        registerKillIcon(registry, SMOKE_GRENADE, "smoke_shell");
        registerKillIcon(registry, SMOKE_GRENADE_T, "smoke_shell");
        registerKillIcon(registry, FLASH_BANG, "flash_bomb");
        registerKillIcon(registry, FLASH_BANG_T, "flash_bomb");
    }

    public static void init() {
        MinecraftForge.EVENT_BUS.register(CSGrenadeCompat.class);

        registerThrowable(ThrowableType.FLASH_BANG, FLASH_BANG);
        registerThrowable(ThrowableType.FLASH_BANG, FLASH_BANG_T);
        registerThrowable(ThrowableType.DECOY, DECOY);
        registerThrowable(ThrowableType.DECOY, DECOY_T);
        registerThrowable(ThrowableType.GRENADE, HE_GRENADE);
        registerThrowable(ThrowableType.GRENADE, HE_GRENADE_T);
        registerThrowable(ThrowableType.SMOKE, SMOKE_GRENADE);
        registerThrowable(ThrowableType.SMOKE, SMOKE_GRENADE_T);
        registerThrowable(ThrowableType.INCENDIARY_GRENADE, MOLOTOV);
        registerThrowable(ThrowableType.INCENDIARY_GRENADE, MOLOTOV_T);
        registerThrowable(ThrowableType.INCENDIARY_GRENADE, INCENDIARY);
        registerThrowable(ThrowableType.INCENDIARY_GRENADE, INCENDIARY_T);
    }

    public static boolean is(Entity entity){
        return entity instanceof CounterStrikeGrenadeEntity;
    }

    @SubscribeEvent
    public static void onThrowGrenadeEvent(GrenadeThrowEvent event) {
        if(BOClientEvent.isLocked()){
            event.setCanceled(true);
        }else{
            BOUtil.buildGrenadeMessageAndSend(event.getItemStack());
        }
    }

    private static void registerKillIcon(Map<ResourceLocation, String> registry, ResourceLocation itemId, String iconId) {
        if (ForgeRegistries.ITEMS.containsKey(itemId)) {
            registry.put(itemId, iconId);
        }
    }

    private static void registerThrowable(ThrowableType type, ResourceLocation itemId) {
        Item item = ForgeRegistries.ITEMS.getValue(itemId);
        if (item != null) {
            BOUtil.registerThrowable(type, item);
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.tryBuild("csgrenades", path);
    }
}
