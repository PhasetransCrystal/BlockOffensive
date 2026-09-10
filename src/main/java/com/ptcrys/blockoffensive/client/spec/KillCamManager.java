package com.ptcrys.blockoffensive.client.spec;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.ptcrys.blockoffensive.BlockOffensive;
import com.ptcrys.blockoffensive.net.spec.RequestAttachTeammateC2SPacket;
import com.ptcrys.fpsmatch.common.client.FPSMClient;
import com.ptcrys.fpsmatch.common.client.data.FPSMClientGlobalData;
import com.ptcrys.fpsmatch.common.client.spec.SpectateMode;
import com.ptcrys.fpsmatch.common.client.spec.SpectateState;
import com.ptcrys.fpsmatch.common.client.spec.SpectatorCameraController;
import com.ptcrys.fpsmatch.core.team.ClientTeam;
import com.ptcrys.fpsmatch.util.FPSMFormatUtil;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Team;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

@EventBusSubscriber(value = {Dist.CLIENT}, bus = Bus.FORGE)
public final class KillCamManager {
    private static final int PULL_T = 50;
    private static final int FADE_T = 24;
    private static final double EXTRA_DIST = 5.0D;
    private static final float GRAY_SWITCH_FRAC = 0.15F;
    /** PULL 进度超过该比例后开始叠加渐黑，与拉远尾段重叠（CS2 式死亡镜头）。 */
    private static final float BLACK_FADE_START = 0.55F;
    /** 相机与最近方块的间隔余量，防止贴墙时穿模。 */
    private static final double WALL_INSET = 0.35D;

    private static final int HUD_IN_T = 10;
    private static final float POP_MIN_SCALE = 0.92F;
    private static final float POP_MAX_SCALE = 1.0F;

    private static final int HUD_MIN_TICKS = 60;
    private static final int ICON_MIN_SHOW_TICKS = 20;
    private static final int ICON_MAX_WAIT_TICKS = 40;
    private static final int CAM_HOLD_EXTRA_TICKS = 40;

    private static final int HEAD = 32;
    private static final int ICON = 16;
    private static final int PAD = 8;
    private static final int BG_H = 42;
    private static final int CENTER_OFFSET_Y = 0;

    private static final int PANEL_ALPHA = 224;
    private static final int CT_BASE_RGB = 7319295;
    private static final int T_BASE_RGB = 15913338;
    private static final float VICTIM_BLEND = 0.35F;
    private static final float GRADIENT_GAMMA = 0.55F;
    private static final float KILLER_LIGHTEN = 0.28F;
    private static final float VICTIM_DARKEN = 0.18F;

    private static final ResourceLocation KILLCAM_FILTER = ResourceLocation.tryBuild("blockoffensive", "shaders/post/killcam_gray.json");
    private static final ResourceLocation FALLBACK_DESAT = ResourceLocation.tryBuild("minecraft", "shaders/post/desaturate.json");

    private static final int GRAY_BACKEND_SHADER = 0;
    private static final int GRAY_BACKEND_LIGHT = 1;

    private static final int AUTO_MIN_FPS = 45;
    private static final int AUTO_SAMPLE_FRAMES = 16;

    private static final int FAST_GRADIENT_MIN_SEG = 24;
    private static final int FAST_GRADIENT_MAX_SEG = 64;

    private static Phase phase = Phase.NONE;
    private static int tickIn = 0;

    /** 死亡镜头相机运动模型：集中管理拉远缓动、帧间插值朝向与方块碰撞收近。 */
    private static final DeathCamRig rig = new DeathCamRig();

    private static boolean hudOn = false;
    private static int hudTick = 0;
    private static int hudAlive = 0;

    private static boolean iconWanted = false;
    private static int iconReadyTick = -1;
    private static int camHoldTick = 0;

    private static UUID killerId;
    private static String killerName = "???";
    private static String gunName = "";
    private static ItemStack gunStack = ItemStack.EMPTY;
    private static ResourceLocation killerSkin;

    private static Side killerSide = Side.UNKNOWN;
    private static Side victimSide = Side.UNKNOWN;
    private static int sideResolveCooldown = 0;

    private static int clientAttachTries = 0;
    private static int clientAttachCooldown = 0;
    /** 死亡镜头整体渐黑进度(0~1)，单调递增至 1，避免阶段切换时的跳变。 */
    private static float blackFade = 0.0F;

    private static Entity ghostCam;

    private static boolean grayEnabled = false;
    private static boolean holdBlack = false;

    private static int grayBackend = GRAY_BACKEND_SHADER;
    private static boolean grayRequested = false;

    private static boolean shaderPrewarmAttempted = false;
    private static boolean shaderPrewarmed = false;
    private static boolean shaderTooHeavy = false;
    private static boolean shaderBroken = false;

    private static float uiVeilStrength = 0.0F;

    private static boolean probeActive = false;
    private static int probeFrames = 0;
    private static float probeDtSum = 0.0F;
    private static float probeWorstDt = 0.0F;

    private static long lastNs = 0L;

    private static String killedByText = "";
    private static String hudText = "";
    private static int hudTextWidth = 0;

    private static volatile IHudStyleProvider STYLE_PROVIDER;
    private static volatile ICustomHudRenderer CUSTOM_RENDERER;

    public static void setStyleProvider(BiFunction<UUID, String, Style> f) {
        Objects.requireNonNull(f);
        STYLE_PROVIDER = f::apply;
    }

    public static void setStyleProvider(IHudStyleProvider p) {
        STYLE_PROVIDER = p;
    }

    public static void setCustomRenderer(ICustomHudRenderer r) {
        CUSTOM_RENDERER = r;
    }

    public static void startFromPacket() {
        if (phase != Phase.NONE) {
            return;
        }

        Vec3 kPos = KillCamClientCache.consumeKiller();
        Vec3 vPos = KillCamClientCache.consumeVictim();
        if (kPos == null || vPos == null) {
            return;
        }

        Vec3 killerEye = kPos;
        Vec3 victimEye = vPos;

        // 拉远方向：victim - killer（即朝杀手身后反方向拉远），零长度时退化到本地玩家视向
        double rx = victimEye.x - killerEye.x;
        double ry = victimEye.y - killerEye.y;
        double rz = victimEye.z - killerEye.z;
        double len2 = rx * rx + ry * ry + rz * rz;

        double dx, dy, dz;
        if (len2 < 1.0E-6) {
            LocalPlayer p = Minecraft.getInstance().player;
            if (p == null) {
                return;
            }
            Vec3 d = Vec3.directionFromRotation(p.getXRot(), p.getYRot());
            dx = d.x;
            dy = d.y;
            dz = d.z;
        } else {
            double inv = 1.0D / Math.sqrt(len2);
            dx = rx * inv;
            dy = ry * inv;
            dz = rz * inv;
        }

        killerId = KillCamClientCache.getKillerUUID();
        killerName = KillCamClientCache.getKillerName();
        gunStack = KillCamClientCache.getWeapon();
        gunName = FPSMFormatUtil.i18n(gunStack);
        killerSkin = fetchSkin(killerId, killerName);

        killedByText = I18n.get("blockoffensive.killed_by");
        hudText = "§c" + killerName + " §7" + I18n.get("blockoffensive.killcam.used") + " §e" + gunName + " §7" + killedByText;

        Font font = Minecraft.getInstance().font;
        hudTextWidth = font != null ? font.width(hudText) : 0;

        killerSide = detectSide(killerId);
        UUID myId = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getUUID() : null;
        victimSide = detectSide(myId);
        sideResolveCooldown = 0;

        hudOn = true;
        hudTick = 0;
        hudAlive = 0;
        camHoldTick = 0;

        iconWanted = gunStack != null && !gunStack.isEmpty();
        iconReadyTick = iconWanted ? 0 : -1;

        clientAttachTries = 8;
        clientAttachCooldown = 0;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.setCameraEntity(mc.player);
        }

        phase = Phase.PULL;
        tickIn = 0;

        grayEnabled = false;
        holdBlack = false;

        blackFade = 0.0F;
        double maxPull = computeWallClampedPullDistance(mc, victimEye, dx, dy, dz);
        rig.begin(victimEye, killerEye, dx, dy, dz, maxPull);

        grayRequested = false;
        uiVeilStrength = 0.0F;
        endProbe();

        lastNs = 0L;

        ensureGhost();
        if (ghostCam != null) {
            Vec3 start = rig.position();
            ghostCam.moveTo(start.x, start.y, start.z, rig.yawAt(start.x, start.y, start.z), rig.pitchAt(start.x, start.y, start.z));
            ghostCam.setOldPosAndRot();
            mc.setCameraEntity(ghostCam);
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer pl = mc.player;
        if (pl == null) {
            reset();
            return;
        }

        boolean isSpec = pl.isSpectator();
        if (isSpec && phase != Phase.NONE && SpectateState.isRestricted()) {
            clearKillCamState(false);
        }
        if (isSpec && mc.getCameraEntity() != pl && mc.getCameraEntity() != ghostCam && phase != Phase.NONE) {
            reset();
            return;
        }
        if (hudOn) {
            ++hudAlive;
            if (sideResolveCooldown > 0) {
                --sideResolveCooldown;
            }
        }

        switch (phase) {
            case PULL -> {
                ensureGhost();

                double t = Mth.clamp((double) tickIn / (double) PULL_T, 0.0D, 1.0D);
                double s = kickThenEaseOut(t);
                rig.pull(s);

                // 渐黑与拉远尾段重叠：PULL 进度越过 BLACK_FADE_START 后单调推进
                float pullP = s >= 1.0D ? 1.0F : (float) tickIn / (float) PULL_T;
                blackFade = Math.max(blackFade, pullProgressiveFade(pullP));

                Vec3 camPos = rig.position();
                if (ghostCam != null) {
                    ghostCam.setOldPosAndRot();
                    ghostCam.setPos(camPos.x, camPos.y, camPos.z);
                    if (mc.getCameraEntity() != ghostCam) {
                        mc.setCameraEntity(ghostCam);
                    }
                }

                if (!grayEnabled && t >= (double) GRAY_SWITCH_FRAC) {
                    enableGray();
                    grayEnabled = true;
                }

                if (tickIn < PULL_T) {
                    ++tickIn;
                } else {
                    ++camHoldTick;
                }

                boolean uiLongEnough = hudAlive >= HUD_MIN_TICKS;
                boolean iconShownEnough = !iconWanted
                        || (iconReadyTick >= 0 && (hudAlive - iconReadyTick) >= ICON_MIN_SHOW_TICKS)
                        || hudAlive >= (HUD_MIN_TICKS + ICON_MAX_WAIT_TICKS);
                boolean canEnterFade = uiLongEnough && iconShownEnough && camHoldTick >= CAM_HOLD_EXTRA_TICKS;

                if (canEnterFade) {
                    phase = Phase.FADE;
                    tickIn = 0;
                    holdBlack = false;
                }
            }
            case FADE -> {
                // 淡出收尾：平滑推进到全黑，避免阶段切换跳变
                blackFade = Math.min(1.0F, blackFade + (1.0F / (float) FADE_T));
                if (!holdBlack && ++tickIn >= FADE_T) {
                    holdBlack = true;
                    BlockOffensive.INSTANCE.sendToServer(new RequestAttachTeammateC2SPacket());
                    tryLocalAttachToNearestTeammate();
                    finishKillCamForSpectating();
                }
            }
            default -> {
            }
        }

        // Only restore the camera when a killcam phase is actually active. Without the
        // phase guard this block also fires during normal teammate spectating (camera is a
        // living teammate, not `pl`/`ghostCam`), force-restoring the camera to the local
        // player every client tick and breaking "follow the spectated teammate".
        if (isSpec && phase != Phase.NONE) {
            Entity camEnt = mc.getCameraEntity();
            if (camEnt != null && camEnt != pl && camEnt != ghostCam) {
                resetForLifecycleBoundary();
            }
        }

        if (phase == Phase.NONE) {
            attemptAttachRetryIfNeeded();
            prewarmShaderIfNeeded();
        }

        if (!isSpec) {
            forceRestoreCameraToPlayer();
            reset();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onCamAngles(ViewportEvent.ComputeCameraAngles e) {
        if (phase != Phase.PULL) {
            return;
        }
        // 使用相机本帧已插值好的位置计算朝向，替代每 tick 采样的离散值，消除旋转顿挫
        Vec3 from = e.getCamera().getPosition();
        e.setYaw(rig.yawAt(from.x, from.y, from.z));
        e.setPitch(rig.pitchAt(from.x, from.y, from.z));
    }

    // BO 1.20.1-forge-official-1.3.0 (6) changed the gray-screen entry from
    // overlay(RenderGuiOverlayEvent.Post) to onRenderGuiPre/Post(RenderGuiEvent.*).
    // If this replacement keeps the old single Post hook, current BO source will not compile
    // cleanly and the gray veil/HUD can render in the wrong phase.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderGuiPre(RenderGuiEvent.Pre e) {
        boolean need = grayRequested || uiVeilStrength > 0.01F || (grayBackend == GRAY_BACKEND_SHADER && grayRequested && probeActive);
        if (!need) {
            return;
        }

        float dt = frameDtSeconds();
        if (grayBackend == GRAY_BACKEND_SHADER && grayRequested) {
            probeFrame(dt);
        }

        Window win = e.getWindow();
        int sw = win.getGuiScaledWidth();
        int sh = win.getGuiScaledHeight();
        GuiGraphics gg = e.getGuiGraphics();

        if (grayRequested || uiVeilStrength > 0.01F) {
            renderUiVeil(gg, sw, sh, dt);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderGuiPost(RenderGuiEvent.Post e) {
        float blackAlpha = currentBlackAlpha();
        if (!hudOn && blackAlpha <= 0.0F) {
            return;
        }

        Window win = e.getWindow();
        int sw = win.getGuiScaledWidth();
        int sh = win.getGuiScaledHeight();
        GuiGraphics gg = e.getGuiGraphics();
        Minecraft mc = Minecraft.getInstance();

        if (hudOn) {
            renderKillHud(mc, gg, sw, sh, blackAlpha);
        }

        // 全屏黑幕放在整个 GUI(含聊天)之后绘制，避免聊天等 UI 穿透看到黑幕下的画面
        if (blackAlpha > 0.0F) {
            int a = Mth.clamp(Math.round(255.0F * blackAlpha), 0, 255);
            int argb = a << 24;
            RenderSystem.enableBlend();
            gg.fill(0, 0, sw, sh, argb);
            RenderSystem.disableBlend();
        }
    }

    private static float currentBlackAlpha() {
        return Mth.clamp(blackFade, 0.0F, 1.0F);
    }

    @SubscribeEvent
    public static void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        resetForLifecycleBoundary();
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            resetForLifecycleBoundary();
        }
    }

    public static void resetForLifecycleBoundary() {
        reset();
    }

    private static void renderUiVeil(GuiGraphics gg, int sw, int sh, float dt) {
        float target = grayRequested ? 1.0F : 0.0F;
        float speed = grayRequested ? 16.0F : 20.0F;
        uiVeilStrength = approachExp(uiVeilStrength, target, dt, speed);

        if (uiVeilStrength <= 0.01F) {
            uiVeilStrength = 0.0F;
            return;
        }

        float s = smoothStep(uiVeilStrength);

        int grayA;
        int darkA;

        if (grayBackend == GRAY_BACKEND_LIGHT) {
            grayA = Mth.clamp((int) (116.0F * s), 0, 180);
            darkA = Mth.clamp((int) (36.0F * s), 0, 110);
        } else {
            grayA = Mth.clamp((int) (92.0F * s), 0, 150);
            darkA = Mth.clamp((int) (28.0F * s), 0, 90);
        }

        int grayArgb = (grayA << 24) | 0x808080;
        int darkArgb = darkA << 24;

        RenderSystem.enableBlend();
        gg.fill(0, 0, sw, sh, grayArgb);
        gg.fill(0, 0, sw, sh, darkArgb);

        float fps = dt > 0.0001F ? (1.0F / dt) : 120.0F;
        if (fps >= 28.0F) {
            int minDim = Math.max(1, Math.min(sw, sh));
            int edgeH = Mth.clamp(Math.round(minDim * 0.10F), 18, 72);
            int sideW = Mth.clamp(Math.round(minDim * 0.06F), 10, 48);

            int edgeA = Mth.clamp((int) (76.0F * s), 0, 120);
            int sideA = Mth.clamp((int) (46.0F * s), 0, 96);

            int edgeCol = edgeA << 24;
            gg.fillGradient(0, 0, sw, edgeH, edgeCol, 0x00000000);
            gg.fillGradient(0, sh - edgeH, sw, sh, 0x00000000, edgeCol);

            gg.fill(0, 0, sideW, sh, sideA << 24);
            gg.fill(sw - sideW, 0, sw, sh, sideA << 24);
        }

        RenderSystem.disableBlend();
    }

    private static void renderKillHud(Minecraft mc, GuiGraphics gg, int sw, int sh, float blackAlpha) {
        Font font = mc.font;
        if (font == null) {
            return;
        }

        float in = Mth.clamp((float) hudTick / (float) HUD_IN_T, 0.0F, 1.0F);
        if (hudTick < HUD_IN_T) {
            ++hudTick;
        }

        float alphaMul = in * (1.0F - blackAlpha);
        if (alphaMul <= 0.01F) {
            return;
        }

        if ((killerSide == Side.UNKNOWN || victimSide == Side.UNKNOWN) && sideResolveCooldown <= 0) {
            if (killerSide == Side.UNKNOWN) {
                killerSide = detectSide(killerId);
            }
            if (victimSide == Side.UNKNOWN) {
                UUID myId = mc.player != null ? mc.player.getUUID() : null;
                victimSide = detectSide(myId);
            }
            sideResolveCooldown = 10;
        }

        int killerRGB = lighten(sideBaseRGB(killerSide));
        int victimRGB = darken(sideBaseRGB(victimSide));
        int startARGB = (PANEL_ALPHA << 24) | (killerRGB & 0xFFFFFF);
        int endARGB = (PANEL_ALPHA << 24) | (mixRGB(killerRGB, victimRGB, VICTIM_BLEND) & 0xFFFFFF);

        String text = hudText;
        int txtW = hudTextWidth > 0 ? hudTextWidth : font.width(text);

        boolean reserveIcon = iconWanted;
        int iconPart = reserveIcon ? 24 : 0;
        int totalW = 40 + txtW + PAD + iconPart;

        int cx = sw / 2;
        int cy = sh / 2 + CENTER_OFFSET_Y;

        float pop = easeOutBack(in);
        float scale = Mth.lerp(pop, POP_MIN_SCALE, POP_MAX_SCALE);

        float maxW = (float) sw * 0.92F;
        if ((float) totalW * scale > maxW) {
            scale *= maxW / ((float) totalW * scale);
        }

        float tx = snapToPixel((float) cx, scale);
        float ty = snapToPixel((float) cy, scale);

        int x0 = Math.round(-(float) totalW / 2.0F);
        int y0 = -21;
        int x1 = x0 + totalW;
        int y1 = y0 + BG_H;

        RenderSystem.enableBlend();

        PoseStack pose = gg.pose();
        pose.pushPose();
        pose.translate(tx, ty, 0.0F);
        pose.scale(scale, scale, 1.0F);

        drawGradientPanelRect(gg, x0, y0, x1, y1, mulAlpha(startARGB, alphaMul), mulAlpha(endARGB, alphaMul), GRADIENT_GAMMA);

        int headX = x0 + PAD;
        int headY = y0 + 5;

        if (killerSkin != null) {
            gg.blit(killerSkin, headX, headY, 32, 32, 8.0F, 8.0F, 8, 8, 64, 64);
            gg.blit(killerSkin, headX, headY, 32, 32, 40.0F, 8.0F, 8, 8, 64, 64);
        }

        int textLeft = headX + HEAD + PAD;
        int textRight = x1 - (reserveIcon ? 32 : PAD);
        int allowedW = Math.max(0, textRight - textLeft);

        float textScale = Math.min(1.0F, (float) allowedW / Math.max(1.0F, (float) txtW));
        int tDrawW = Math.round((float) txtW * textScale);
        int textX = textLeft + (allowedW - tDrawW) / 2;
        int textY = y0 + (BG_H - 9) / 2;

        pose.pushPose();
        pose.translate((float) textX, (float) textY, 0.0F);
        pose.scale(textScale, textScale, 1.0F);
        gg.drawString(font, text, 0, 0, mulAlpha(0xFFFFFFFF, alphaMul), false);
        pose.popPose();

        pose.popPose();

        if (gunStack != null && !gunStack.isEmpty()) {
            int localIconX = x1 - PAD - ICON;
            int localIconY = y0 + 13;

            int absIconX = Math.round(tx + (float) localIconX * scale);
            int absIconY = Math.round(ty + (float) localIconY * scale);
            int size = Math.max(1, Math.round((float) ICON * scale));

            renderItemAt(gg, gunStack, absIconX, absIconY, size, size, font);
            if (iconWanted && iconReadyTick < 0) {
                iconReadyTick = hudAlive;
            }
        }

        RenderSystem.disableBlend();
    }

    private static float frameDtSeconds() {
        long now = System.nanoTime();
        float dt = lastNs == 0L ? 0.0F : (float) ((now - lastNs) / 1.0E9D);
        lastNs = now;
        return Mth.clamp(dt, 0.0F, 0.1F);
    }

    private static float approachExp(float current, float target, float dt, float speed) {
        float k = 1.0F - (float) Math.exp(-speed * dt);
        k = Mth.clamp(k, 0.0F, 1.0F);
        return current + (target - current) * k;
    }

    private static float smoothStep(float x) {
        x = Mth.clamp(x, 0.0F, 1.0F);
        return x * x * (3.0F - 2.0F * x);
    }

    private static void prewarmShaderIfNeeded() {
        if (shaderPrewarmAttempted || shaderPrewarmed || shaderBroken) {
            return;
        }
        if (!canUseShaderBackend()) {
            shaderBroken = true;
            grayBackend = GRAY_BACKEND_LIGHT;
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null || mc.gameRenderer == null) {
            return;
        }
        if (phase != Phase.NONE || grayRequested) {
            return;
        }

        shaderPrewarmAttempted = true;
        try {
            mc.gameRenderer.loadEffect(KILLCAM_FILTER);
            mc.gameRenderer.shutdownEffect();
            shaderPrewarmed = true;
        } catch (Throwable t) {
            try {
                mc.gameRenderer.loadEffect(FALLBACK_DESAT);
                mc.gameRenderer.shutdownEffect();
                shaderPrewarmed = true;
            } catch (Throwable t2) {
                shaderBroken = true;
                grayBackend = GRAY_BACKEND_LIGHT;
            }
        }
    }

    private static void enableGray() {
        grayRequested = true;
        lastNs = 0L;

        if (shaderBroken || shaderTooHeavy || !canUseShaderBackend()) {
            grayBackend = GRAY_BACKEND_LIGHT;
            uiVeilStrength = Math.max(uiVeilStrength, 0.85F);
            return;
        }

        grayBackend = GRAY_BACKEND_SHADER;
        beginProbe();
        uiVeilStrength = Math.max(uiVeilStrength, 0.55F);

        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gameRenderer != null) {
                mc.gameRenderer.loadEffect(KILLCAM_FILTER);
            }
        } catch (Throwable t) {
            try {
                Minecraft mc = Minecraft.getInstance();
                if (mc.gameRenderer != null) {
                    mc.gameRenderer.loadEffect(FALLBACK_DESAT);
                }
            } catch (Throwable t2) {
                shaderBroken = true;
                grayBackend = GRAY_BACKEND_LIGHT;
                uiVeilStrength = Math.max(uiVeilStrength, 0.85F);
                endProbe();
            }
        }
    }

    private static void disableGray() {
        grayRequested = false;
        endProbe();

        if (grayBackend == GRAY_BACKEND_SHADER) {
            try {
                Minecraft mc = Minecraft.getInstance();
                if (mc.gameRenderer != null) {
                    mc.gameRenderer.shutdownEffect();
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static boolean canUseShaderBackend() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.options == null) {
                return false;
            }
            GraphicsStatus gs = mc.options.graphicsMode().get();
            return gs.getId() != 0;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void beginProbe() {
        if (shaderBroken || shaderTooHeavy) {
            return;
        }
        probeActive = true;
        probeFrames = 0;
        probeDtSum = 0.0F;
        probeWorstDt = 0.0F;
    }

    private static void endProbe() {
        probeActive = false;
        probeFrames = 0;
        probeDtSum = 0.0F;
        probeWorstDt = 0.0F;
    }

    private static void probeFrame(float dt) {
        if (!probeActive || dt <= 0.0F) {
            return;
        }
        if (probeFrames >= AUTO_SAMPLE_FRAMES) {
            return;
        }

        ++probeFrames;
        probeDtSum += dt;
        if (dt > probeWorstDt) {
            probeWorstDt = dt;
        }

        if (probeFrames < AUTO_SAMPLE_FRAMES) {
            return;
        }

        float avgDt = probeDtSum / (float) Math.max(1, probeFrames);
        float dtBudget = 1.0F / Math.max(20.0F, (float) AUTO_MIN_FPS);

        boolean tooHeavy = avgDt > dtBudget * 1.20F || probeWorstDt > dtBudget * 1.70F;
        probeActive = false;

        if (!tooHeavy) {
            return;
        }

        shaderTooHeavy = true;
        switchToLightBackendNow();
    }

    private static void switchToLightBackendNow() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.gameRenderer != null) {
            try {
                mc.gameRenderer.shutdownEffect();
            } catch (Throwable ignored) {
            }
        }
        grayBackend = GRAY_BACKEND_LIGHT;
        uiVeilStrength = Math.max(uiVeilStrength, 0.85F);
    }

    private static double kickThenEaseOut(double t) {
        t = Mth.clamp(t, 0.0D, 1.0D);
        double pre = 0.2D;
        double kick = 0.33D;

        if (t <= pre) {
            double u = t / pre;
            return kick * u * u;
        }

        double u = (t - pre) / (1.0D - pre);
        double easeOutCubic = 1.0D - Math.pow(1.0D - u, 3.0D);
        return kick + (1.0D - kick) * easeOutCubic;
    }

    /**
     * PULL 进度对应的渐黑叠值：进度 &lt; {@link #BLACK_FADE_START} 时为 0，
     * 之后线性拉满，与拉远尾段重叠，避免进入 FADE 时的明暗跳变。
     */
    private static float pullProgressiveFade(float pullProgress) {
        if (pullProgress <= BLACK_FADE_START) {
            return 0.0F;
        }
        return Mth.clamp((pullProgress - BLACK_FADE_START) / (1.0F - BLACK_FADE_START), 0.0F, 1.0F);
    }

    /**
     * 沿拉远方向(victim→killer身后)做方块射线检测：相机最多拉至最近方块前的
     * {@link #WALL_INSET} 距离，实现"根据方块碰撞体积自动收近"；无遮挡时用满距离。
     */
    private static double computeWallClampedPullDistance(Minecraft mc, Vec3 from, double dx, double dy, double dz) {
        if (mc.level == null || mc.player == null) {
            return EXTRA_DIST;
        }
        Vec3 to = from.add(dx * EXTRA_DIST, dy * EXTRA_DIST, dz * EXTRA_DIST);
        ClipContext ctx = new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player);
        BlockHitResult hit = mc.level.clip(ctx);
        if (hit.getType() == HitResult.Type.BLOCK) {
            double blocked = from.distanceTo(hit.getLocation()) - WALL_INSET;
            return Math.max(0.6D, blocked);
        }
        return EXTRA_DIST;
    }

    private static Side detectSide(UUID id) {
        if (id == null) {
            return Side.UNKNOWN;
        }

        try {
            FPSMClientGlobalData gd = FPSMClient.getGlobalData();
            Optional<ClientTeam> opt = gd.getTeamByUUID(id);
            if (opt.isPresent()) {
                String s = opt.get().getName().trim().toLowerCase(Locale.ROOT);
                if (s.equals("ct") || s.contains("counter")) {
                    return Side.CT;
                }
                if (s.equals("t") || s.contains("terror")) {
                    return Side.T;
                }
            }
        } catch (Throwable ignored) {
        }

        Minecraft mc = Minecraft.getInstance();
        ClientLevel lvl = mc.level;
        if (lvl != null) {
            Player p = lvl.getPlayerByUUID(id);
            if (p != null) {
                Team team = p.getTeam();
                if (team != null) {
                    String n = team.getName().toLowerCase(Locale.ROOT);
                    if (n.contains("ct") || n.contains("counter") || n.contains("blue")) {
                        return Side.CT;
                    }
                    if (n.equals("t") || n.contains("terror") || n.contains("red") || n.matches(".*\\bt\\b")) {
                        return Side.T;
                    }
                }
            }
        }

        return Side.UNKNOWN;
    }

    private static int sideBaseRGB(Side s) {
        return switch (s) {
            case CT -> CT_BASE_RGB;
            case T -> T_BASE_RGB;
            default -> 8421504;
        };
    }

    private static ResourceLocation fetchSkin(UUID id, String name) {
        return Minecraft.getInstance().getSkinManager().getInsecureSkinLocation(new GameProfile(id, name));
    }

    private static void drawGradientPanelRect(GuiGraphics gg, int x0, int y0, int x1, int y1, int startARGB, int endARGB, float gamma) {
        int w = Math.max(1, x1 - x0);
        if (w <= 2) {
            gg.fill(x0, y0, x1, y1, startARGB);
            return;
        }

        int seg = Mth.clamp(w / 4, FAST_GRADIENT_MIN_SEG, FAST_GRADIENT_MAX_SEG);
        seg = Math.min(seg, w);

        if (seg <= 1) {
            gg.fill(x0, y0, x1, y1, startARGB);
            return;
        }

        for (int i = 0; i < seg; ++i) {
            int sx0 = x0 + (int) ((long) i * (long) w / (long) seg);
            int sx1 = x0 + (int) ((long) (i + 1) * (long) w / (long) seg);
            if (sx1 <= sx0) {
                continue;
            }

            float t = (i == 0) ? 0.0F : (i == seg - 1) ? 1.0F : ((float) i + 0.5F) / (float) seg;
            float tg = gamma <= 0.0F ? t : (float) Math.pow((double) t, (double) gamma);
            int col = lerpARGB(startARGB, endARGB, tg);
            gg.fill(sx0, y0, sx1, y1, col);
        }
    }

    private static void renderItemAt(GuiGraphics g, ItemStack s, int x, int y, int w, int h, Font font) {
        if (s == null || s.isEmpty()) {
            return;
        }

        PoseStack pose = g.pose();
        pose.pushPose();

        float sc = Math.max(0.001F, (float) Math.min(w, h) / 16.0F);
        int dx = x + Math.round(((float) w - 16.0F * sc) / 2.0F);
        int dy = y + Math.round(((float) h - 16.0F * sc) / 2.0F);

        pose.translate((float) dx, (float) dy, 0.0F);
        pose.scale(sc, sc, 1.0F);

        g.renderItem(s, 0, 0);
        g.renderItemDecorations(font, s, 0, 0);

        pose.popPose();
    }

    private static int lighten(int rgb) {
        return mixRGB(rgb, 0xFFFFFF, KILLER_LIGHTEN);
    }

    private static int darken(int rgb) {
        return mixRGB(rgb, 0x000000, VICTIM_DARKEN);
    }

    private static float snapToPixel(float v, float scale) {
        return (float) Math.round(v * scale) / scale;
    }

    private static int mulAlpha(int argb, float mul) {
        mul = Mth.clamp(mul, 0.0F, 1.0F);
        int a = (int) ((float) ((argb >>> 24) & 255) * mul);
        return (a << 24) | (argb & 0xFFFFFF);
    }

    private static int lerpARGB(int a, int b, float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);

        int aA = (a >>> 24) & 255;
        int aR = (a >>> 16) & 255;
        int aG = (a >>> 8) & 255;
        int aB = a & 255;

        int bA = (b >>> 24) & 255;
        int bR = (b >>> 16) & 255;
        int bG = (b >>> 8) & 255;
        int bB = b & 255;

        int rA = aA + Math.round((float) (bA - aA) * t);
        int rR = aR + Math.round((float) (bR - aR) * t);
        int rG = aG + Math.round((float) (bG - aG) * t);
        int rB = aB + Math.round((float) (bB - aB) * t);

        return (rA << 24) | (rR << 16) | (rG << 8) | rB;
    }

    private static int mixRGB(int c1, int c2, float p) {
        p = Mth.clamp(p, 0.0F, 1.0F);

        int r1 = (c1 >>> 16) & 255;
        int g1 = (c1 >>> 8) & 255;
        int b1 = c1 & 255;

        int r2 = (c2 >>> 16) & 255;
        int g2 = (c2 >>> 8) & 255;
        int b2 = c2 & 255;

        int r = (int) ((float) r1 + (float) (r2 - r1) * p);
        int g = (int) ((float) g1 + (float) (g2 - g1) * p);
        int b = (int) ((float) b1 + (float) (b2 - b1) * p);

        return (r << 16) | (g << 8) | b;
    }

    private static float easeOutBack(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        float c1 = 1.70158F;
        float c3 = c1 + 1.0F;
        float u = t - 1.0F;
        return 1.0F + c3 * u * u * u + c1 * u * u;
    }

    private static void ensureGhost() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        if (ghostCam == null || ghostCam.level() != mc.level || ghostCam.isRemoved()) {
            Entity g = EntityType.MARKER.create(mc.level);
            if (g == null) {
                g = EntityType.ARMOR_STAND.create(mc.level);
            }
            ghostCam = g;
        }
    }

    private static void attemptAttachRetryIfNeeded() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer me = mc.player;
        if (me == null || mc.level == null) {
            return;
        }

        boolean needAttach = me.isSpectator() && mc.getCameraEntity() == me;
        if (!needAttach) {
            return;
        }

        if (clientAttachTries <= 0) {
            return;
        }

        if (clientAttachCooldown > 0) {
            --clientAttachCooldown;
            return;
        }

        BlockOffensive.INSTANCE.sendToServer(new RequestAttachTeammateC2SPacket());
        tryLocalAttachToNearestTeammate();
        --clientAttachTries;
        clientAttachCooldown = 5;
    }

    private static void tryLocalAttachToNearestTeammate() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer me = mc.player;
        ClientLevel lvl = mc.level;
        if (me == null || lvl == null || !me.isSpectator()) {
            return;
        }

        Optional<String> myTeam = getTeam(me.getUUID());
        Player best = null;
        double bestD = Double.MAX_VALUE;

        for (Player p : lvl.players()) {
            if (p == null || p == me) {
                continue;
            }
            if (!p.isAlive() || p.isSpectator()) {
                continue;
            }
            if (!isSameTeamClientSide(myTeam, me, p)) {
                continue;
            }

            double d = p.distanceToSqr(me);
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }

        if (best != null) {
            mc.setCameraEntity(best);
        }
    }

    private static Optional<String> getTeam(UUID id) {
        try {
            return FPSMClient.getGlobalData().getTeamByUUID(id).map(t -> t.getName().trim().toLowerCase(Locale.ROOT));
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }

    private static boolean isSameTeamClientSide(Optional<String> myTeam, Player me, Player other) {
        Optional<String> ot = getTeam(other.getUUID());
        if (myTeam.isPresent() && ot.isPresent()) {
            return ot.get().equals(myTeam.get());
        }

        Team mt = me.getTeam();
        Team otm = other.getTeam();
        if (mt != null && otm != null) {
            return mt.getName().equalsIgnoreCase(otm.getName());
        }

        return false;
    }

    private static void forceRestoreCameraToPlayer() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) {
            return;
        }
        if (mc.getCameraEntity() != p) {
            mc.setCameraEntity(p);
        }
    }

    private static void reset() {
        clearKillCamState(true);
    }

    private static void finishKillCamForSpectating() {
        int remainingAttachTries = clientAttachTries;
        clearKillCamState(false);
        clientAttachTries = remainingAttachTries;
        clientAttachCooldown = 5;
    }

    private static void clearKillCamState(boolean restoreCamera) {
        if (restoreCamera) {
            forceRestoreCameraToPlayer();
            // 回到本体时清空观战状态：否则无队友时 DEATH_SPOT / C4_ORBIT 的相机锁定
            // (CameraRestrictedSpectatorMixin) 会在重生后仍把相机钉在死亡点/装弹点，
            // 表现为"重生后视角没回到玩家本体"。
            SpectateState.set(SpectateMode.FREE);
            SpectatorCameraController.reset();
        } else {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.getCameraEntity() == ghostCam) {
                mc.setCameraEntity(mc.player);
            }
        }
        blackFade = 0.0F;
        disableGray();

        phase = Phase.NONE;
        tickIn = 0;

        hudOn = false;
        hudTick = 0;
        hudAlive = 0;

        iconWanted = false;
        iconReadyTick = -1;
        camHoldTick = 0;

        killerId = null;
        killerName = "???";
        gunName = I18n.get("blockoffensive.unknown_weapon");
        gunStack = ItemStack.EMPTY;
        killerSkin = null;

        killerSide = Side.UNKNOWN;
        victimSide = Side.UNKNOWN;
        sideResolveCooldown = 0;

        killedByText = I18n.get("blockoffensive.killed_by");
        hudText = "";
        hudTextWidth = 0;

        clientAttachTries = 0;
        clientAttachCooldown = 0;

        lastNs = 0L;

        rig.reset();

        ghostCam = null;

        grayEnabled = false;
        holdBlack = false;

        grayRequested = false;
        uiVeilStrength = 0.0F;
        endProbe();
    }

    private KillCamManager() {
    }

    /**
     * 死亡镜头相机运动模型。
     * <p>集中管理死亡镜头的空间数学：起点(受害者眼位)、视线目标(杀手眼位)、
     * 归一化拉远方向、方块截断后的最大拉远距离、每 tick 的拉远缓动推进，
     * 以及基于帧间插值位置的平滑朝向。把相机"怎么动"与 {@link KillCamManager}
     * 的"何时切相位/画 HUD"解耦，提升可读性与可调性。</p>
     */
    static final class DeathCamRig {
        private double victimX;
        private double victimY;
        private double victimZ;
        private double targetX;
        private double targetY;
        private double targetZ;
        private double dirX;
        private double dirY;
        private double dirZ;
        private double maxPull;
        private double posX;
        private double posY;
        private double posZ;
        private double prevX;
        private double prevY;
        private double prevZ;
        private boolean active;

        void begin(Vec3 victim, Vec3 killer, double dirX, double dirY, double dirZ, double maxPull) {
            this.victimX = victim.x;
            this.victimY = victim.y;
            this.victimZ = victim.z;
            this.targetX = killer.x;
            this.targetY = killer.y;
            this.targetZ = killer.z;
            this.dirX = dirX;
            this.dirY = dirY;
            this.dirZ = dirZ;
            this.maxPull = maxPull;
            this.posX = victimX;
            this.posY = victimY;
            this.posZ = victimZ;
            this.prevX = victimX;
            this.prevY = victimY;
            this.prevZ = victimZ;
            this.active = true;
        }

        /** 按缓动进度推进本 tick 相机位置（s = 缓动后的 0~1）。 */
        void pull(double easedProgress) {
            this.prevX = posX;
            this.prevY = posY;
            this.prevZ = posZ;
            this.posX = victimX + dirX * maxPull * easedProgress;
            this.posY = victimY + dirY * maxPull * easedProgress;
            this.posZ = victimZ + dirZ * maxPull * easedProgress;
        }

        /** 当前 tick 相机位置（喂给 ghostCam，原版会做帧间插值）。 */
        Vec3 position() {
            return new Vec3(posX, posY, posZ);
        }

        /** 从指定位置看向视线目标(杀手)的偏航角。 */
        float yawAt(double fromX, double fromY, double fromZ) {
            return (float) Math.toDegrees(Math.atan2(-(targetX - fromX), targetZ - fromZ));
        }

        /** 从指定位置看向视线目标(杀手)的俯仰角。 */
        float pitchAt(double fromX, double fromY, double fromZ) {
            double horizontal = Math.sqrt(
                    (targetX - fromX) * (targetX - fromX) + (targetZ - fromZ) * (targetZ - fromZ));
            return (float) Math.toDegrees(Math.atan2(-(targetY - fromY), horizontal));
        }

        boolean active() {
            return active;
        }

        void reset() {
            active = false;
        }
    }

    private enum Phase {
        NONE,
        PULL,
        FADE
    }

    private enum Side {
        CT,
        T,
        UNKNOWN
    }

    public record Style(int bg0, int bg1, int border) {
        public Style(int bg0, int bg1, int border) {
            this.bg0 = bg0;
            this.bg1 = bg1;
            this.border = border;
        }

        public int bg0() {
            return this.bg0;
        }

        public int bg1() {
            return this.bg1;
        }

        public int border() {
            return this.border;
        }
    }

    public interface ICustomHudRenderer {
        boolean render(GuiGraphics var1, int var2, int var3, UUID var4, String var5, String var6, String var7, ItemStack var8, Style var9);
    }

    @FunctionalInterface
    public interface IHudStyleProvider extends BiFunction<UUID, String, Style> {
    }
}
