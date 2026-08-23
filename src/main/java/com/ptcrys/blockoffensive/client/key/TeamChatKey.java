package com.ptcrys.blockoffensive.client.key;

import com.mojang.blaze3d.platform.InputConstants;
import com.ptcrys.blockoffensive.client.screen.TeamChatScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(value = Dist.CLIENT)
public class TeamChatKey {
    public static final KeyMapping TEAM_CHAT_KEY = new KeyMapping("key.blockoffensive.team_chat.desc",
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_U,
            "key.category.blockoffensive");

    @SubscribeEvent
    public static void onTeamChatPress(TickEvent.ClientTickEvent event) {
        if(event.phase == TickEvent.Phase.END && TEAM_CHAT_KEY.isDown()) {
            Minecraft.getInstance().setScreen(new TeamChatScreen());
        }
    }
}