package com.ptcrys.blockoffensive.client.key;

import com.mojang.blaze3d.platform.InputConstants;
import com.ptcrys.blockoffensive.BlockOffensive;
import com.ptcrys.blockoffensive.client.screen.hud.CSVoteHud;
import com.ptcrys.blockoffensive.net.vote.VoteCastC2SPacket;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * 投票按键：Y=同意 / N=反对。仅在有进行中的投票时生效，避免与其他操作冲突。
 * 与聊天命令 .a/.da 等价，二者可并存。
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(value = Dist.CLIENT)
public class VoteKey {
    public static final KeyMapping VOTE_AGREE_KEY = new KeyMapping("key.blockoffensive.vote_agree.desc",
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Y,
            "key.category.blockoffensive");

    public static final KeyMapping VOTE_DISAGREE_KEY = new KeyMapping("key.blockoffensive.vote_disagree.desc",
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            "key.category.blockoffensive");

    @SubscribeEvent
    public static void onVoteKeyPress(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!CSVoteHud.getInstance().isActive()) {
            // 排空按键，避免投票结束后残留触发
            while (VOTE_AGREE_KEY.consumeClick()) { /* drain */ }
            while (VOTE_DISAGREE_KEY.consumeClick()) { /* drain */ }
            return;
        }
        if (VOTE_AGREE_KEY.consumeClick()) {
            BlockOffensive.INSTANCE.sendToServer(new VoteCastC2SPacket(true));
        }
        if (VOTE_DISAGREE_KEY.consumeClick()) {
            BlockOffensive.INSTANCE.sendToServer(new VoteCastC2SPacket(false));
        }
    }
}
