package net.ptcrys.blockoffensive.client.key;

import net.ptcrys.blockoffensive.BlockOffensive;
import net.ptcrys.blockoffensive.net.bomb.BombActionC2SPacket;
import net.ptcrys.fpsmatch.compat.gun.GunCompatManager;

import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(value = Dist.CLIENT)
public class DismantleBombKey {

    public static final KeyMapping DISMANTLE_BOMB_KEY = new KeyMapping("key.blockoffensive.dismantle_bomb.desc",
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_E,
            "key.category.blockoffensive");
    private static final DismantleInputEdge INPUT_EDGE = new DismantleInputEdge();

    @SubscribeEvent
    public static void onInspectPress(InputEvent.Key event) {
        INPUT_EDGE.accept(
                DISMANTLE_BOMB_KEY.matches(event.getKey(), event.getScanCode()),
                event.getAction(),
                GunCompatManager.isInGame()).ifPresent(action -> BlockOffensive.INSTANCE.sendToServer(new BombActionC2SPacket(action)));
    }
}
