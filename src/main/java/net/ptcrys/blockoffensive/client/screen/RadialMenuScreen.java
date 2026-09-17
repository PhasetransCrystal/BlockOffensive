package net.ptcrys.blockoffensive.client.screen;

import net.ptcrys.blockoffensive.BlockOffensive;
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
 * 鼠标悬停高亮，左键点击后向准星指向位置发送 Ping（PingC2SPacket），
 * 服务端广播给同队，队友在世界内看到光柱标记（5~8 秒后消失，同玩家新标替换旧标）。
 */
public class RadialMenuScreen extends Screen {

    private static final int SLOT_COUNT = 6;
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
        int radius = 64;
        int btn = 36;
        var font = Minecraft.getInstance().font;

        for (int i = 0; i < SLOT_COUNT; i++) {
            double ang = Math.toRadians(-90.0D + i * (360.0D / SLOT_COUNT));
            int x = cx + (int) (Math.cos(ang) * radius) - btn / 2;
            int y = cy + (int) (Math.sin(ang) * radius) - btn / 2;
            boolean over = mouseX >= x && mouseX <= x + btn && mouseY >= y && mouseY <= y + btn;
            int bg = ((over ? 0x88 : 0x4D) << 24) | (COLORS[i] & 0xFFFFFF);
            graphics.fill(x, y, x + btn, y + btn, bg);
            // 边框
            graphics.fill(x, y, x + btn, y + 1, 0x99FFFFFF);
            graphics.fill(x, y + btn - 1, x + btn, y + btn, 0x99FFFFFF);
            graphics.fill(x, y, x + 1, y + btn, 0x99FFFFFF);
            graphics.fill(x + btn - 1, y, x + btn, y + btn, 0x99FFFFFF);
            // 标签（走翻译）
            String label = Component.translatable(LABEL_KEYS[i]).getString();
            int tw = font.width(label);
            graphics.drawString(font, label,
                    cx + (int) (Math.cos(ang) * radius) - tw / 2,
                    cy + (int) (Math.sin(ang) * radius) + btn / 2 + 4, 0xFFFFFFFF);
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
     * CSGO 式交互：按住 Z 打开轮盘后，移动鼠标指向方向，<b>松开 Z</b> 即发送所选标点。
     * 鼠标不在任何选项上（快速点按）则关闭不发送。
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
        int radius = 64;
        int btn = 36;
        for (int i = 0; i < SLOT_COUNT; i++) {
            double ang = Math.toRadians(-90.0D + i * (360.0D / SLOT_COUNT));
            int x = cx + (int) (Math.cos(ang) * radius) - btn / 2;
            int y = cy + (int) (Math.sin(ang) * radius) - btn / 2;
            if (mouseX >= x && mouseX <= x + btn && mouseY >= y && mouseY <= y + btn) {
                return i;
            }
        }
        return -1;
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
