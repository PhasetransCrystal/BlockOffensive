package com.ptcrys.blockoffensive.intro.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.ptcrys.blockoffensive.BlockOffensive;
import com.ptcrys.blockoffensive.intro.IntroPhase;
import com.ptcrys.blockoffensive.intro.IntroTeamSide;
import com.ptcrys.blockoffensive.intro.net.IntroSequenceS2CPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class IntroCinematicOverlay {
    private static final ResourceLocation CT_ICON = ResourceLocation.tryBuild(BlockOffensive.MODID, "textures/gui/intro/ct_icon.png");
    private static final ResourceLocation T_ICON = ResourceLocation.tryBuild(BlockOffensive.MODID, "textures/gui/intro/t_icon.png");

    private IntroCinematicOverlay() {
    }

    public static Result render(GuiGraphics graphics, int width, int height, IntroSequenceS2CPacket packet, int tick) {
        int duration = Math.max(1, packet.durationTicks);
        int movementStartTick = packet.movementStartTick();
        boolean movementPhase = packet.phase != IntroPhase.PREARM && tick >= movementStartTick;
        int movementTick = Math.max(0, tick - movementStartTick);
        float progress = Mth.clamp(movementTick / (float) duration, 0.0f, 1.0f);
        int blackAlpha = blackAlpha(packet.phase, progress, movementPhase);
        float titleAlpha = movementPhase ? titleAlpha(progress) : 0.0f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        graphics.pose().pushPose();
        try {
            if (blackAlpha > 0) {
                graphics.fill(0, 0, width, height, blackAlpha << 24);
            }
            if (titleAlpha > 0.01F) {
                drawHeader(graphics, width, height, packet, titleAlpha);
            }
        } finally {
            graphics.pose().popPose();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.disableBlend();
        }
        return new Result(titleAlpha > 0.01F, overlayKey(packet));
    }

    private static int blackAlpha(IntroPhase phase, float progress, boolean movementPhase) {
        if (phase == IntroPhase.PREARM || !movementPhase) {
            return 255;
        }
        boolean preview5 = phase == IntroPhase.PREVIEW5;
        float fadeInEnd = preview5 ? 0.10F : 0.18F;
        float fadeOutEnd = preview5 ? 0.12F : 0.16F;
        float maxAlpha = preview5 ? 140.0F : 180.0F;
        float alpha = Math.max(fadeAmount(progress, 0.0F, fadeInEnd), fadeAmount(1.0F - progress, 0.0F, fadeOutEnd));
        return Mth.clamp(Math.round(alpha * maxAlpha), 0, Math.round(maxAlpha));
    }

    private static float titleAlpha(float progress) {
        float in = Mth.clamp((progress - 0.04F) / 0.10F, 0.0F, 1.0F);
        float out = Mth.clamp((0.98F - progress) / 0.10F, 0.0F, 1.0F);
        return smoothstep(Math.min(in, out));
    }

    private static void drawHeader(GuiGraphics graphics, int width, int height, IntroSequenceS2CPacket packet, float alpha) {
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        int iconSize = Mth.clamp(height / 11, 24, 40);
        int top = Mth.clamp(height / 34, 6, 16);
        int centerX = width / 2;
        int color = withAlpha(0xFFFFFF, Math.round(alpha * 255.0F));
        int shadowColor = withAlpha(0x141414, Math.round(alpha * 220.0F));
        ResourceLocation icon = packet.side == IntroTeamSide.T ? T_ICON : CT_ICON;

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
        graphics.blit(icon, centerX - iconSize / 2, top, 0, 0, iconSize, iconSize, iconSize, iconSize);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        String title = net.minecraft.network.chat.Component.translatable(
                "blockoffensive.halftime.title").getString();
        String subtitle = net.minecraft.network.chat.Component.translatable(
                packet.side == IntroTeamSide.T
                        ? "blockoffensive.halftime.side.t"
                        : "blockoffensive.halftime.side.ct").getString();
        int titleY = top + iconSize + 4;
        drawCenteredScaled(graphics, font, title, centerX + 1, titleY + 1, 1.35F, shadowColor);
        drawCenteredScaled(graphics, font, title, centerX, titleY, 1.35F, color);
        int subtitleY = titleY + Math.round(font.lineHeight * 1.45F) + 2;
        drawCenteredScaled(graphics, font, subtitle, centerX + 1, subtitleY + 1, 0.82F, shadowColor);
        drawCenteredScaled(graphics, font, subtitle, centerX, subtitleY, 0.82F, color);
    }

    private static void drawCenteredScaled(GuiGraphics graphics, Font font, String text, int centerX, int y, float scale, int color) {
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(centerX, y, 0.0F);
            graphics.pose().scale(scale, scale, 1.0F);
            graphics.drawString(font, text, -font.width(text) / 2, 0, color, true);
        } finally {
            graphics.pose().popPose();
        }
    }

    private static int withAlpha(int rgb, int alpha) {
        return (Mth.clamp(alpha, 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    private static float fadeAmount(float progress, float start, float end) {
        if (progress <= start) {
            return 1.0F;
        }
        if (progress >= end) {
            return 0.0F;
        }
        return 1.0F - (progress - start) / Math.max(0.0001F, end - start);
    }

    private static float smoothstep(float value) {
        return value * value * (3.0F - 2.0F * value);
    }

    private static String overlayKey(IntroSequenceS2CPacket packet) {
        String half = packet.phase == IntroPhase.SWITCH ? "second_half" : "first_half";
        return half + ":" + packet.side.id();
    }

    public record Result(boolean titleDrawn, String overlayKey) {
    }
}
