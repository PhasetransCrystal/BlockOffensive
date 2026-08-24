package com.ptcrys.blockoffensive.minimap;

import com.ptcrys.blockoffensive.BlockOffensive;
import com.ptcrys.blockoffensive.command.BOMinimapAcceptanceCommand;
import com.ptcrys.blockoffensive.event.CSGameMapEvent;
import com.ptcrys.fpsmatch.FPSMatch;
import com.ptcrys.fpsmatch.common.event.FPSMGunShootEvent;
import com.ptcrys.fpsmatch.common.event.FPSMapEvent;
import com.ptcrys.fpsmatch.core.FPSMCore;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Forge lifecycle bridge for the map-scoped BO minimap runtime. */
@Mod.EventBusSubscriber(modid = BlockOffensive.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BlockOffensiveMinimapRuntimeEvents {
    private BlockOffensiveMinimapRuntimeEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            BOMinimapAcceptanceCommand.tick();
            BlockOffensiveMinimapRuntime.tick();
        }
    }

    @SubscribeEvent
    public static void onGunShoot(FPSMGunShootEvent event) {
        if (event.getShooter() instanceof ServerPlayer shooter) {
            BlockOffensiveMinimapRuntime.noteFire(shooter);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && FPSMCore.initialized()) {
            FPSMCore.getInstance().getMapByPlayerWithSpec(player)
                    .ifPresent(map -> BlockOffensiveMinimapRuntime.targetDied(map, player.getUUID()));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            cleanup("player logout", () ->
                    BOMinimapAcceptanceCommand.cleanupPlayer(player.getUUID()));
            if (FPSMCore.initialized()) {
                FPSMCore.getInstance().getMapByPlayerWithSpec(player)
                        .ifPresent(map -> BlockOffensiveMinimapRuntime.playerLeft(
                                map, player.getUUID()));
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onMapLoggedOut(FPSMapEvent.PlayerEvent.LoggedOutEvent event) {
        cleanup("map player logout", () ->
                BOMinimapAcceptanceCommand.cleanupPlayer(event.getPlayer().getUUID()));
        BlockOffensiveMinimapRuntime.playerLeft(event.getMap(), event.getPlayer().getUUID());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onMapReset(FPSMapEvent.ResetEvent event) {
        cleanup("map reset", () -> BOMinimapAcceptanceCommand.cleanupMap(event.getMap()));
        BlockOffensiveMinimapRuntime.reset(event.getMap());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onMapClear(FPSMapEvent.ClearEvent event) {
        cleanup("map clear", () -> BOMinimapAcceptanceCommand.cleanupMap(event.getMap()));
        BlockOffensiveMinimapRuntime.reset(event.getMap());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onMapReload(FPSMapEvent.ReloadEvent event) {
        if (!event.isCanceled()) {
            cleanup("map reload", () -> BOMinimapAcceptanceCommand.cleanupMap(event.getMap()));
            BlockOffensiveMinimapRuntime.reset(event.getMap());
        }
    }

    @SubscribeEvent
    public static void onTeamSwitch(CSGameMapEvent.TeamSwitchEvent event) {
        cleanup("team switch", () -> BOMinimapAcceptanceCommand.cleanupMap(event.getMap()));
        BlockOffensiveMinimapRuntime.reset(event.getMap());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        cleanup("server stopping (best effort)",
                () -> BOMinimapAcceptanceCommand.cleanupAll());
        BlockOffensiveMinimapRuntime.clear();
    }

    private static void cleanup(String reason, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException failure) {
            FPSMatch.LOGGER.error("Acceptance minimap cleanup failed: {}", reason, failure);
        }
    }
}
