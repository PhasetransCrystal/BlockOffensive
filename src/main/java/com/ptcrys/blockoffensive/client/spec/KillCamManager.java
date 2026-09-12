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
import com.ptcrys.fpsmatch.common.client.spec.SpectateTarget;
import com.ptcrys.fpsmatch.mixin.spec.teammate.CameraInvokerMixin;
import com.ptcrys.fpsmatch.common.client.spec.SpectatorCameraController;
import com.ptcrys.fpsmatch.core.team.ClientTeam;
import com.ptcrys.fpsmatch.util.FPSMFormatUtil;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
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
    private static final double EXTRA_DIST = 5.0D;
    private static final double WALL_INSET = 0.35D;
    private static final float POP_MIN_SCALE = 0.92F;
    private static final float POP_MAX_SCALE = 1.0F;

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

    private static final int FAST_GRADIENT_MIN_SEG = 24;
    private static final int FAST_GRADIENT_MAX_SEG = 64;
    private static final DeathCameraTimeline timeline = new DeathCameraTimeline();
    private static final DeathCamRig rig = new DeathCamRig();
    private static boolean pendingStart;
    private static int pendingTicks;
    private static boolean targetReceived;
    private static boolean ownsSpectatorState;
    private static int sideResolveCooldown;
    private static boolean iconWanted;
    private static Vec3 deathAnchor;
    private static float deathYaw;
    private static float deathPitch;
    private static Entity ghostCam;

    private static UUID killerId;
    private static String killerName = "???";
    private static String gunName = "";
    private static ItemStack gunStack = ItemStack.EMPTY;
    private static ResourceLocation killerSkin;

    private static Side killerSide = Side.UNKNOWN;
    private static Side victimSide = Side.UNKNOWN;
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
        if (timeline.active()) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;
        if (!client.player.isSpectator()) {
            // Do not consume the packet until the game-mode update arrives.
            pendingStart = true;
            return;
        }
        pendingStart = false;
        pendingTicks = 0;

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
            dx = -d.x;
            dy = -d.y;
            dz = -d.z;
            killerEye = victimEye.add(d);
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

        iconWanted = gunStack != null && !gunStack.isEmpty();
        Minecraft mc = Minecraft.getInstance();
        deathAnchor = victimEye;
        deathYaw = mc.player.getYRot();
        deathPitch = mc.player.getXRot();
        targetReceived = false;
        timeline.start();
        ownsSpectatorState = true;
        // ATTACH with no target locks spectator controls without invoking an orbit rig.
        SpectateState.setTarget(null);
        SpectateState.set(SpectateMode.ATTACH);
        SpectatorCameraController.reset();
        double maxPull = Math.min(0.8D, computeWallClampedPullDistance(mc, victimEye, dx, dy, dz));
        rig.begin(victimEye, killerEye, dx, dy, dz, maxPull, deathYaw, deathPitch);

        ensureGhost();
        if (ghostCam != null) {
            Vec3 start = rig.position();
            ghostCam.moveTo(start.x, start.y, start.z, rig.yawAt(start.x, start.y, start.z), rig.pitchAt(start.x, start.y, start.z));
            ghostCam.setOldPosAndRot();
            mc.setCameraEntity(ghostCam);
            // Camera changes can replace entity post effects; restore ours after the switch.
            DeathWorldMask.begin();
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer pl = mc.player;
        if (pl == null || mc.level == null) {
            reset();
            return;
        }
        if (pendingStart) {
            if (pl.isSpectator()) {
                startFromPacket();
                return;
            }
            if (++pendingTicks >= 40) reset();
            return;
        }
        if (!timeline.active()) {
            if (ownsSpectatorState && !pl.isSpectator()) resetForLifecycleBoundary();
            return;
        }
        if (!pl.isSpectator()) {
            reset();
            return;
        }
        if (sideResolveCooldown > 0) --sideResolveCooldown;
        timeline.tick();
        if (timeline.waitingForTarget()) {
            // Accept the target as soon as the regular spectator handler has
            // installed it.  The mixin acknowledgement is an optimization;
            // the state check also covers packet-handler order on Forge.
            if ((targetReceived || SpectateState.getTarget() != null) && attachReceivedTarget()) {
                clearKillCamState(false);
                return;
            }
            if (timeline.targetWaitExpired()) {
                // Network/unloaded-entity fallback is still a restricted death-location view.
                SpectateState.setTarget(new SpectateTarget(SpectateMode.DEATH_SPOT,
                        pl.getId(), deathAnchor, deathYaw, deathPitch, 4.0F));
                SpectatorCameraController.setAngles(deathYaw, deathPitch);
                mc.setCameraEntity(pl);
                clearKillCamState(false);
                return;
            }
            if (timeline.shouldRequestTarget()) {
                BlockOffensive.INSTANCE.sendToServer(new RequestAttachTeammateC2SPacket());
            }
        }
        ensureGhost();
        if (ghostCam != null) {
            if (mc.getCameraEntity() != ghostCam) {
                mc.setCameraEntity(ghostCam);
                // Camera changes can replace entity post effects; restore ours after the switch.
                DeathWorldMask.begin();
            }
        }
    }

    private static boolean attachReceivedTarget() {
        Minecraft mc = Minecraft.getInstance();
        SpectateTarget target = SpectateState.getTarget();
        if (target == null || mc.player == null || mc.level == null) return false;
        if (target.mode() == SpectateMode.TEAMMATE) {
            Entity entity = mc.level.getEntity(target.entityId());
            if (!(entity instanceof Player player) || !player.isAlive() || player.isSpectator()) return false;
            mc.setCameraEntity(entity);
            return true;
        }
        if (target.mode() == SpectateMode.C4_ORBIT || target.mode() == SpectateMode.DEATH_SPOT) {
            mc.setCameraEntity(mc.player);
            return true;
        }
        return false;
    }

    /** Called by the target-packet mixin; stale targets cannot interrupt the presentation. */
    public static boolean deferSpectatorTarget() {
        return pendingStart || (timeline.active() && !timeline.waitingForTarget());
    }

    public static void spectatorTargetReceived() {
        if (timeline.waitingForTarget()) targetReceived = true;
    }

    public static boolean isActive() {
        return timeline.active() || pendingStart;
    }

    public static boolean timelineActive() {
        return timeline.active();
    }

    public static float maskProgress(float partialTick) {
        return timeline.maskProgress(partialTick);
    }

    public static float presentationProgress(float partialTick) {
        return timeline.presentationProgress(partialTick);
    }

    public static float hitFilterStrength(float partialTick) {
        // The red damage grade remains active for the whole death presentation.
        // The world shader lets the later black fade take visual precedence.
        return timeline.active() ? 0.72F : 0.0F;
    }

    public static float hitVignetteStrength(float partialTick) {
        return timeline.active() ? 1.0F : 0.0F;
    }

    /** Camera.setup tail: ignores F5 offsets and stale orbit state during death. */
    public static void applyDeathCamera(net.minecraft.client.Camera camera, float partialTick) {
        if (!timeline.active()) return;
        float fall = timeline.fallProgress(partialTick);
        rig.pull(fall);
        Vec3 pos = rig.position();
        float yaw = rig.yawAt(pos.x, pos.y, pos.z);
        double yawRad = Math.toRadians(yaw);
        double left = 0.34D * fall;
        pos = pos.add(-Math.cos(yawRad) * left, -0.85D * fall, -Math.sin(yawRad) * left);
        float shake = timeline.shakeProgress(partialTick);
        float t = timeline.elapsed(partialTick);
        pos = pos.add(Math.sin(t * 31.0F) * 0.045D * shake,
                Math.cos(t * 37.0F) * 0.035D * shake,
                Math.sin(t * 27.0F + 0.7F) * 0.045D * shake);
        CameraInvokerMixin access = (CameraInvokerMixin) camera;
        access.invokeSetPosition(pos.x, pos.y, pos.z);
        access.invokeSetRotation(yaw + (float) Math.sin(t * 22.0F) * 1.4F * shake,
                rig.pitchAt(pos.x, pos.y, pos.z) + (float) Math.cos(t * 25.0F) * 1.2F * shake);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onCamAngles(ViewportEvent.ComputeCameraAngles e) {
        if (!timeline.active()) return;
        Vec3 pos = e.getCamera().getPosition();
        float fall = timeline.fallProgress(0.0F);
        float shake = timeline.shakeProgress(0.0F);
        float t = timeline.elapsed(0.0F);
        e.setYaw(rig.yawAt(pos.x, pos.y, pos.z) + (float) Math.sin(t * 22.0F) * 1.4F * shake);
        e.setPitch(rig.pitchAt(pos.x, pos.y, pos.z) + (float) Math.cos(t * 25.0F) * 1.2F * shake);
        e.setRoll(-12.0F * fall + (float) Math.sin(t * 29.0F) * 1.8F * shake);
    }

    @SubscribeEvent
    public static void onMovement(net.minecraftforge.client.event.MovementInputUpdateEvent e) {
        if (!isActive()) return;
        var input = e.getInput();
        input.forwardImpulse = 0;
        input.leftImpulse = 0;
        input.up = input.down = input.left = input.right = false;
        input.jumping = input.shiftKeyDown = false;
    }

    @SubscribeEvent
    public static void onInteraction(net.minecraftforge.client.event.InputEvent.InteractionKeyMappingTriggered e) {
        if (!isActive()) return;
        e.setSwingHand(false);
        e.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderGuiPost(RenderGuiEvent.Post e) {
        if (!timeline.showInformation()) return;
        Window win = e.getWindow();
        renderDamageDirection(e.getGuiGraphics(), win.getGuiScaledWidth(), win.getGuiScaledHeight(), timeline.shakeProgress(0.0F));
        renderKillHud(Minecraft.getInstance(), e.getGuiGraphics(),
                win.getGuiScaledWidth(), win.getGuiScaledHeight());
    }

    private static void renderDamageDirection(GuiGraphics gg, int sw, int sh, float strength) {
        if (strength <= 0.01F) return;
        float cx = sw * 0.5F;
        float cy = sh * 0.08F;
        int alpha = Mth.clamp(Math.round(220.0F * (1.0F - strength * 0.35F)), 0, 255);
        int color = (alpha << 24) | 0xD51F2B;
        // Eleven short radial bars form a compact upper-center hit arc.
        for (int i = 0; i < 11; ++i) {
            double angle = Math.toRadians(205.0D + i * 13.0D);
            float radius = 22.0F + (i == 5 ? 3.0F : 0.0F);
            int x = Math.round(cx + (float) Math.cos(angle) * radius);
            int y = Math.round(cy + (float) Math.sin(angle) * radius * 0.55F);
            int width = i == 5 ? 6 : 4;
            gg.fill(x - width / 2, y - 2, x + width / 2 + 1, y + 2, color);
        }
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
        if (ownsSpectatorState || isActive()) {
            forceRestoreCameraToPlayer();
            SpectateState.set(SpectateMode.FREE);
            SpectatorCameraController.reset();
        }
        ownsSpectatorState = false;
        reset();
    }

    private static void renderKillHud(Minecraft mc, GuiGraphics gg, int sw, int sh) {
        Font font = mc.font;
        if (font == null) {
            return;
        }

        float in = 1.0F;
        float alphaMul = 1.0F;

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
        }

        RenderSystem.disableBlend();
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
            return Math.max(0.0D, blocked);
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

    private static void clearKillCamState(boolean restoreCamera) {
        DeathWorldMask.close();
        if (restoreCamera && isActive()) {
            forceRestoreCameraToPlayer();
            SpectateState.set(SpectateMode.FREE);
            SpectatorCameraController.reset();
            ownsSpectatorState = false;
        }
        timeline.reset();
        pendingStart = false;
        pendingTicks = 0;
        targetReceived = false;
        killerId = null;
        killerName = "???";
        gunStack = ItemStack.EMPTY;
        killerSkin = null;
        killerSide = victimSide = Side.UNKNOWN;
        iconWanted = false;
        hudText = "";
        hudTextWidth = 0;
        rig.reset();
        ghostCam = null;
        KillCamClientCache.clear();
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
        private float baseYaw;
        private float basePitch;
        private boolean active;

        void begin(Vec3 victim, Vec3 killer, double dirX, double dirY, double dirZ, double maxPull,
                   float baseYaw, float basePitch) {
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
            this.baseYaw = baseYaw;
            this.basePitch = basePitch;
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
            return baseYaw;
        }

        /** 从指定位置看向视线目标(杀手)的俯仰角。 */
        float pitchAt(double fromX, double fromY, double fromZ) {
            return basePitch;
        }

        boolean active() {
            return active;
        }

        void reset() {
            active = false;
        }
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
