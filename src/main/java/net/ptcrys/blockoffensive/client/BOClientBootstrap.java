package net.ptcrys.blockoffensive.client;

import net.ptcrys.blockoffensive.BlockOffensive;
import net.ptcrys.blockoffensive.client.key.DismantleBombKey;
import net.ptcrys.blockoffensive.client.key.MvpMusicKey;
import net.ptcrys.blockoffensive.client.key.OpenShopKey;
import net.ptcrys.blockoffensive.client.key.RadioKey;
import net.ptcrys.blockoffensive.client.key.SwitchSpectatorKey;
import net.ptcrys.blockoffensive.client.renderer.C4Renderer;
import net.ptcrys.blockoffensive.client.screen.hud.*;
import net.ptcrys.blockoffensive.entity.BOEntityRegister;
import net.ptcrys.blockoffensive.intro.client.IntroClientController;
import net.ptcrys.fpsmatch.common.client.FPSMGameHudManager;
import net.ptcrys.fpsmatch.common.client.spec.SpecKeyHandler;
import net.ptcrys.fpsmatch.common.client.tab.TabManager;

import net.minecraft.Optionull;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

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
        event.register(net.ptcrys.blockoffensive.client.key.TeamChatKey.TEAM_CHAT_KEY);
        event.register(net.ptcrys.blockoffensive.client.key.VoteKey.VOTE_AGREE_KEY);
        event.register(net.ptcrys.blockoffensive.client.key.VoteKey.VOTE_DISAGREE_KEY);
        event.register(RadioKey.RADIO_TACTICAL_KEY);
        event.register(MvpMusicKey.KEY_MVP_MUSIC);
        SpecKeyHandler.registerSwitchKey(SwitchSpectatorKey.KEY_SPECTATE_NEXT);
        SpecKeyHandler.registerSwitchKey(SwitchSpectatorKey.KEY_SPECTATE_PREV);
        // cs: hud | overlay | tab
        TabManager.getInstance().registerRenderer(new CSGameTabRenderer());
        TabManager.getInstance().registerRenderer(new CSDMTabRenderer());

        FPSMGameHudManager.INSTANCE.registerHud("cs", CSGameHud.getInstance());
        FPSMGameHudManager.INSTANCE.registerHud("csdm", CSGameHud.getInstance());
    }

    @SubscribeEvent
    public static void onRegisterEntityRenderEvent(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(BOEntityRegister.C4.get(), new C4Renderer());
        event.registerEntityRenderer(BOEntityRegister.PING_MARKER.get(),
                net.ptcrys.blockoffensive.client.renderer.PingMarkerRenderer::new);
    }

    public static List<PlayerInfo> getPlayerInfos() {
        if (Minecraft.getInstance().player != null) {
            return Minecraft.getInstance().player.connection.getListedOnlinePlayers().stream().sorted(PLAYER_COMPARATOR).limit(80L).toList();
        }
        return new ArrayList<>();
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(IntroClientController::registerClient);
    }
}
