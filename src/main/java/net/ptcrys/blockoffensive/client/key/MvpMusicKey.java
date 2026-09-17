package net.ptcrys.blockoffensive.client.key;

import net.ptcrys.blockoffensive.client.screen.MvpMusicScreen;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import org.lwjgl.glfw.GLFW;

/**
 * MVP 本地音乐设置键（默认 F9）：打开音乐设置页。
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class MvpMusicKey {

    public static final KeyMapping KEY_MVP_MUSIC = new KeyMapping(
            "key.blockoffensive.mvp_music.desc", GLFW.GLFW_KEY_F9, "key.category.blockoffensive");

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) {
            return;
        }
        if (KEY_MVP_MUSIC.consumeClick()) {
            mc.setScreen(new MvpMusicScreen());
        }
    }
}
