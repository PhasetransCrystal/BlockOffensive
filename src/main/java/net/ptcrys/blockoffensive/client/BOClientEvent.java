package net.ptcrys.blockoffensive.client;

import net.ptcrys.blockoffensive.BOConfig;
import net.ptcrys.blockoffensive.BlockOffensive;
import net.ptcrys.blockoffensive.client.data.CSClientData;
import net.ptcrys.blockoffensive.client.screen.hud.CSGameHud;
import net.ptcrys.blockoffensive.compat.BOImpl;
import net.ptcrys.blockoffensive.net.dm.PlayerMoveC2SPacket;
import net.ptcrys.blockoffensive.util.BOUtil;
import net.ptcrys.blockoffensive.web.BOClientWebServer;
import net.ptcrys.fpsmatch.FPSMatch;
import net.ptcrys.fpsmatch.common.client.FPSMClient;
import net.ptcrys.fpsmatch.common.client.data.FPSMClientGlobalData;
import net.ptcrys.fpsmatch.common.client.event.FPSMClientResetEvent;
import net.ptcrys.fpsmatch.common.event.FPSMThrowGrenadeEvent;
import net.ptcrys.fpsmatch.common.event.RequestSpectatorOutlinesEvent;
import net.ptcrys.fpsmatch.compat.CounterStrikeGrenadesCompat;
import net.ptcrys.fpsmatch.compat.LrtacticalCompat;
import net.ptcrys.fpsmatch.compat.gun.GunCompatManager;
import net.ptcrys.fpsmatch.compat.impl.FPSMImpl;
import net.ptcrys.fpsmatch.core.item.IThrowEntityAble;
import net.ptcrys.fpsmatch.core.team.ClientTeam;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod.EventBusSubscriber(modid = BlockOffensive.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class BOClientEvent {

    @SubscribeEvent
    public static void onRenderGuiOverlayPost(RenderGuiOverlayEvent.Post event) {
        if (FMLEnvironment.production) return;
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;
        if (FPSMClient.getGlobalData().isInMap()) return;
        CSGameHud.getInstance().getDeathMessageHud().render(event.getGuiGraphics());
    }

    @SubscribeEvent
    public static void onClientTickEvent(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        FPSMClientGlobalData data = FPSMClient.getGlobalData();
        if (event.phase == TickEvent.Phase.END && mc.player != null && mc.level != null && mc.getConnection() != null && CSClientData.isStart && (!data.isInMap() || !data.isInGame())) {
            FPSMatch.pullGameInfo();
        }

        lockMove(mc);

        if (BOConfig.common.webServerEnabled.get()) {
            if (!(!data.isInMap() || !data.isInGame()) && data.isSpectator()) {
                BOClientWebServer.start();
            } else {
                BOClientWebServer.stop();
            }
        }
        // HTTP 工作线程只读主线程构建的快照，避免并发读游戏状态
        if (BOClientWebServer.isRunning()) {
            BOClientWebServer.refreshSnapshot();
        }
    }

    @SubscribeEvent
    public static void onRequestSpectatorOutlinesEvent(RequestSpectatorOutlinesEvent event) {
        event.setCanceled(FPSMClient.getGlobalData().getCurrentClientTeam().map(ClientTeam::isNormal).orElse(false));
    }

    @SubscribeEvent
    public static void onPlayerMoveInput(MovementInputUpdateEvent event) {
        if (Minecraft.getInstance().player == null) return;
        Input input = event.getInput();
        if (!FPSMClient.getGlobalData().isCurrentGameType("csdm")) return;

        if (input.left || input.right || input.up || input.down || input.shiftKeyDown) {
            FPSMatch.sendToServer(new PlayerMoveC2SPacket());
        }
    }

    @SubscribeEvent
    public static void onFPSMatchThrowGrenade(FPSMThrowGrenadeEvent event) {
        BOUtil.buildGrenadeMessageAndSend(event.getItemStack());
    }

    public static void lockMove(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null) return;
        if (isLocked()) {
            mc.options.keyUp.setDown(false);
            mc.options.keyLeft.setDown(false);
            mc.options.keyDown.setDown(false);
            mc.options.keyRight.setDown(false);
            mc.options.keyJump.setDown(false);
        }
    }

    public static boolean isLocked() {
        return (CSClientData.isWaiting || CSClientData.isPause) && (FPSMClient.getGlobalData().isCurrentGameType("cs") && !FPSMClient.getGlobalData().isSpectator());
    }

    @SubscribeEvent
    public static void onFPSMClientReset(FPSMClientResetEvent event) {
        CSClientData.reset();
    }

    @SubscribeEvent
    public static void onInput(InputEvent.MouseButton.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null) return;
        if (isLocked() && GunCompatManager.isInGame()) {
            if (checkLocalPlayerHand() && shouldCancelLockedCombatInput(minecraft, event.getButton())) {
                if (event.getAction() == 1) {
                    event.setCanceled(true);
                }
            }
        }
    }

    private static boolean shouldCancelLockedCombatInput(Minecraft minecraft, int button) {
        return minecraft.options.keyAttack.matchesMouse(button) || minecraft.options.keyUse.matchesMouse(button);
    }

    public static boolean checkLocalPlayerHand() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            return itemCheck(player) || (FPSMImpl.findLrtacticalMod() && LrtacticalCompat.itemCheck(player) || (BOImpl.isCounterStrikeGrenadesLoaded() && CounterStrikeGrenadesCompat.itemCheck(player)));
        }
        return false;
    }

    private static boolean itemCheck(Player player) {
        Item main = player.getMainHandItem().getItem();
        Item off = player.getOffhandItem().getItem();
        return (GunCompatManager.isGun(new ItemStack(main)) || main instanceof IThrowEntityAble) || (GunCompatManager.isGun(new ItemStack(off)) || off instanceof IThrowEntityAble);
    }
}
