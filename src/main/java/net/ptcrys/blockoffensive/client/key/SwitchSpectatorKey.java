package net.ptcrys.blockoffensive.client.key;

import net.ptcrys.blockoffensive.net.spec.SwitchSpectateC2SPacket;
import net.ptcrys.blockoffensive.spectator.BOSpecManager;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class SwitchSpectatorKey {

    public static final KeyMapping KEY_SPECTATE_PREV = new KeyMapping(
            "key.blockoffensive.switch_spec_previous.desc", GLFW.GLFW_KEY_A, "key.category.blockoffensive.spec");
    public static final KeyMapping KEY_SPECTATE_NEXT = new KeyMapping(
            "key.blockoffensive.switch_spec_next.desc", GLFW.GLFW_KEY_D, "key.category.blockoffensive.spec");

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        if (mc.gameMode == null || mc.gameMode.getPlayerMode() != GameType.SPECTATOR) {
            return;
        }

        boolean pressedPrev = KEY_SPECTATE_PREV.consumeClick();
        boolean pressedNext = KEY_SPECTATE_NEXT.consumeClick();
        if (!pressedPrev && !pressedNext) {
            return;
        }

        if (mc.screen != null || !net.ptcrys.fpsmatch.common.client.camera.CameraDirector.policy().allowSpectatorSwitch()) return;
        Entity camera = mc.getCameraEntity();
        if (!(camera instanceof Player teammate) || teammate == player || !teammate.isAlive() || teammate.isSpectator()) {
            return;
        }

        BOSpecManager.sendSwitchSpectate(pressedPrev ? SwitchSpectateC2SPacket.SwitchDirection.PREV : SwitchSpectateC2SPacket.SwitchDirection.NEXT);
    }
}
