package com.ptcrys.blockoffensive.entity;

import com.ptcrys.blockoffensive.BOConfig;
import com.ptcrys.blockoffensive.BlockOffensive;
import com.ptcrys.blockoffensive.item.BOItemRegister;
import com.ptcrys.blockoffensive.map.CSGameMap;
import com.ptcrys.blockoffensive.net.bomb.BombActionS2CPacket;
import com.ptcrys.blockoffensive.net.bomb.BombDemolitionProgressS2CPacket;
import com.ptcrys.blockoffensive.net.spec.BombFuseS2CPacket;
import com.ptcrys.blockoffensive.sound.BOSoundRegister;
import com.ptcrys.blockoffensive.util.BOUtil;
import com.ptcrys.fpsmatch.core.FPSMCore;
import com.ptcrys.fpsmatch.core.entity.BlastBombEntity;
import com.ptcrys.fpsmatch.core.map.BaseMap;
import com.ptcrys.fpsmatch.core.map.BlastBombState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

public class CompositionC4Entity extends BlastBombEntity {
    private static final EntityDataAccessor<Integer> DATA_EXPLOSION_RADIUS = SynchedEntityData.defineId(CompositionC4Entity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_DELETE_TIME = SynchedEntityData.defineId(CompositionC4Entity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_EXPLOSION_INTERACTION = SynchedEntityData.defineId(CompositionC4Entity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_INSTANT_KILL_RADIUS = SynchedEntityData.defineId(CompositionC4Entity.class, EntityDataSerializers.INT);

    private static final int DEFAULT_FUSE_TIME = 800; // 40秒
    private static final int DEFAULT_EXPLOSION_RADIUS = 60;
    private static final int DEFAULT_INSTANT_KILL_RADIUS = 20; // 默认即死范围

    private Player owner;
    @Nullable
    private Player demolisher;
    private boolean deleting = false;
    private int demolitionProgress = 0;
    private int fuse = DEFAULT_FUSE_TIME;
    private BlastBombState state = BlastBombState.TICKING;
    private CSGameMap map;
    private int forcedChunkX;
    private int forcedChunkZ;
    private boolean chunkForced;
    private boolean chunkForceOwned;

    public CompositionC4Entity(EntityType<?> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
        this.blocksBuilding = true;
        this.noCulling = true;
        this.setDeleteTime(0);
        this.setExplosionRadius(DEFAULT_INSTANT_KILL_RADIUS + 31);
        this.setInstantKillRadius(DEFAULT_INSTANT_KILL_RADIUS);
    }

    public CompositionC4Entity(Level pLevel, double pX, double pY, double pZ, Player pOwner, @NotNull CSGameMap map) {
        this(BOEntityRegister.C4.get(), pLevel);
        this.map = map;
        int ikr = map.getC4InstantKillRadius();
        this.setFuse(BOConfig.common.fuseTime.get());
        this.setExplosionRadius(ikr + 31);
        this.setInstantKillRadius(ikr);
        this.setPos(pX, pY, pZ);
        this.owner = pOwner;
        this.map.setBombEntity(this);
    }

    public CompositionC4Entity(Level pLevel, Vec3 pos, Player pOwner, @NotNull CSGameMap map, int fuseTime, int explosionRadius) {
        this(BOEntityRegister.C4.get(), pLevel);
        this.map = map;
        this.setFuse(fuseTime);
        this.setExplosionRadius(explosionRadius + 31);
        this.setInstantKillRadius(explosionRadius);
        this.setPos(pos);
        this.owner = pOwner;
        this.map.setBombEntity(this);
    }

    public CompositionC4Entity(Level pLevel, Vec3 pos, Player pOwner, @NotNull CSGameMap map, int fuseTime, int explosionRadius, Level.ExplosionInteraction explosionInteraction) {
        this(BOEntityRegister.C4.get(), pLevel);
        this.map = map;
        this.setExplosionInteraction(explosionInteraction);
        this.setFuse(fuseTime);
        this.setExplosionRadius(explosionRadius + 31);
        this.setInstantKillRadius(explosionRadius);
        this.setPos(pos);
        this.owner = pOwner;
        this.map.setBombEntity(this);
    }

    // 添加即死半径的设置器和获取器
    public void setInstantKillRadius(int radius) {
        this.entityData.set(DATA_INSTANT_KILL_RADIUS, radius);
    }

    public int getInstantKillRadius() {
        return this.entityData.get(DATA_INSTANT_KILL_RADIUS);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_EXPLOSION_RADIUS, DEFAULT_EXPLOSION_RADIUS);
        this.entityData.define(DATA_DELETE_TIME, 0);
        this.entityData.define(DATA_EXPLOSION_INTERACTION, Level.ExplosionInteraction.NONE.ordinal());
        this.entityData.define(DATA_INSTANT_KILL_RADIUS, DEFAULT_INSTANT_KILL_RADIUS);
    }

    @Override
    public boolean isAlwaysTicking() {
        return true;
    }

    public boolean isDeleting(){
        return this.deleting;
    }


    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        forceC4Chunk(true);
    }

    @Override
    public void onRemovedFromWorld() {
        super.onRemovedFromWorld();
        forceC4Chunk(false);
        if (!this.level().isClientSide) {
            if(map != null){
                map.setBombEntity(null);
            }
        }
    }

    private void forceC4Chunk(boolean force) {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        if (force && !chunkForced) {
            forcedChunkX = this.blockPosition().getX() >> 4;
            forcedChunkZ = this.blockPosition().getZ() >> 4;
            chunkForceOwned = !serverLevel.getForcedChunks().contains(ChunkPos.asLong(forcedChunkX, forcedChunkZ));
            serverLevel.setChunkForced(forcedChunkX, forcedChunkZ, true);
            chunkForced = true;
        } else if (!force && chunkForced) {
            if (chunkForceOwned) {
                serverLevel.setChunkForced(forcedChunkX, forcedChunkZ, false);
            }
            chunkForced = false;
            chunkForceOwned = false;
        }
    }
    @Override
    protected void readAdditionalSaveData(CompoundTag pCompound) {
        this.setFuse(pCompound.getInt("Fuse"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag pCompound) {
        pCompound.putInt("Fuse", this.getFuse());
    }

    public void tick() {
        if(this.getDeleteTime() >= 140){
            demolitionProgress = 0;
            this.discard();
        }

        if(!this.level().isClientSide){
            if(this.map == null){
                this.discard();
                return;
            }

            if(this.deleting){
                int d = this.getDeleteTime() + 1;
                this.setDeleteTime(d);
                return;
            }

            int i = this.getFuse() - 1;
            this.setFuse(i);
            if(demolisher == null){
                demolitionProgress = 0;
            }else{
                demolitionProgress++;
                if (demolisher instanceof ServerPlayer serverPlayer){
                    if(serverPlayer.gameMode.getGameModeForPlayer() == GameType.SPECTATOR){
                        demolitionProgress = 0;
                        this.demolisher = null;
                    }
                    if(i % 2 == 0 && i > 0){
                        BlockOffensive.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new BombActionS2CPacket());
                    }
                }
            }

            this.syncDemolitionProgress();
            if(demolitionProgress >= getTotalDemolitionProgress()){
                if (demolisher instanceof ServerPlayer serverPlayer) {
                    map.recordBombDefused(serverPlayer);
                }
                this.state = BlastBombState.DEFUSED;
                this.deleting = true;
                this.demolitionProgress = 0;
                this.playDefusedSound();
                return;
            }

            if (i <= 0) {
                if (!this.level().isClientSide) {
                    this.state = BlastBombState.EXPLODED;
                    this.explode();
                }
            }
            if (i > 0 && i % getBeepInterval(i) == 0){
                this.playBeepSound();
            }
            if(i % 20 == 0){
                ((ServerLevel)this.level()).sendParticles(new DustParticleOptions(new Vector3f(1,0.1f,0.1f),1),this.getX(),this.getY() + 0.25,this.getZ(),1,0,0,0,1);
            }

            if(i == 20){
                this.playNvgOnSound();
            }
        }

        if (!this.isNoGravity()) {
            this.setDeltaMovement(0,0,0);
            this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -0.04D, 0.0D)); // 重力
        }
        this.move(MoverType.SELF, this.getDeltaMovement());
    }


    private int getBeepInterval(int fuse) {
        if (fuse <= 20) return 2;
        if (fuse <= 60) return 3;
        if (fuse <= 100) return 5;
        if (fuse <= 200) return 10;
        return 20;
    }

    private float getBeepPitch() {
        float progress = 1.0F - Math.max(0, Math.min(getFuse(), DEFAULT_FUSE_TIME)) / (float) DEFAULT_FUSE_TIME;
        return 0.8F + progress * 0.7F;
    }

    public void playBeepSound(){
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), BOSoundRegister.BEEP.get(), SoundSource.VOICE, 3.0F, getBeepPitch());
    }

    public void playNvgOnSound(){
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), BOSoundRegister.WEAPON_C4_PRE_EXPLODE.get(), SoundSource.VOICE, 6.0F, 1);
    }

    public void playDefusingSound(){
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.VOICE, 6.0F, 1);
    }
    public void playDefusedSound(){
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), BOSoundRegister.DEFUSED.get(), SoundSource.VOICE, 3.0F, 1);
    }

    @Nullable
    public LivingEntity getOwner() {
        return this.owner;
    }

    protected float getEyeHeight(@NotNull Pose pPose, @NotNull EntityDimensions pSize) {
        return 0.15F;
    }

    public void setFuse(int pLife) {
        this.fuse = pLife;
        this.map.getMapTeams().getSpecPlayers().forEach((pUUID)->{
            Optional<ServerPlayer> receiver = FPSMCore.getInstance().getPlayerByUUID(pUUID);
            receiver.ifPresent(player -> BlockOffensive.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new BombFuseS2CPacket(pLife,BOConfig.common.fuseTime.get())));
        });
    }

    public void syncDemolitionProgress(){
        BaseMap map = this.map;
        float progress = this.getDemolitionProgress();
        if(map != null){
            map.getMapTeams().getJoinedPlayers().forEach((data)->{
                data.getPlayer().ifPresent(receiver->{
                    map.getMapTeams().getTeamByPlayer(receiver).ifPresent(team->{
                        boolean flag = this.map.checkCanPlacingBombs(team.getFixedName());
                        if(!flag){
                            BlockOffensive.INSTANCE.send(PacketDistributor.PLAYER.with(() -> receiver), new BombDemolitionProgressS2CPacket(progress));
                        }
                    });
                });
            });

            map.getMapTeams().getSpecPlayers().forEach((pUUID)-> {
                ServerPlayer receiver = (ServerPlayer) this.level().getPlayerByUUID(pUUID);
                if (receiver != null) {
                    BlockOffensive.INSTANCE.send(PacketDistributor.PLAYER.with(() -> receiver), new BombDemolitionProgressS2CPacket(progress));
                }
            });
        }
    }

    public int getFuse() {
        return this.fuse;
    }

    public void setExplosionRadius(int radius){
        this.entityData.set(DATA_EXPLOSION_RADIUS, radius);
    }

    public int getExplosionRadius() {
        return this.entityData.get(DATA_EXPLOSION_RADIUS);
    }

    public int getDeleteTime(){
        return this.entityData.get(DATA_DELETE_TIME);
    }

    public int getTotalDemolitionProgress(){
        int j = 200;
        if(demolisher != null && this.demolisher.getInventory().countItem(BOItemRegister.BOMB_DISPOSAL_KIT.get()) >= 1){
            j = 100;
        }
        return j;
    }

    public float getDemolitionProgress(){
        return (float) this.demolitionProgress / this.getTotalDemolitionProgress();
    }

    public void setDeleteTime(int progress){
        this.entityData.set(DATA_DELETE_TIME, progress);
    }

    public void setDemolisher(@org.jetbrains.annotations.Nullable Player player){
        if(this.demolisher == null) {
            if (player != null && checkDemolisher(player)) {
                this.playDefusingSound();
                this.demolisher = player;
                map.getMapTeams().getTeamByPlayer(player).ifPresent(team->{
                    team.sendMessage(BOUtil.buildTeamChatMessage(player,team, Component.translatable("blockoffensive.demolish.message.c4"),Component.empty(), TextColor.parseColor(team.name.equals("ct") ? "#96C8FA" : "#EAC055")));
                });
            }
        }
    }

    public void resetDemolisher(){
        this.demolisher = null;
    }

    public AABB getR(){
        AABB ab = this.getBoundingBox();
        int r = 3;
        return new AABB(ab.minX - r, ab.minY - r, ab.minZ - r, ab.maxX + r, ab.maxY + r, ab.maxZ + r);
    }
    public boolean checkDemolisher(Player player){
        return this.getR().contains(player.getPosition(0));
    }

    @Nullable
    public LivingEntity getDemolisher() {
        return demolisher;
    }

    public void setExplosionInteraction(Level.ExplosionInteraction explosionInteraction) {
        this.entityData.set(DATA_EXPLOSION_INTERACTION, explosionInteraction.ordinal());
    }

    public Level.ExplosionInteraction explosionInteraction() {
        return Level.ExplosionInteraction.values()[this.entityData.get(DATA_EXPLOSION_INTERACTION)];
    }

    public BlastBombState getState(){
        return state;
    }

    private void explode() {
        // 获取爆炸参数
        int instantKillRadius = this.getInstantKillRadius();
        int damageRadius = instantKillRadius + 31;
        Vec3 explosionPos = this.position();

        // 播放爆炸粒子和音效
        this.playExplosionEffects();

        // 计算伤害范围
        AABB explosionArea = new AABB(
                explosionPos.x - damageRadius, explosionPos.y - damageRadius, explosionPos.z - damageRadius,
                explosionPos.x + damageRadius, explosionPos.y + damageRadius, explosionPos.z + damageRadius
        );

        // 获取范围内的所有实体（玩家和其他实体）
        List<Entity> affectedEntities = this.level().getEntities(this, explosionArea,
                entity -> entity instanceof LivingEntity && entity.isAlive());

        // 对每个实体计算伤害
        for (Entity entity : affectedEntities) {
            if (entity instanceof LivingEntity livingEntity) {
                // 计算距离
                double distance = entity.position().distanceTo(explosionPos);
                if (distance <= instantKillRadius){
                    applyExplosionDamage(livingEntity, 0, 0, 999);
                }else{
                    double blockageFactor = calculateBlockageFactor(explosionPos, entity.position(), distance);

                    applyExplosionDamage(livingEntity, distance, blockageFactor, instantKillRadius);
                }
            }
        }

        this.deleting = true;
        this.demolitionProgress = 0;
        this.state = BlastBombState.EXPLODED;
    }

    private void playExplosionEffects() {
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 4.0F,
                (1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.2F) * 0.7F);

        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                    this.getX(), this.getY(), this.getZ(),
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
            serverLevel.sendParticles(ParticleTypes.EXPLOSION,
                    this.getX(), this.getY(), this.getZ(),
                    10, 2.5, 5, 2.5, 0.15D);
            serverLevel.sendParticles(ParticleTypes.SMOKE,
                    this.getX(), this.getY(), this.getZ(),
                    50, 1.0D, 1.0D, 1.0D, 0.2D);
        }
    }

    private double calculateBlockageFactor(Vec3 startPos, Vec3 endPos, double totalDistance) {
        int instantKillRadius = this.getInstantKillRadius();
        double blockageFactor = 1.0;
        int blockCount = 0;

        Vec3 direction = endPos.subtract(startPos).normalize();
        double stepSize = 0.5;

        for (double i = 0; i < totalDistance; i += stepSize) {
            Vec3 checkPos = startPos.add(direction.scale(i));
            BlockPos blockPos = new BlockPos(
                    (int)Math.floor(checkPos.x),
                    (int)Math.floor(checkPos.y),
                    (int)Math.floor(checkPos.z)
            );

            BlockState blockState = this.level().getBlockState(blockPos);

            if (blockState.isAir() || blockState.getBlock() == Blocks.WATER ||
                    blockState.getBlock() == Blocks.LAVA) {
                continue;
            }

            double reductionPerBlock;

            if (i <= instantKillRadius + 20) {
                if (blockCount % 5 == 0) {
                    reductionPerBlock = 0.1;
                } else {
                    reductionPerBlock = blockCount * 0.02;
                }
            } else if (i <= instantKillRadius + 30) {
                reductionPerBlock = 0.05;
            } else {
                reductionPerBlock = 0.1;
            }

            blockageFactor *= (1.0 - reductionPerBlock);
            blockCount++;
        }

        return Math.max(blockageFactor, 0.0);
    }

    private void applyExplosionDamage(LivingEntity entity, double distance, double blockageFactor, int instantKillRadius) {
        if (distance <= instantKillRadius) {
            entity.hurt(entity.level().damageSources().explosion(this, null), entity.getHealth());
            return;
        }

        if (distance > instantKillRadius + 31) {
            return;
        }

        float maxHealth = entity.getMaxHealth() + 5f;
        float damage;

        if (distance <= instantKillRadius + 10) {
            damage = maxHealth * (float)interpolate(0.9, 1.0,
                    (distance - instantKillRadius) / 10.0);
        } else if (distance <= instantKillRadius + 20) {
            damage = maxHealth * (float)interpolate(0.5, 0.9,
                    (distance - instantKillRadius - 10) / 10.0);
        } else if (distance <= instantKillRadius + 25) {
            damage = maxHealth * (float)interpolate(0.25, 0.5,
                    (distance - instantKillRadius - 20) / 5.0);
        } else {
            damage = maxHealth * (float)interpolate(0.1, 0.25,
                    (distance - instantKillRadius - 25) / 6.0);
        }

        // 应用阻挡因子
        damage *= (float) blockageFactor;

        // 确保最低伤害为1
        damage = Math.max(damage, 1.0f);

        // 应用伤害
        entity.hurt(entity.level().damageSources().explosion(this, this.owner), damage);
    }

    // 线性插值函数
    private double interpolate(double start, double end, double progress) {
        progress = Math.max(0.0, Math.min(1.0, progress)); // 限制在0-1之间
        return start + (end - start) * progress;
    }

    // 改进的AABB计算方法
    public AABB getExplosionArea() {
        int damageRadius = this.getInstantKillRadius() + 31;
        return new AABB(
                this.getX() - damageRadius, this.getY() - damageRadius, this.getZ() - damageRadius,
                this.getX() + damageRadius, this.getY() + damageRadius, this.getZ() + damageRadius
        );
    }
}
