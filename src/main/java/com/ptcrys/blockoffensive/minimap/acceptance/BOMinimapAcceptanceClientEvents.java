package com.ptcrys.blockoffensive.minimap.acceptance;

import com.ptcrys.blockoffensive.BlockOffensive;
import com.ptcrys.blockoffensive.net.acceptance.BOMinimapAcceptanceSceneSignal;
import com.ptcrys.fpsmatch.common.client.event.FPSMClientResetEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

import java.util.Objects;

/** Owns connection generation capture and client acceptance lifecycle events. */
@Mod.EventBusSubscriber(
        modid = BlockOffensive.MODID,
        bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT
)
public final class BOMinimapAcceptanceClientEvents {
    private BOMinimapAcceptanceClientEvents() {
    }

    public static void enqueue(
            BOMinimapAcceptanceSceneSignal signal,
            NetworkEvent.Context context
    ) {
        // Packets stay registered for discriminator stability, so gate before client state access.
        BOMinimapAcceptanceEnvironment.runIfAvailable(
                FMLEnvironment.production,
                () -> enqueueDevelopment(signal, context)
        );
    }

    private static void enqueueDevelopment(
            BOMinimapAcceptanceSceneSignal signal,
            NetworkEvent.Context context
    ) {
        Objects.requireNonNull(signal, "signal");
        Objects.requireNonNull(context, "context");
        long capturedGeneration =
                BOMinimapAcceptanceClientProjector.connectionGeneration();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> BOMinimapAcceptanceClientProjector.accept(
                        signal, capturedGeneration
                )
        ));
    }

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        BOMinimapAcceptanceClientProjector.resetConnection();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        BOMinimapAcceptanceClientProjector.resetConnection();
    }

    @SubscribeEvent
    public static void onReset(FPSMClientResetEvent event) {
        BOMinimapAcceptanceClientProjector.resetConnection();
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            BOMinimapAcceptanceClientProjector.tick();
        }
    }

    @SubscribeEvent
    public static void onScreenClosing(ScreenEvent.Closing event) {
        Screen screen = event.getScreen();
        BOMinimapAcceptanceClientProjector.manualClose(screen);
    }
}
