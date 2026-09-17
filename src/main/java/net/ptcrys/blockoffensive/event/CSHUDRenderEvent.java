package net.ptcrys.blockoffensive.event;

import net.ptcrys.blockoffensive.client.screen.hud.CSMvpHud;
import net.ptcrys.blockoffensive.data.MvpReason;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.eventbus.api.Event;

public class CSHUDRenderEvent extends Event {

    public final GuiGraphics guiGraphics;
    public final int screenWidth;
    public final int screenHeight;

    public CSHUDRenderEvent(GuiGraphics guiGraphics, int screenWidth, int screenHeight) {
        this.guiGraphics = guiGraphics;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
    }

    public GuiGraphics getGuiGraphics() {
        return guiGraphics;
    }

    public int getScreenHeight() {
        return screenHeight;
    }

    public int getScreenWidth() {
        return screenWidth;
    }

    public static class RenderMvpHud extends CSHUDRenderEvent {

        private final CSMvpHud hud;

        public RenderMvpHud(GuiGraphics guiGraphics, int screenWidth, int screenHeight, CSMvpHud hud) {
            super(guiGraphics, screenWidth, screenHeight);
            this.hud = hud;
        }

        public CSMvpHud getHud() {
            return hud;
        }

        public static class Pre extends RenderMvpHud {

            public Pre(GuiGraphics guiGraphics, int screenWidth, int screenHeight, CSMvpHud hud) {
                super(guiGraphics, screenWidth, screenHeight, hud);
            }
        }

        public static class Post extends RenderMvpHud {

            public Post(GuiGraphics guiGraphics, int screenWidth, int screenHeight, CSMvpHud hud) {
                super(guiGraphics, screenWidth, screenHeight, hud);
            }
        }

        public static class TriggeredAnimation extends Event {

            public final MvpReason reason;

            public TriggeredAnimation(MvpReason reason) {
                this.reason = reason;
            }

            public MvpReason getReason() {
                return reason;
            }
        }
    }
}
