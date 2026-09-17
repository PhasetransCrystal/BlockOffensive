package net.ptcrys.blockoffensive.client.screen;

import net.ptcrys.blockoffensive.BlockOffensive;
import net.ptcrys.blockoffensive.client.screen.hud.CSGameTabRenderer;
import net.ptcrys.fpsmatch.common.client.FPSMClient;
import net.ptcrys.fpsmatch.common.client.FPSMGameHudManager;
import net.ptcrys.fpsmatch.common.client.screen.TabScreen;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

/** Mouse interaction for the held CS scoreboard; Screen owns cursor release and restoration. */
@Mod.EventBusSubscriber(modid = BlockOffensive.MODID, value = Dist.CLIENT)
public final class CSGameTabScreen extends TabScreen {

    private final InputConstants.Key heldKey;

    private CSGameTabScreen(InputConstants.Key heldKey) {
        super(new CSGameTabRenderer(), true);
        this.heldKey = heldKey;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_RIGHT || event.getAction() != GLFW.GLFW_PRESS || mc.screen != null || mc.player == null || mc.level == null || !mc.options.keyPlayerList.isDown() || !FPSMGameHudManager.shouldRender() || !FPSMClient.getGlobalData().isCurrentGameType("cs")) return;

        // Consume the opening click before it can aim, use an item, or interact with the world.
        event.setCanceled(true);
        mc.setScreen(new CSGameTabScreen(mc.options.keyPlayerList.getKey()));
    }

    @Override
    public void tick() {
        super.tick();
        if (minecraft == null) return;
        if (minecraft.player == null || minecraft.level == null || !minecraft.isWindowActive() || !FPSMGameHudManager.shouldRender() || !FPSMClient.getGlobalData().isCurrentGameType("cs") || !isHeld()) {
            onClose();
        }
    }

    private boolean isHeld() {
        // Opening a Screen clears KeyMapping state. Query the physical binding instead.
        long window = minecraft.getWindow().getWindow();
        if (heldKey.getType() == InputConstants.Type.KEYSYM) {
            return InputConstants.isKeyDown(window, heldKey.getValue());
        }
        if (heldKey.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, heldKey.getValue()) == GLFW.GLFW_PRESS;
        }
        // Scan-code bindings are closed by keyReleased; GLFW cannot poll them directly.
        return true;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        boolean released = heldKey.getType() == InputConstants.Type.SCANCODE ? heldKey.getValue() == scanCode : heldKey.equals(InputConstants.getKey(keyCode, scanCode));
        if (released) {
            onClose();
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (heldKey.getType() == InputConstants.Type.MOUSE && heldKey.getValue() == button) {
            onClose();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
}
