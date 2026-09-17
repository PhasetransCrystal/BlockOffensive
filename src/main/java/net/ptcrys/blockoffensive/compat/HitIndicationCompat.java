package net.ptcrys.blockoffensive.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import com.rosymaple.hitindication.config.HitIndicatorClientConfigs;
import com.rosymaple.hitindication.latesthits.*;
import org.joml.Vector3d;

@SuppressWarnings("all")
public class HitIndicationCompat {

    public static class Renderer {

        private static final ResourceLocation INDICATOR = ResourceLocation.tryBuild("hitindication", "textures/hit/indicator.png");
        private static final ResourceLocation EDGE_INDICATOR = ResourceLocation.tryBuild("hitindication", "textures/hit/edge_indicator.png");
        private static final ResourceLocation INDICATOR_BLOCK = ResourceLocation.tryBuild("hitindication", "textures/hit/indicator_block.png");
        private static final ResourceLocation ND_INDICATOR = ResourceLocation.tryBuild("hitindication", "textures/hit/nd_person_damage.png");
        private static final ResourceLocation[] MARKER_CRIT = new ResourceLocation[] { ResourceLocation.tryBuild("hitindication", "textures/hit/marker_crit1.png"), ResourceLocation.tryBuild("hitindication", "textures/hit/marker_crit2.png"), ResourceLocation.tryBuild("hitindication", "textures/hit/marker_crit3.png"), ResourceLocation.tryBuild("hitindication", "textures/hit/marker_crit4.png") };
        private static final ResourceLocation[] MARKER_KILL = new ResourceLocation[] { ResourceLocation.tryBuild("hitindication", "textures/hit/marker_kill1.png"), ResourceLocation.tryBuild("hitindication", "textures/hit/marker_kill2.png"), ResourceLocation.tryBuild("hitindication", "textures/hit/marker_kill3.png"), ResourceLocation.tryBuild("hitindication", "textures/hit/marker_kill4.png") };
        private static String lastHitColorString = "FF0000";
        private static String lastBlockColorString = "0000FF";
        private static float hitColorR = 1.0F;
        private static float hitColorG = 0.0F;
        private static float hitColorB = 0.0F;
        private static float blockColorR = 0.0F;
        private static float blockColorG = 0.0F;
        private static float blockColorB = 1.0F;

        public static void render(Window window, GuiGraphics guiGraphics) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                int screenMiddleX = window.getGuiScaledWidth() / 2;
                int screenMiddleY = window.getGuiScaledHeight() / 2;
                updateColorsIfNeeded();
                Vec3 viewVector = calculateViewVector(0.0F, mc.player.getYRot());
                Vec2 lookVec = new Vec2((float) viewVector.x, (float) viewVector.z);
                Vec2 playerPos = new Vec2((float) mc.player.getX(), (float) mc.player.getZ());
                if (HitIndicatorClientConfigs.EdgeOfScreenMode.get()) {
                    for (HitIndicator hit : ClientLatestHits.latestHitIndicators) {
                        drawIndicatorEdge(guiGraphics, hit, screenMiddleX, screenMiddleY, playerPos, lookVec);
                    }
                } else {
                    for (HitIndicator hit : ClientLatestHits.latestHitIndicators) {
                        drawIndicator(guiGraphics, hit, screenMiddleX, screenMiddleY, playerPos, lookVec);
                    }
                }

                if (ClientLatestHits.currentHitMarker != null) {
                    drawHitMarker(guiGraphics, ClientLatestHits.currentHitMarker, screenMiddleX, screenMiddleY);
                }

            }
        }

        private static void updateColorsIfNeeded() {
            String currentHitColor = HitIndicatorClientConfigs.HitIndicatorColor.get();
            String currentBlockColor = HitIndicatorClientConfigs.BlockIndicatorColor.get();
            if (!lastHitColorString.equals(currentHitColor)) {
                try {
                    int parsedValue = Integer.parseInt(currentHitColor, 16);
                    hitColorR = (float) (parsedValue >> 16 & 255) / 255.0F;
                    hitColorG = (float) (parsedValue >> 8 & 255) / 255.0F;
                    hitColorB = (float) (parsedValue & 255) / 255.0F;
                } catch (Exception var4) {
                    hitColorR = 1.0F;
                    hitColorG = 0.0F;
                    hitColorB = 0.0F;
                }

                lastHitColorString = currentHitColor;
            }

            if (!lastBlockColorString.equals(currentBlockColor)) {
                try {
                    int parsedValue = Integer.parseInt(currentBlockColor, 16);
                    blockColorR = (float) (parsedValue >> 16 & 255) / 255.0F;
                    blockColorG = (float) (parsedValue >> 8 & 255) / 255.0F;
                    blockColorB = (float) (parsedValue & 255) / 255.0F;
                } catch (Exception var3) {
                    blockColorR = 0.0F;
                    blockColorG = 0.0F;
                    blockColorB = 1.0F;
                }

                lastBlockColorString = currentBlockColor;
            }
        }

        private static void drawHitMarker(GuiGraphics gui, HitMarker hitMarker, int screenMiddleX, int screenMiddleY) {
            float opacity = hitMarker.getType() == HitMarkerType.CRIT ? 30.0F : 60.0F;
            opacity /= 100.0F;
            ResourceLocation atlasLocation = getMarkerTexture(hitMarker.getType(), hitMarker.getLifeTime());
            float defaultScale = 1.0F;
            int scaledTextureWidth = (int) Math.floor((double) (20.0F * defaultScale));
            int scaledTextureHeight = (int) Math.floor((double) (20.0F * defaultScale));
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, opacity);
            gui.blit(atlasLocation, screenMiddleX - scaledTextureWidth / 2, screenMiddleY - scaledTextureHeight / 2, 0.0F, 0.0F, scaledTextureWidth, scaledTextureHeight, scaledTextureWidth, scaledTextureHeight);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }

        private static void drawIndicator(GuiGraphics gui, HitIndicator hit, int screenMiddleX, int screenMiddleY, Vec2 playerPos, Vec2 lookVec) {
            Vector3d sourceVec3d = hit.getLocation();
            Vec2 diff = new Vec2((float) (sourceVec3d.x - (double) playerPos.x), (float) (sourceVec3d.z - (double) playerPos.y));
            double angleBetween = angleBetween(lookVec, diff);
            int distanceFromCrosshair = (Integer) HitIndicatorClientConfigs.DistanceFromCrosshair.get();
            float defaultScale = 1.0F + (float) (Integer) HitIndicatorClientConfigs.IndicatorDefaultScale.get() / 100.0F;
            int scaledTextureWidth = hit.getType() != HitIndicatorType.ND_HIT ? (int) Math.floor((double) (42.0F * defaultScale)) : (int) Math.floor((double) 82.5F);
            int scaledTextureHeight = hit.getType() != HitIndicatorType.ND_HIT ? (int) Math.floor((double) (13.0F * defaultScale)) : (int) Math.floor((double) 82.5F);
            if (hit.getType() != HitIndicatorType.ND_HIT) {
                if ((Boolean) HitIndicatorClientConfigs.SizeDependsOnDamage.get()) {
                    float scale = Mth.clamp(hit.getDamagePercent() > 30 ? 1.0F + (float) hit.getDamagePercent() / 125.0F : 1.0F, 0.0F, 3.0F);
                    scaledTextureWidth = (int) Math.floor((double) ((float) scaledTextureWidth * scale));
                    scaledTextureHeight = (int) Math.floor((double) ((float) scaledTextureHeight * scale));
                }

                if ((Boolean) HitIndicatorClientConfigs.EnableDistanceScaling.get()) {
                    float distanceFromPlayer = calculateDistanceFromPlayer(hit.getLocation());
                    float distanceScalingCutoff = (float) (Integer) HitIndicatorClientConfigs.DistanceScalingCutoff.get();
                    float distanceScaling = 1.0F - (distanceFromPlayer <= distanceScalingCutoff ? 0.0F : (distanceFromPlayer - distanceScalingCutoff) / 10.0F);
                    if (distanceScaling > 1.0F) {
                        distanceScaling = 1.0F;
                    }

                    if (distanceScaling < 0.0F) {
                        distanceScaling = 0.0F;
                    }

                    scaledTextureWidth = (int) Math.floor((double) ((float) scaledTextureWidth * distanceScaling));
                    scaledTextureHeight = (int) Math.floor((double) ((float) scaledTextureHeight * distanceScaling));
                }
            }

            RenderSystem.enableBlend();
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            ResourceLocation atlasLocation = getTextureAndSetColor(hit);
            gui.pose().pushPose();
            gui.pose().translate((float) screenMiddleX, (float) screenMiddleY, 0.0F);
            if (hit.getType() != HitIndicatorType.ND_HIT) {
                gui.pose().mulPose(Axis.ZP.rotationDegrees((float) angleBetween));
            }

            gui.pose().translate((float) (-screenMiddleX), (float) (-screenMiddleY), 0.0F);
            gui.blit(atlasLocation, screenMiddleX - scaledTextureWidth / 2, screenMiddleY - scaledTextureHeight / 2 - (hit.getType() == HitIndicatorType.ND_HIT ? 0 : distanceFromCrosshair), 0.0F, 0.0F, scaledTextureWidth, scaledTextureHeight, scaledTextureWidth, scaledTextureHeight);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            gui.pose().popPose();
            RenderSystem.disableBlend();
        }

        private static void drawIndicatorEdge(GuiGraphics gui, HitIndicator hit, int screenMiddleX, int screenMiddleY, Vec2 playerPos, Vec2 lookVec) {
            if (hit.getType() != HitIndicatorType.ND_HIT || !(Boolean) HitIndicatorClientConfigs.EdgeOfScreenMode.get()) {
                Vector3d sourceVec3d = hit.getLocation();
                Vec2 diff = new Vec2((float) (sourceVec3d.x - (double) playerPos.x), (float) (sourceVec3d.z - (double) playerPos.y));
                double angleBetween = angleBetween(lookVec, diff);
                float defaultScale = 1.0F + (float) (Integer) HitIndicatorClientConfigs.IndicatorDefaultScale.get() / 100.0F;
                int scaledTextureWidth = (int) Math.floor((double) (16.0F * defaultScale));
                int scaledTextureHeight = (int) Math.floor((double) (16.0F * defaultScale));
                if ((Boolean) HitIndicatorClientConfigs.SizeDependsOnDamage.get()) {
                    float scale = Mth.clamp(hit.getDamagePercent() > 30 ? 1.0F + (float) hit.getDamagePercent() / 125.0F : 1.0F, 0.0F, 3.0F);
                    scaledTextureWidth = (int) Math.floor((double) ((float) scaledTextureWidth * scale));
                    scaledTextureHeight = (int) Math.floor((double) ((float) scaledTextureHeight * scale));
                }

                if ((Boolean) HitIndicatorClientConfigs.EnableDistanceScaling.get()) {
                    float distanceFromPlayer = calculateDistanceFromPlayer(hit.getLocation());
                    float distanceScalingCutoff = (float) (Integer) HitIndicatorClientConfigs.DistanceScalingCutoff.get();
                    float distanceScaling = 1.0F - (distanceFromPlayer <= distanceScalingCutoff ? 0.0F : (distanceFromPlayer - distanceScalingCutoff) / 10.0F);
                    if (distanceScaling > 1.0F) {
                        distanceScaling = 1.0F;
                    }

                    if (distanceScaling < 0.0F) {
                        distanceScaling = 0.0F;
                    }

                    scaledTextureWidth = (int) Math.floor((double) ((float) scaledTextureWidth * distanceScaling));
                    scaledTextureHeight = (int) Math.floor((double) ((float) scaledTextureHeight * distanceScaling));
                }

                double targetAngle = -angleBetween;
                int blitX;
                int blitY;
                if (targetAngle >= (double) 45.0F && targetAngle <= (double) 135.0F) {
                    blitX = 0;
                    blitY = (int) Mth.lerp((targetAngle - (double) 45.0F) / (double) 90.0F, (double) 0.0F, (double) (2 * screenMiddleY - scaledTextureHeight));
                } else if (targetAngle <= (double) -45.0F && targetAngle >= (double) -135.0F) {
                    blitX = 2 * screenMiddleX - scaledTextureWidth;
                    blitY = (int) Mth.lerp((targetAngle + (double) 45.0F) / (double) -90.0F, (double) 0.0F, (double) (2 * screenMiddleY - scaledTextureHeight));
                } else if (targetAngle >= (double) 0.0F && targetAngle <= (double) 45.0F) {
                    blitX = (int) Mth.lerp(targetAngle / (double) 45.0F, (double) screenMiddleX, (double) 0.0F);
                    blitY = 0;
                } else if (targetAngle >= (double) -45.0F && targetAngle <= (double) 0.0F) {
                    blitX = (int) Mth.lerp(targetAngle / (double) -45.0F, (double) screenMiddleX, (double) (2 * screenMiddleX - scaledTextureWidth));
                    blitY = 0;
                } else if (targetAngle <= (double) 180.0F && targetAngle >= (double) 135.0F) {
                    blitX = (int) Mth.lerp((targetAngle - (double) 135.0F) / (double) 45.0F, (double) 0.0F, (double) screenMiddleX);
                    blitY = 2 * screenMiddleY - scaledTextureHeight;
                } else {
                    blitX = (int) Mth.lerp((targetAngle + (double) 135.0F) / (double) -45.0F, (double) (2 * screenMiddleX - scaledTextureWidth), (double) screenMiddleX);
                    blitY = 2 * screenMiddleY - scaledTextureHeight;
                }

                RenderSystem.enableBlend();
                RenderSystem.setShader(GameRenderer::getPositionTexShader);
                ResourceLocation atlasLocation = getTextureAndSetColor(hit);
                gui.pose().pushPose();
                gui.pose().translate((float) blitX + (float) scaledTextureWidth / 2.0F, (float) blitY + (float) scaledTextureHeight / 2.0F, 0.0F);
                gui.pose().mulPose(Axis.ZP.rotationDegrees((float) angleBetween));
                gui.pose().translate((float) (-blitX) - (float) scaledTextureWidth / 2.0F, (float) (-blitY) - (float) scaledTextureHeight / 2.0F, 0.0F);
                gui.blit(atlasLocation, blitX, blitY, 0.0F, 0.0F, scaledTextureWidth, scaledTextureHeight, scaledTextureWidth, scaledTextureHeight);
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                gui.pose().popPose();
                RenderSystem.disableBlend();
            }
        }

        private static ResourceLocation getTextureAndSetColor(HitIndicator hit) {
            float opacity = hit.getLifeTime() >= 25 ? (float) (Integer) HitIndicatorClientConfigs.IndicatorOpacity.get() : (float) ((Integer) HitIndicatorClientConfigs.IndicatorOpacity.get() * hit.getLifeTime()) / 25.0F;
            opacity /= 100.0F;
            if (hit.getType() != HitIndicatorType.ND_HIT && hit.getType() != HitIndicatorType.HIT) {
                RenderSystem.setShaderColor(blockColorR, blockColorG, blockColorB, opacity);
            } else {
                RenderSystem.setShaderColor(hitColorR, hitColorG, hitColorB, opacity);
            }

            if ((Boolean) HitIndicatorClientConfigs.EdgeOfScreenMode.get()) {
                return EDGE_INDICATOR;
            } else if (hit.getType() == HitIndicatorType.ND_HIT) {
                return ND_INDICATOR;
            } else {
                return hit.getType() == HitIndicatorType.HIT ? INDICATOR : INDICATOR_BLOCK;
            }
        }

        private static ResourceLocation getMarkerTexture(HitMarkerType type, int lifetime) {
            switch (type) {
                case KILL:
                    if (lifetime > 6) {
                        return MARKER_KILL[9 - lifetime];
                    }

                    return MARKER_KILL[3];
                default:
                    return lifetime > 6 ? MARKER_CRIT[9 - lifetime] : MARKER_CRIT[3];
            }
        }

        private static double angleBetween(Vec2 first, Vec2 second) {
            double dot = (double) (first.x * second.x + first.y * second.y);
            double cross = (double) (first.x * second.y - second.x * first.y);
            double res = Math.atan2(cross, dot) * (double) 180.0F / Math.PI;
            return res;
        }

        private static float calculateDistanceFromPlayer(Vector3d damageLocation) {
            if (Minecraft.getInstance().player == null) {
                return 0.0F;
            } else {
                Vec3 playerPos = Minecraft.getInstance().player.getPosition(0.0F);
                double d0 = damageLocation.x - playerPos.x;
                double d1 = damageLocation.y - playerPos.y;
                double d2 = damageLocation.z - playerPos.z;
                return (float) Math.sqrt(d0 * d0 + d1 * d1 + d2 * d2);
            }
        }

        private static Vec3 calculateViewVector(float pPitch, float pYaw) {
            float f = pPitch * ((float) Math.PI / 180F);
            float f1 = -pYaw * ((float) Math.PI / 180F);
            float f2 = Mth.cos(f1);
            float f3 = Mth.sin(f1);
            float f4 = Mth.cos(f);
            float f5 = Mth.sin(f);
            return new Vec3((double) (f3 * f4), (double) (-f5), (double) (f2 * f4));
        }
    }
}
