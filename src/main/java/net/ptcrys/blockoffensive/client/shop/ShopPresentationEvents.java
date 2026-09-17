package net.ptcrys.blockoffensive.client.shop;

import net.ptcrys.blockoffensive.client.screen.CSGameShopScreen;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "blockoffensive", value = Dist.CLIENT)
public final class ShopPresentationEvents {

    private ShopPresentationEvents() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void hideFirstPersonHands(RenderHandEvent event) {
        if (Minecraft.getInstance().screen instanceof CSGameShopScreen) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void hideCrosshair(RenderGuiOverlayEvent.Pre event) {
        if (Minecraft.getInstance().screen instanceof CSGameShopScreen && event.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.id())) event.setCanceled(true);
    }
}
