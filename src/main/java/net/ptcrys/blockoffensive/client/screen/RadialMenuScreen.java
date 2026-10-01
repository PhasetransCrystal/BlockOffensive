package net.ptcrys.blockoffensive.client.screen;

import net.ptcrys.blockoffensive.BlockOffensive;
import net.ptcrys.blockoffensive.client.key.RadioKey;
import net.ptcrys.blockoffensive.net.ping.PingC2SPacket;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Z 键标记轮盘（CSGO 风格）：
 * <p>
 * 屏幕中央放射状 6 个选项——标记 / 敌人 / 危险 / 进攻 / 防守 / 支援。
 * 长按时按鼠标方向高亮扇区，松开按键后向准星指向位置发送 Ping（PingC2SPacket），
 * 服务端广播给同队，队友在 HUD 上看到目标位置或屏幕边缘提示（到期消失，同玩家新标替换旧标）。
 */
public class RadialMenuScreen extends Screen {

    private static final int SLOT_COUNT = 6;
    private static final int OPTION_RADIUS = 64;
    private static final int BUTTON_SIZE = 36;
    private static final int SELECTION_INNER_RADIUS = 24;
    private static final int SELECTION_OUTER_RADIUS = 106;
    /** 标签翻译键（与 PING_TYPES 一一对应）。 */
    private static final String[] LABEL_KEYS = {
            "blockoffensive.ping.option.normal",
            "blockoffensive.ping.option.enemy",
            "blockoffensive.ping.option.danger",
            "blockoffensive.ping.option.attack",
            "blockoffensive.ping.option.defend",
            "blockoffensive.ping.option.help"
    };
    private static final int[] COLORS = {
            0xFF4FA3FF, 0xFFFF5A5A, 0xFFFF9A3C, 0xFF4ADE80, 0xFFFFE14A, 0xFF3CE0E0
    };
    private static final int[] TYPES = {
            PingC2SPacket.TYPE_NORMAL, PingC2SPacket.TYPE_ENEMY, PingC2SPacket.TYPE_DANGER,
            PingC2SPacket.TYPE_ATTACK, PingC2SPacket.TYPE_DEFEND, PingC2SPacket.TYPE_HELP
    };

    public RadialMenuScreen() {
        super(Component.translatable("blockoffensive.ping.menu.title"));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 半透明背景
        graphics.fillGradient(0, 0, this.width, this.height, 0x55000000, 0x55000000);

        int cx = this.width / 2;
        int cy = this.height / 2;
        var font = Minecraft.getInstance().font;
        int selectedSlot = slotAt(mouseX, mouseY);

        for (int i = 0; i < SLOT_COUNT; i++) {
            double ang = Math.toRadians(-90.0D + i * (360.0D / SLOT_COUNT));
            int x = cx + (int) (Math.cos(ang) * OPTION_RADIUS) - BUTTON_SIZE / 2;
            int y = cy + (int) (Math.sin(ang) * OPTION_RADIUS) - BUTTON_SIZE / 2;
            boolean over = selectedSlot == i;
            int bg = ((over ? 0xCC : 0x55) << 24) | (COLORS[i] & 0xFFFFFF);
            graphics.fill(x, y, x + BUTTON_SIZE, y + BUTTON_SIZE, bg);
            // 边框
            int border = over ? 0xFFFFFFFF : 0x99FFFFFF;
            graphics.fill(x, y, x + BUTTON_SIZE, y + 1, border);
            graphics.fill(x, y + BUTTON_SIZE - 1, x + BUTTON_SIZE, y + BUTTON_SIZE, border);
            graphics.fill(x, y, x + 1, y + BUTTON_SIZE, border);
            graphics.fill(x + BUTTON_SIZE - 1, y, x + BUTTON_SIZE, y + BUTTON_SIZE, border);
            // 标签（走翻译）
            String label = Component.translatable(LABEL_KEYS[i]).getString();
            int tw = font.width(label);
            graphics.drawString(font, label,
                    cx + (int) (Math.cos(ang) * OPTION_RADIUS) - tw / 2,
                    cy + (int) (Math.sin(ang) * OPTION_RADIUS) + BUTTON_SIZE / 2 + 4, 0xFFFFFFFF);
        }

        String hint = Component.translatable("blockoffensive.ping.menu.hint",
                RadioKey.RADIO_TACTICAL_KEY.getTranslatedKeyMessage()).getString();
        graphics.drawString(font, hint, cx - font.width(hint) / 2, 12, 0xFFFFFFFF);
        if (selectedSlot >= 0) {
            String selected = Component.translatable(LABEL_KEYS[selectedSlot]).getString();
            graphics.drawString(font, selected, cx - font.width(selected) / 2, cy - font.lineHeight / 2,
                    COLORS[selectedSlot]);
        }
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // 以释放瞬间的鼠标位置重新计算选中项（不依赖渲染帧的 hovered）
            int slot = slotAt(mouseX, mouseY);
            if (slot >= 0) {
                fire(slot);
                this.onClose();
                return true;
            }
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /**
     * 长按打开轮盘后，移动鼠标指向方向，松开按键发送所选标记；短按由按键处理器发送普通标记。
     */
    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (net.ptcrys.blockoffensive.client.key.RadioKey.RADIO_TACTICAL_KEY.matches(keyCode, scanCode)) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.mouseHandler != null) {
                double mx = mc.mouseHandler.xpos() / mc.getWindow().getGuiScale();
                double my = mc.mouseHandler.ypos() / mc.getWindow().getGuiScale();
                int slot = slotAt(mx, my);
                if (slot >= 0) {
                    fire(slot);
                }
            }
            this.onClose();
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static int slotAt(double mouseX, double mouseY) {
        Minecraft mc = Minecraft.getInstance();
        int cx = mc.getWindow().getGuiScaledWidth() / 2;
        int cy = mc.getWindow().getGuiScaledHeight() / 2;
        double dx = mouseX - cx;
        double dy = mouseY - cy;
        double distanceSquared = dx * dx + dy * dy;
        if (distanceSquared < SELECTION_INNER_RADIUS * SELECTION_INNER_RADIUS || distanceSquared > SELECTION_OUTER_RADIUS * SELECTION_OUTER_RADIUS) {
            return -1;
        }
        double angle = Math.toDegrees(Math.atan2(dy, dx));
        return (int) ((angle + 480.0D) % 360.0D / (360.0D / SLOT_COUNT));
    }

    public static void sendQuickPing() {
        fire(0);
    }

    private static void fire(int slot) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        try {
            Vec3 target = pickTarget(mc);
            if (target == null) {
                mc.player.displayClientMessage(
                        Component.translatable("blockoffensive.ping.fail"), false);
                return;
            }
            com.mojang.logging.LogUtils.getLogger().info("[PingC2S] slot={} target=({},{},{})",
                    slot, String.format(java.util.Locale.ROOT, "%.2f", target.x),
                    String.format(java.util.Locale.ROOT, "%.2f", target.y),
                    String.format(java.util.Locale.ROOT, "%.2f", target.z));
            BlockOffensive.INSTANCE.sendToServer(new PingC2SPacket(TYPES[slot], target.x, target.y, target.z));
        } catch (Throwable t) {
            com.mojang.logging.LogUtils.getLogger().error("[PingC2S] failed", t);
            mc.player.displayClientMessage(
                    Component.translatable("blockoffensive.ping.fail"), false);
        }
    }

    /**
     * 从摄像机视角（准星中心）发射精确射线：
     * <ol>
     * <li>检测方块碰撞：取命中点 + 沿表面法线微偏移 0.05 格（贴表面、不穿模）；</li>
     * <li>检测实体（更近者优先）：取实体脚底 + 0.2 格（贴近场景，不浮空）；</li>
     * <li>两者都未命中返回 null（调用方提示"无法标点"）。</li>
     * </ol>
     */
    private static Vec3 pickTarget(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return null;
        }
        var camera = mc.gameRenderer.getMainCamera();
        Vec3 eye = camera.getPosition();
        var lookF = camera.getLookVector();
        Vec3 look = new Vec3(lookF.x(), lookF.y(), lookF.z());
        Vec3 end = eye.add(look.scale(200.0D));

        // 1. 方块碰撞
        var blockHit = mc.level.clip(new net.minecraft.world.level.ClipContext(
                eye, end, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        Vec3 blockPoint = null;
        if (blockHit.getType() == HitResult.Type.BLOCK) {
            Vec3 loc = blockHit.getLocation();
            Vec3 normal = Vec3.atLowerCornerOf(
                    ((net.minecraft.world.phys.BlockHitResult) blockHit).getDirection().getNormal());
            blockPoint = loc.add(normal.scale(0.05D));
        }

        // 2. 实体碰撞（可拾取、非观战、非自身）
        net.minecraft.world.phys.AABB area = new net.minecraft.world.phys.AABB(eye, end).inflate(1.0D);
        Entity bestEntity = null;
        double bestEntityDist = Double.MAX_VALUE;
        for (Entity e : mc.level.getEntities(player, area,
                e -> e.isPickable() && !e.isSpectator() && !e.is(player))) {
            var hit = e.getBoundingBox().inflate(0.2D).clip(eye, end);
            if (hit.isPresent()) {
                double d = eye.distanceToSqr(hit.get());
                if (d < bestEntityDist) {
                    bestEntityDist = d;
                    bestEntity = e;
                }
            }
        }

        // 3. 取更近者
        double blockDist = blockPoint != null ? eye.distanceToSqr(blockPoint) : Double.MAX_VALUE;
        if (bestEntity != null && bestEntityDist < blockDist) {
            // 实体位置：脚底 + 0.2 格，贴近场景不浮空
            return new Vec3(bestEntity.getX(), bestEntity.getBoundingBox().minY + 0.2D, bestEntity.getZ());
        }
        return blockPoint; // 可能为 null（未命中）
    }
}
