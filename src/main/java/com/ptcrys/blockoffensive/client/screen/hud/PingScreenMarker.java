package com.ptcrys.blockoffensive.client.screen.hud;

import com.ptcrys.blockoffensive.client.data.CSClientData;
import com.ptcrys.blockoffensive.net.ping.PingC2SPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec3;

/**
 * 队友 Ping 屏幕投影标记（HUD 层，可靠性最高）：
 * <p>
 * 将 Ping 世界坐标投影到屏幕实际位置（准星视角内），在该位置绘制彩色圆点 + 距离（米）。
 * 投影基于相机 yaw/pitch 与 FOV，标记准确落在 Ping 在屏幕上的位置，不是边缘环绕箭头。
 * 视野外不绘制。与实体光柱（PingMarkerRenderer）双保险，保证 Ping 一定有可见反馈。
 */
public final class PingScreenMarker {
    private PingScreenMarker() {
    }

    public static void render(GuiGraphics graphics, int screenWidth, int screenHeight) {
        CSClientData.purgeExpiredPings();
        if (CSClientData.pings.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        var camera = mc.gameRenderer.getMainCamera();
        Vec3 eye = camera.getPosition();
        double fov = mc.options.fov().get();
        double focal = (screenHeight / 2.0D) / Math.tan(Math.toRadians(fov / 2.0D));
        int cx = screenWidth / 2;
        int cy = screenHeight / 2;
        Font font = mc.font;

        // 相机正交基（MC 内部维护，手性正确）：look=前、left=屏幕左、up=屏幕上
        var lookF = camera.getLookVector();
        var leftF = camera.getLeftVector();
        var upF = camera.getUpVector();
        Vec3 look = new Vec3(lookF.x(), lookF.y(), lookF.z());
        Vec3 left = new Vec3(leftF.x(), leftF.y(), leftF.z());
        Vec3 up = new Vec3(upF.x(), upF.y(), upF.z());

        for (CSClientData.PingData ping : CSClientData.pings) {
            if (ping.expired()) {
                continue;
            }
            Vec3 to = new Vec3(ping.x() - eye.x, ping.y() - eye.y, ping.z() - eye.z);
            double dist = to.length();
            if (dist < 0.5D) {
                continue;
            }
            // 相机空间分量：前=dF、右=dR（左的反）、上=dU
            double dF = to.dot(look);
            double dR = -to.dot(left);
            double dU = to.dot(up);

            if (dF <= 0.01D) {
                // 在相机正后方：按边缘指示（方向取反，提示转身）
                renderEdgeMarker(graphics, font, cx, cy, screenWidth, screenHeight,
                        -dR, -dU, dist, colorFor(ping.type()), typeLabel(ping.type()));
                continue;
            }
            double sxD = cx + (dR / dF) * focal;
            double syD = cy - (dU / dF) * focal;
            if (sxD >= 0 && sxD <= screenWidth && syD >= 0 && syD <= screenHeight) {
                // 视野内：圆点 + 类型文字（上方）+ 距离（下方）
                int sx = (int) Math.round(sxD);
                int sy = (int) Math.round(syD);
                int color = colorFor(ping.type());
                String label = typeLabel(ping.type());
                graphics.fill(sx - 4, sy - 4, sx + 4, sy + 4, 0xFF000000);
                graphics.fill(sx - 3, sy - 3, sx + 3, sy + 3, color);
                graphics.drawString(font, label, sx - font.width(label) / 2, sy - 11, 0xFFFFFFFF);
                String txt = Math.round(dist) + "m";
                graphics.drawString(font, txt, sx - font.width(txt) / 2, sy + 7, 0xFFFFFFFF);
            } else {
                // 视野外（前方但超出屏幕）：锚定屏幕边缘 + 方向指示
                renderEdgeMarker(graphics, font, cx, cy, screenWidth, screenHeight,
                        dR, dU, dist, colorFor(ping.type()), typeLabel(ping.type()));
            }
        }
    }

    /**
     * 视野外/背后的 Ping：锚定到屏幕边缘（保留方向），画圆点 + 方向短线段 + 类型文字 + 距离。
     */
    private static void renderEdgeMarker(GuiGraphics graphics, Font font,
                                         int cx, int cy, int screenWidth, int screenHeight,
                                         double dirX, double dirY, double dist, int color, String label) {
        int margin = 26;
        double angle = Math.atan2(dirY, dirX); // 屏幕方向角（右 0°，上 +90°）
        double halfW = screenWidth / 2.0D - margin;
        double halfH = screenHeight / 2.0D - margin;
        double absX = Math.abs(dirX);
        double absY = Math.abs(dirY);
        double ex, ey;
        if (absX < 1.0E-6D && absY < 1.0E-6D) {
            return;
        }
        if (absX * halfH >= absY * halfW) {
            ex = Math.signum(dirX) * halfW;
            ey = (dirY / absX) * halfW;
        } else {
            ey = Math.signum(dirY) * halfH;
            ex = (dirX / absY) * halfH;
        }
        int exI = cx + (int) Math.round(ex);
        int eyI = cy - (int) Math.round(ey);

        // 圆点（黑描边 + 彩色）
        graphics.fill(exI - 4, eyI - 4, exI + 4, eyI + 4, 0xFF000000);
        graphics.fill(exI - 3, eyI - 3, exI + 3, eyI + 3, color);
        // 指向目标方向的短线段（屏幕外延伸感）
        for (int i = 1; i <= 3; i++) {
            int lx = exI + (int) (Math.cos(angle) * i * 3.5D);
            int ly = eyI - (int) (Math.sin(angle) * i * 3.5D);
            graphics.fill(lx - 1, ly - 1, lx + 1, ly + 1, color);
        }
        // 类型说明（上方）+ 距离（下方）
        graphics.drawString(font, label, exI - font.width(label) / 2, eyI - 11, 0xFFFFFFFF);
        String txt = Math.round(dist) + "m";
        graphics.drawString(font, txt, exI - font.width(txt) / 2, eyI + 7, 0xFFFFFFFF);
    }

    /** 类型 → 说明文字（CSGO 风格标记说明，走翻译键）。 */
    private static String typeLabel(int type) {
        return net.minecraft.client.resources.language.I18n.get(typeKey(type));
    }

    private static String typeKey(int type) {
        return switch (type) {
            case PingC2SPacket.TYPE_ENEMY -> "blockoffensive.ping.option.enemy";
            case PingC2SPacket.TYPE_DANGER -> "blockoffensive.ping.option.danger";
            case PingC2SPacket.TYPE_ATTACK -> "blockoffensive.ping.option.attack";
            case PingC2SPacket.TYPE_DEFEND -> "blockoffensive.ping.option.defend";
            case PingC2SPacket.TYPE_HELP -> "blockoffensive.ping.option.help";
            default -> "blockoffensive.ping.option.normal";
        };
    }

    private static int colorFor(int type) {
        return switch (type) {
            case PingC2SPacket.TYPE_ENEMY -> 0xFFFF5A5A;
            case PingC2SPacket.TYPE_DANGER -> 0xFFFF9A3C;
            case PingC2SPacket.TYPE_ATTACK -> 0xFF4ADE80;
            case PingC2SPacket.TYPE_DEFEND -> 0xFFFFE14A;
            case PingC2SPacket.TYPE_HELP -> 0xFF3CE0E0;
            default -> 0xFF4FA3FF;
        };
    }
}