package com.ptcrys.blockoffensive.client.key;

import com.ptcrys.blockoffensive.net.spec.SwitchSpectateC2SPacket;
import com.ptcrys.blockoffensive.spectator.BOSpecManager;
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

        // 仅当视角真正挂在某位存活队友身上时才切换目标。
        // 自由飞行（视角在自身）或 KillCam 拉镜阶段（视角在 ghost 实体）时，
        // A/D 完全交给原版观战飞行，避免"按 A/D 切人又把视角甩出去"的双重行为。
        Entity cam = mc.getCameraEntity();
        if (cam == null || cam == player || !(cam instanceof Player p) || !p.isAlive() || p.isSpectator()) {
            return;
        }

        BOSpecManager.sendSwitchSpectate(pressedPrev
                ? SwitchSpectateC2SPacket.SwitchDirection.PREV
                : SwitchSpectateC2SPacket.SwitchDirection.NEXT);
    }
}
