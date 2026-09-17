package net.ptcrys.blockoffensive.client.key;

import net.ptcrys.blockoffensive.client.screen.RadialMenuScreen;
import net.ptcrys.fpsmatch.compat.gun.GunCompatManager;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

/**
 * Z 键标记轮盘：
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(value = Dist.CLIENT)
public class RadioKey {

    public static final KeyMapping RADIO_TACTICAL_KEY = new KeyMapping("key.blockoffensive.radio_tactical.desc",
            KeyConflictContext.IN_GAME, KeyModifier.NONE, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z,
            "key.category.blockoffensive");

    @SubscribeEvent
    public static void onKeyPress(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS) return;
        if (!GunCompatManager.isInGame()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null) return;

        if (RADIO_TACTICAL_KEY.consumeClick()) {
            mc.setScreen(new RadialMenuScreen());
        }
    }
}
