package net.ptcrys.blockoffensive.entity;

import net.ptcrys.blockoffensive.BOConfig;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * 队友 Ping 标记实体（世界内光柱）：
 * <ul>
 * <li>服务端在 Ping 位置生成，TTL 到期（pingTtlSeconds，默认 6 秒）自动消失；</li>
 * <li>同一玩家再次 Ping 时服务端先移除旧标记（handlePing 内替换）；</li>
 * <li>实体通过标准实体通道同步给所有客户端，客户端渲染器按同队过滤绘制光柱。</li>
 * </ul>
 */
public class PingMarkerEntity extends Entity {

    private static final EntityDataAccessor<Integer> DATA_TYPE = SynchedEntityData.defineId(PingMarkerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> DATA_OWNER = SynchedEntityData.defineId(PingMarkerEntity.class, EntityDataSerializers.STRING);

    private int ticksAlive = 0;
    private int ttlTicks = 120; // 默认 6 秒（服务端初始化时按配置覆盖）

    public PingMarkerEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    public PingMarkerEntity(Level level, UUID owner, int pingType) {
        this(BOEntityRegister.PING_MARKER.get(), level);
        this.entityData.set(DATA_TYPE, pingType);
        this.entityData.set(DATA_OWNER, owner.toString());
        this.ttlTicks = Math.max(20, BOConfig.common.pingTtlSeconds.get() * 20);
    }

    public int pingType() {
        return this.entityData.get(DATA_TYPE);
    }

    public UUID ownerId() {
        String s = this.entityData.get(DATA_OWNER);
        try {
            return s == null || s.isEmpty() ? null : UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_TYPE, 0);
        this.entityData.define(DATA_OWNER, "");
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide) {
            if (++this.ticksAlive >= this.ttlTicks) {
                this.discard();
            }
            return;
        }
        // 客户端：在 Ping 位置生成彩色粒子光柱（MC 标准粒子渲染，锚定方块位置，绝对可见）
        // 仅同队玩家可见（敌队看不到，防信息泄露）
        if (!isTeammateToLocalPlayer()) {
            return;
        }
        int color = colorFor(this.entityData.get(DATA_TYPE));
        float r = ((color >> 16) & 0xFF) / 255.0F;
        float g = ((color >> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;
        if (this.tickCount % 3 == 0) {
            // 垂直光柱粒子
            double h = this.level().random.nextDouble() * 3.2D;
            this.level().addParticle(new net.minecraft.core.particles.DustParticleOptions(
                    new org.joml.Vector3f(r, g, b), 1.1F),
                    this.getX() + (this.level().random.nextDouble() - 0.5D) * 0.5D,
                    this.getY() + h,
                    this.getZ() + (this.level().random.nextDouble() - 0.5D) * 0.5D,
                    0.0D, 0.0D, 0.0D);
        }
        if (this.tickCount % 8 == 0) {
            // 顶部圆环粒子（更亮更大）
            double ang = this.level().random.nextDouble() * Math.PI * 2.0D;
            double ring = 0.55D;
            this.level().addParticle(new net.minecraft.core.particles.DustParticleOptions(
                    new org.joml.Vector3f(r, g, b), 1.6F),
                    this.getX() + Math.cos(ang) * ring,
                    this.getY() + 3.2D,
                    this.getZ() + Math.sin(ang) * ring,
                    0.0D, 0.0D, 0.0D);
        }
    }

    private static int colorFor(int type) {
        return switch (type) {
            case 1 -> 0xFFFF5A5A; // 敌人 红
            case 2 -> 0xFFFF9A3C; // 危险 橙
            case 3 -> 0xFF4ADE80; // 进攻 绿
            case 4 -> 0xFFFFE14A; // 防守 黄
            case 5 -> 0xFF3CE0E0; // 支援 青
            default -> 0xFF4FA3FF; // 普通 蓝
        };
    }

    /** 本地玩家与 Ping 所有者是否同队（客户端，仅同队可见粒子）。 */
    private boolean isTeammateToLocalPlayer() {
        try {
            net.minecraft.client.player.LocalPlayer local = net.minecraft.client.Minecraft.getInstance().player;
            if (local == null) {
                return false;
            }
            UUID owner = ownerId();
            if (owner == null) {
                return false;
            }
            if (local.getUUID().equals(owner)) {
                return true;
            }
            var localTeam = net.ptcrys.fpsmatch.common.client.FPSMClient.getGlobalData().getTeamByUUID(local.getUUID());
            var ownerTeam = net.ptcrys.fpsmatch.common.client.FPSMClient.getGlobalData().getTeamByUUID(owner);
            if (localTeam.isPresent() && ownerTeam.isPresent()) {
                String lt = localTeam.get().getName().trim().toLowerCase(java.util.Locale.ROOT);
                String ot = ownerTeam.get().getName().trim().toLowerCase(java.util.Locale.ROOT);
                if ("spectator".equals(lt) || "spectator".equals(ot)) {
                    return false;
                }
                return lt.equals(ot);
            }
        } catch (Throwable ignored) {
            return false;
        }
        // 队伍数据不可用时保守隐藏，避免客户端状态不完整导致敌方看到标记。
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSqr) {
        double maxDistance = BOConfig.common.pingMaxDistance.get();
        return distanceSqr < maxDistance * maxDistance;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("PingType")) this.entityData.set(DATA_TYPE, tag.getInt("PingType"));
        if (tag.contains("PingOwner")) this.entityData.set(DATA_OWNER, tag.getString("PingOwner"));
        this.ticksAlive = tag.getInt("TicksAlive");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("PingType", this.entityData.get(DATA_TYPE));
        tag.putString("PingOwner", this.entityData.get(DATA_OWNER));
        tag.putInt("TicksAlive", this.ticksAlive);
    }

    @Override
    public @NotNull EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.scalable(0.01F, 0.01F);
    }
}
