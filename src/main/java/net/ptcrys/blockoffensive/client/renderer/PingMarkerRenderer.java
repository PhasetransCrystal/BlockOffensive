package net.ptcrys.blockoffensive.client.renderer;

import net.ptcrys.blockoffensive.entity.PingMarkerEntity;
import net.ptcrys.blockoffensive.net.ping.PingC2SPacket;
import net.ptcrys.fpsmatch.common.client.FPSMClient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import java.util.Locale;
import java.util.UUID;

/**
 * Ping 标记实体渲染器：在实体位置绘制 CSGO 风格垂直光柱 + 顶部圆环。
 * <p>
 * 在实体渲染通道（LevelRenderer 实体循环）内直接 Tesselator 手绘，此时
 * PoseStack 已定位到实体原点，因此顶点使用局部坐标；关闭深度测试实现
 * 透墙可见（CSGO ping 风格）。
 * 仅对<b>同队玩家</b>渲染（敌队看不到，防信息泄露）。
 * 颜色按类型区分（普通=蓝 / 敌人=红 / 危险=橙 / 进攻=绿 / 防守=黄 / 支援=青）。
 */
@OnlyIn(Dist.CLIENT)
public class PingMarkerRenderer extends EntityRenderer<PingMarkerEntity> {

    private static final double BEAM_HEIGHT = 3.2D;
    private static final double BEAM_HALF_WIDTH = 0.35D;
    private static final double RING_HALF = 0.55D;
    private static final float BEAM_ALPHA = 0.85F;

    public PingMarkerRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public net.minecraft.resources.ResourceLocation getTextureLocation(PingMarkerEntity entity) {
        return null; // 自定义世界渲染，不使用纹理
    }

    @Override
    public void render(PingMarkerEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        // 同队过滤：敌队/观战者不渲染（防信息泄露）
        boolean teammate = isTeammate(mc.player, entity.ownerId());
        // 诊断日志不应在每个渲染帧路径中输出。
        if (!teammate) {
            return;
        }

        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        int color = colorFor(entity.pingType());
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest(); // 光柱透墙可见（CSGO ping 风格）
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        renderBeam(buffer, cam.x - entity.getX(), cam.z - entity.getZ(), color);

        tesselator.end();

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void renderBeam(BufferBuilder buffer, double cameraX, double cameraZ, int color) {
        float r = ((color >> 16) & 0xFF) / 255.0F;
        float g = ((color >> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;

        double top = BEAM_HEIGHT;
        // EntityRenderDispatcher 已将 PoseStack 平移到实体原点，顶点必须使用局部坐标。
        double len = Math.sqrt(cameraX * cameraX + cameraZ * cameraZ);
        double ux = len > 1.0E-4D ? cameraX / len : 1.0D;
        double uz = len > 1.0E-4D ? cameraZ / len : 0.0D;

        quad(buffer, -ux * BEAM_HALF_WIDTH, 0, -uz * BEAM_HALF_WIDTH,
                ux * BEAM_HALF_WIDTH, 0, uz * BEAM_HALF_WIDTH,
                ux * BEAM_HALF_WIDTH, top, uz * BEAM_HALF_WIDTH,
                -ux * BEAM_HALF_WIDTH, top, -uz * BEAM_HALF_WIDTH, r, g, b, BEAM_ALPHA);
        quad(buffer, -uz * BEAM_HALF_WIDTH, 0, ux * BEAM_HALF_WIDTH,
                uz * BEAM_HALF_WIDTH, 0, -ux * BEAM_HALF_WIDTH,
                uz * BEAM_HALF_WIDTH, top, -ux * BEAM_HALF_WIDTH,
                -uz * BEAM_HALF_WIDTH, top, ux * BEAM_HALF_WIDTH, r, g, b, BEAM_ALPHA * 0.6F);

        double ringHalf = RING_HALF;
        quad(buffer, -ringHalf, top, -ringHalf,
                ringHalf, top, -ringHalf,
                ringHalf, top, ringHalf,
                -ringHalf, top, ringHalf, r, g, b, 0.95F);
        quad(buffer, -ringHalf, top, ringHalf,
                ringHalf, top, ringHalf,
                ringHalf, top, -ringHalf,
                -ringHalf, top, -ringHalf, r, g, b, 0.95F);
    }

    private static void quad(BufferBuilder buffer,
                             double x1, double y1, double z1,
                             double x2, double y2, double z2,
                             double x3, double y3, double z3,
                             double x4, double y4, double z4,
                             float r, float g, float b, float a) {
        buffer.vertex(x1, y1, z1).color(r, g, b, a).endVertex();
        buffer.vertex(x2, y2, z2).color(r, g, b, a).endVertex();
        buffer.vertex(x3, y3, z3).color(r, g, b, a).endVertex();
        buffer.vertex(x4, y4, z4).color(r, g, b, a).endVertex();
    }

    /** 本地玩家与 Ping 所有者是否同队（客户端队伍判定）。数据不足时拒绝渲染。 */
    private static boolean isTeammate(Player local, UUID ownerId) {
        if (ownerId == null) {
            return false;
        }
        if (local.getUUID().equals(ownerId)) {
            return true;
        }
        try {
            var localTeam = FPSMClient.getGlobalData().getTeamByUUID(local.getUUID());
            var ownerTeam = FPSMClient.getGlobalData().getTeamByUUID(ownerId);
            if (localTeam.isPresent() && ownerTeam.isPresent()) {
                String lt = localTeam.get().getName().trim().toLowerCase(Locale.ROOT);
                String ot = ownerTeam.get().getName().trim().toLowerCase(Locale.ROOT);
                if ("spectator".equals(lt) || "spectator".equals(ot)) {
                    return false;
                }
                return lt.equals(ot);
            }
        } catch (Throwable ignored) {
            return false;
        }
        // 队伍数据不可用时拒绝渲染，避免敌方因客户端状态缺失看到队友标记。
        return false; // 队伍数据不可用时保守隐藏
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
