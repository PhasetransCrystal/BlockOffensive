package com.ptcrys.blockoffensive.client;

import com.ptcrys.blockoffensive.client.key.SwitchSpectatorKey;
import com.ptcrys.blockoffensive.client.renderer.C4Renderer;
import com.ptcrys.blockoffensive.client.screen.hud.*;
import com.ptcrys.blockoffensive.minimap.CSHudSafeAreaContributors;
import com.ptcrys.blockoffensive.minimap.CSHudSafeAreaLayouts;
import com.ptcrys.fpsmatch.common.client.minimap.hud.HudRenderContext;
import com.ptcrys.fpsmatch.core.minimap.hud.HudSafeAreaRegistry;
import com.ptcrys.blockoffensive.entity.BOEntityRegister;
import com.ptcrys.fpsmatch.common.client.FPSMGameHudManager;
import com.ptcrys.fpsmatch.common.client.spec.SpecKeyHandler;
import com.ptcrys.fpsmatch.common.client.tab.TabManager;
import com.ptcrys.blockoffensive.BlockOffensive;
import com.ptcrys.blockoffensive.client.key.DismantleBombKey;
import com.ptcrys.blockoffensive.client.key.OpenShopKey;
import net.minecraft.Optionull;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT, modid = BlockOffensive.MODID)
public class BOClientBootstrap {
    public static final Comparator<PlayerInfo> PLAYER_COMPARATOR = Comparator.<PlayerInfo>comparingInt((playerInfo) -> 0)
            .thenComparing((playerInfo) -> Optionull.mapOrDefault(playerInfo.getTeam(), PlayerTeam::getName, ""))
            .thenComparing((playerInfo) -> playerInfo.getProfile().getName(), String::compareToIgnoreCase);
    
    @SubscribeEvent
    public static void onClientSetup(RegisterKeyMappingsEvent event) {
        // 注册键位
        event.register(OpenShopKey.OPEN_SHOP_KEY);
        event.register(DismantleBombKey.DISMANTLE_BOMB_KEY);
        event.register(SwitchSpectatorKey.KEY_SPECTATE_NEXT);
        event.register(SwitchSpectatorKey.KEY_SPECTATE_PREV);
        event.register(com.ptcrys.blockoffensive.client.key.TeamChatKey.TEAM_CHAT_KEY);
        event.register(com.ptcrys.blockoffensive.client.key.VoteKey.VOTE_AGREE_KEY);
        event.register(com.ptcrys.blockoffensive.client.key.VoteKey.VOTE_DISAGREE_KEY);
        SpecKeyHandler.registerSwitchKey(SwitchSpectatorKey.KEY_SPECTATE_NEXT);
        SpecKeyHandler.registerSwitchKey(SwitchSpectatorKey.KEY_SPECTATE_PREV);
        // cs: hud | overlay | tab
        TabManager.getInstance().registerRenderer(new CSGameTabRenderer());
        TabManager.getInstance().registerRenderer(new CSDMTabRenderer());

        FPSMGameHudManager.INSTANCE.registerHud("cs", CSGameHud.getInstance());
        FPSMGameHudManager.INSTANCE.registerHud("csdm", CSGameHud.getInstance());
        registerSafeAreaContributors();
    }

    private static void registerSafeAreaContributors() {
        Minecraft mc = Minecraft.getInstance();
        CSHudSafeAreaContributors contributors = new CSHudSafeAreaContributors(
                CSGameHud.getInstance()::currentFrameGeometry,
                new CSHudSafeAreaContributors.RosterSource(
                        () -> CSSpectatorRoster.getInstance().isRendering(),
                        () -> mc.getWindow().getGuiScaledWidth(),
                        () -> CSSpectatorRoster.getInstance().visibleRowCount()
                ),
                new CSHudSafeAreaContributors.KillFeedSource(
                        () -> CSGameHud.getInstance().deathMessageHud().isRendering(),
                        () -> mc.getWindow().getGuiScaledWidth(),
                        () -> mc.getWindow().getGuiScaledHeight(),
                        () -> CSGameHud.getInstance().deathMessageHud().configuredPosition(),
                        () -> CSGameHud.getInstance().deathMessageHud().visibleMessageCount(),
                        () -> CSGameHud.getInstance().deathMessageHud().maxVisibleMessageWidth()
                ),
                new CSHudSafeAreaContributors.SpectatorCardSource(
                        CSSpectatorHudOverlay::isOccupyingScreen,
                        () -> mc.getWindow().getGuiScaledWidth(),
                        () -> mc.getWindow().getGuiScaledHeight(),
                        CSSpectatorHudOverlay::currentSlideYPixels
                )
        );

        FPSMGameHudManager.INSTANCE.registerSafeAreaContributor(
                "blockoffensive:hud_safe_areas",
                CSHudSafeAreaLayouts.PRIORITY,
                (HudSafeAreaRegistry registry, HudRenderContext ctx) -> {
                    if (!ctx.globalEnabled() || (!"cs".equals(ctx.gameType()) && !"csdm".equals(ctx.gameType()))) {
                        return;
                    }
                    CSHudSafeAreaLayouts.HudGeometry frame = CSGameHud.getInstance().currentFrameGeometry();
                    if (frame == null
                            || frame.screenWidth() != mc.getWindow().getGuiScaledWidth()
                            || frame.screenHeight() != mc.getWindow().getGuiScaledHeight()
                            || frame.spectator() != ctx.spectator()
                            || (frame.gameType() == CSHudSafeAreaLayouts.GameType.CS && !"cs".equals(ctx.gameType()))
                            || (frame.gameType() == CSHudSafeAreaLayouts.GameType.CSDM && !"csdm".equals(ctx.gameType()))) {
                        return;
                    }
                    contributors.contributeAll(registry, ctx.spectator());
                }
        );
    }

    @SubscribeEvent
    public static void onRegisterEntityRenderEvent(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(BOEntityRegister.C4.get(), new C4Renderer());
    }

    public static List<PlayerInfo> getPlayerInfos() {
        if (Minecraft.getInstance().player != null) {
            return Minecraft.getInstance().player.connection.getListedOnlinePlayers().stream().sorted(PLAYER_COMPARATOR).limit(80L).toList();
        }
        return new ArrayList<>();
    }
}
