package net.ptcrys.blockoffensive.client.screen;

import net.ptcrys.blockoffensive.util.BOUtil;
import net.ptcrys.fpsmatch.common.client.FPSMClient;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import org.jetbrains.annotations.NotNull;

public class TeamChatScreen extends ChatScreen {

    public static final MutableComponent TITLE = Component.translatable("blockoffensive.team_chat.title");

    public TeamChatScreen() {
        super("");
    }

    @Override
    public void render(@NotNull GuiGraphics pGuiGraphics, int pMouseX, int pMouseY, float pPartialTick) {
        super.render(pGuiGraphics, pMouseX, pMouseY, pPartialTick);
        if (this.input.getValue().isEmpty()) {
            int x = 4;
            int y = this.height - 12;
            pGuiGraphics.drawString(minecraft.font, TITLE, x, y, BOUtil.getTeamColor(minecraft.player.getUUID()));
        }
    }

    @Override
    public boolean handleChatInput(String pInput, boolean pAddToRecentChat) {
        if (!pInput.isEmpty()) {
            if (pInput.startsWith("/")) {
                super.handleChatInput(pInput, pAddToRecentChat);
                return minecraft.screen == this;
            } else {
                if (pAddToRecentChat) {
                    this.minecraft.gui.getChat().addRecentChat(pInput);
                }
            }
            MutableComponent teamMessage = BOUtil.buildTeamChatMessage(Component.literal(pInput));
            FPSMClient.getGlobalData().getCurrentClientTeam().ifPresent(team -> team.sendMessage(teamMessage));
        }
        return minecraft.screen == this;
    }

    @Override
    public @NotNull Component getTitle() {
        return TITLE;
    }
}
