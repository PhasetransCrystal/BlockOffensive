package com.ptcrys.blockoffensive.compat;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.ptcrys.fpsmatch.FPSMatch;
import net.diebuddies.config.ConfigMobs;
import net.diebuddies.math.AABBf;
import net.diebuddies.physics.*;
import net.diebuddies.physics.ragdoll.BreakableRagdoll;
import net.diebuddies.physics.ragdoll.Ragdoll;
import net.diebuddies.physics.ragdoll.RagdollHook;
import net.diebuddies.physics.ragdoll.RagdollMapper;
import net.diebuddies.physics.settings.mobs.MobPhysicsType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import physx.common.PxVec3;
import physx.physics.PxRigidDynamic;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@OnlyIn(Dist.CLIENT)
public class PhysicsModCompat {

    private static final int RAGDOLL_RETRY_TICKS = 60;
    private static final Map<Integer, Integer> PENDING_DEATHS = new ConcurrentHashMap<>();
    private static final ProxiedRagdollRegistry PROXIED_RAGDOLLS = new ProxiedRagdollRegistry();

    public static void init() {
        RagdollMapper.addHook(BORagdollHook.INSTANCE);
        MinecraftForge.EVENT_BUS.register(BORagdollHook.class);
    }

    public static void requestHandleDead(int entityId) {
        if (!beginProxiedRagdoll(entityId)) {
            debugRagdoll("skip duplicate request entity={}", entityId);
            return;
        }
        debugRagdoll("request entity={} retries={}", entityId, RAGDOLL_RETRY_TICKS);
        PENDING_DEATHS.put(entityId, RAGDOLL_RETRY_TICKS);
        tryHandleDead(entityId);
    }

    static boolean beginProxiedRagdoll(int entityId) {
        return PROXIED_RAGDOLLS.begin(entityId);
    }

    static void clearProxiedRagdoll(int entityId) {
        PROXIED_RAGDOLLS.clear(entityId);
    }

    private static void debugRagdoll(String message, Object... args) {
        if (!FMLEnvironment.production) {
            FPSMatch.LOGGER.info("[BO_PHYSICS_RAGDOLL] " + message, args);
        }
    }

    private static boolean tryHandleDead(int entityId) {
        try {
            PhysicsDeathProxyGuard.setActive(true);
            return handleDead(entityId);
        } finally {
            PhysicsDeathProxyGuard.setActive(false);
        }
    }

    /**
     * Copy from {@link PhysicsMod#blockifyEntity}
     * */
    public static boolean handleDead(int EntityId) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return false;
        Entity entity = level.getEntity(EntityId);
        if (entity == null) return false;

        if (entity instanceof LivingEntity && RenderSystem.isOnRenderThread() && ConfigMobs.getMobSetting(entity).getType() != MobPhysicsType.OFF) {
            PhysicsMod mod = PhysicsMod.getInstance(level);
            if (ConfigMobs.getMobSetting(entity).getType() != MobPhysicsType.OFF) {
                if (mod.alreadyBlockified.contains(entity.getId())) {
                    if (!(entity instanceof Player) || hasUsableMappedPlayerRagdoll(entity, EntityId)) {
                        PENDING_DEATHS.remove(EntityId);
                        debugRagdoll("already blockified entity={}", EntityId);
                        return true;
                    }
                    mod.alreadyBlockified.remove(entity.getId());
                    debugRagdoll("retry stale already-blockified player entity={}", EntityId);
                    return false;
                }

                if (!mod.alreadyBlockified.contains(entity.getId())) {
                    boolean wasInvisible = entity.isInvisible();
                    boolean corpseGenerated = false;
                    int blockifiedPartCount = 0;
                    Ragdoll createdRagdoll = null;
                    if (wasInvisible) {
                        debugRagdoll("temporarily render invisible entity={}", EntityId);
                        entity.setInvisible(false);
                    }
                    try {
                        mod.alreadyBlockified.add(entity.getId());
                        EntityRenderer entityRenderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
                        EntityRenderer renderer = entityRenderer;
                        EntityModel model = null;
                        if (entityRenderer instanceof LivingEntityRenderer) {
                            model = ((LivingEntityRenderer) entityRenderer).getModel();
                        }

                        PoseStack stack = new PoseStack();
                        stack.pushPose();
                        mod.blockify = true;
                        mod.localPivotMatrix = new PoseStack();
                        mod.cubifyEntityRenderer = entityRenderer;
                        mod.cubifyEntity = entity;
                        PhysicsMod.setCurrentInstance(mod);
                        TextureManager textureManager = Minecraft.getInstance().getTextureManager();
                        mod.blockifyTexture = textureManager.getTexture(entityRenderer.getTextureLocation(entity));
                        mod.blockifyEntity = entity;
                        mod.blockifyFeature = null;
                        mod.blockifyFeatureIndex = 0;
                        DummyMultiBufferSource source = new DummyMultiBufferSource();

                        try {
                            renderer.render(entity, 0.0F, Minecraft.getInstance().getFrameTime(), stack, source, 0);
                        } catch (Exception e) {
                            System.err.println("error rendering " + entity.getClass());
                            e.printStackTrace();
                        } finally {
                            if (source.lastLayer != null) {
                                source.lastLayer.clearRenderState();
                            }

                        }

                        PhysicsMod.setCurrentInstance(null);
                        mod.blockify = false;

                        try {
                            RagdollMapper.filterCuboidsFromEntities(entity, model);
                        } catch (Exception e) {
                            System.err.println("error filtering " + entity.getClass());
                            e.printStackTrace();
                        }

                        stack.popPose();

                        for (PhysicsEntity physicsEntity : mod.blockifiedEntity) {
                            physicsEntity.backfaceCulling = false;
                        }

                        MobPhysicsType type = ConfigMobs.getMobSetting(entity).getType();
                        blockifiedPartCount = mod.blockifiedEntity.size();
                        if (type != MobPhysicsType.RAGDOLL && type != MobPhysicsType.RAGDOLL_BREAK && type != MobPhysicsType.RAGDOLL_BREAK_BLOOD) {
                            mod.entityBlocks.addAll(mod.blockifiedEntity);
                            corpseGenerated = blockifiedPartCount > 0;
                        } else {
                            Ragdoll ragdoll = null;

                            try {
                                ragdoll = RagdollMapper.map(type, entity, model);
                            } catch (Exception e) {
                                System.err.println("error creating ragdoll for " + entity.getClass());
                                e.printStackTrace();
                            }

                            createdRagdoll = ragdoll;
                            if (ragdoll == null) {
                                mod.entityBlocks.addAll(mod.blockifiedEntity);
                                corpseGenerated = blockifiedPartCount > 0;
                            } else {
                                mod.ragdolls.add(ragdoll);
                                if (level instanceof ClientLevel) {
                                    ClientLevel clientLevel = (ClientLevel) level;
                                    Player closest = clientLevel.getNearestPlayer(entity.getX(), entity.getY(), entity.getZ(), (double) 8.0F, false);
                                    if (closest != null) {
                                        ragdoll.velocity.set(entity.getX() - closest.getX(), (double) 2.0F, entity.getZ() - closest.getZ()).normalize().mul((double) 5.0F);
                                    }
                                }

                                ragdoll.velocity.add(entity.getDeltaMovement().x * (double) 10.0F, entity.getDeltaMovement().y * (double) 10.0F, entity.getDeltaMovement().z * (double) 10.0F);
                                corpseGenerated = isGeneratedRagdollUsable(entity, EntityId, ragdoll);
                            }
                        }

                        mod.blockifiedEntity.clear();
                        RenderSystem.enableBlend();
                        RenderSystem.defaultBlendFunc();
                    } finally {
                        if (wasInvisible) {
                            entity.setInvisible(true);
                        }
                    }

                    if (corpseGenerated) {
                        PENDING_DEATHS.remove(EntityId);
                        debugRagdoll("success entity={} type={} parts={} pos={},{},{}",
                                EntityId, entity.getType(), blockifiedPartCount, entity.getX(), entity.getY(), entity.getZ());
                        return true;
                    }

                    rollbackFailedDeadAttempt(mod, entity, EntityId, createdRagdoll);
                    restoreVisibleEntityAfterFailedProxy(entity);
                    debugRagdoll("retry no corpse entity={} type={} parts={} pos={},{},{}",
                            EntityId, entity.getType(), blockifiedPartCount, entity.getX(), entity.getY(), entity.getZ());
                    return false;
                }
            }
        }
        return false;
    }

    private static boolean isGeneratedRagdollUsable(Entity entity, int entityId, Ragdoll ragdoll) {
        if (entity instanceof Player) {
            return hasMappedPlayerRagdoll(entity, entityId, ragdoll);
        }
        return isUsableRagdoll(ragdoll);
    }

    private static boolean hasUsableMappedPlayerRagdoll(Entity entity, int entityId) {
        if (!(entity instanceof Player player)) {
            return false;
        }
        BORagdollHook.HookData data = BORagdollHook.RAGDOLL_MAP.get(player.getUUID());
        return data != null
                && data.getEntityId() == entityId
                && isUsableRagdoll(data.getRagdoll());
    }

    private static boolean hasMappedPlayerRagdoll(Entity entity, int entityId, Ragdoll ragdoll) {
        if (!(entity instanceof Player player)) {
            return false;
        }
        BORagdollHook.HookData data = BORagdollHook.RAGDOLL_MAP.get(player.getUUID());
        return data != null
                && data.getEntityId() == entityId
                && data.getRagdoll() == ragdoll;
    }

    private static boolean isUsableRagdoll(Ragdoll ragdoll) {
        return ragdoll != null;
    }

    private static void rollbackFailedDeadAttempt(PhysicsMod mod, Entity entity, int entityId, Ragdoll createdRagdoll) {
        mod.alreadyBlockified.remove(entity.getId());
        if (createdRagdoll == null) {
            removeUnmappedPlayerRagdoll(entity, entityId);
        }
    }

    private static void restoreVisibleEntityAfterFailedProxy(Entity entity) {
        if (entity instanceof LivingEntity) {
            entity.setInvisible(false);
        }
    }

    private static void removeUnmappedPlayerRagdoll(Entity entity, int entityId) {
        if (!(entity instanceof Player player)) return;
        BORagdollHook.HookData data = BORagdollHook.RAGDOLL_MAP.get(player.getUUID());
        if (data == null || data.getEntityId() != entityId) return;
        BORagdollHook.RAGDOLL_MAP.remove(player.getUUID());
    }

    public static void frozenAll() {
        for (PhysicsMod physicsMod : PhysicsMod.instances.values()) {
            PhysicsWorld world = physicsMod.getPhysicsWorld();
            for (Ragdoll ragdoll : world.getRagdolls()) {
                ragdoll.setFrozen(true);
            }
        }
    }

    public static void reset() {
        for (PhysicsMod physicsMod : PhysicsMod.instances.values()) {
            PhysicsWorld world = physicsMod.getPhysicsWorld();
            for (Ragdoll ragdoll : world.getRagdolls()) {
                world.removeRagdoll(ragdoll);
            }
        }
        BORagdollHook.RAGDOLL_MAP.clear();
        PROXIED_RAGDOLLS.clearAll();
    }

    public static void remove(Ragdoll ragdoll) {
        for (PhysicsMod physicsMod : PhysicsMod.instances.values()) {
            PhysicsWorld world = physicsMod.getPhysicsWorld();
            world.removeRagdoll(ragdoll);
        }
    }

    public static class BORagdollHook implements RagdollHook {

        public static final Map<UUID, HookData> RAGDOLL_MAP = new ConcurrentHashMap<>();

        public static final BORagdollHook INSTANCE = new BORagdollHook();

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                INSTANCE.tick();
                for (Map.Entry<Integer, Integer> entry : PENDING_DEATHS.entrySet()) {
                    int entityId = entry.getKey();
                    int retries = entry.getValue();
                    if (tryHandleDead(entityId)) {
                        PENDING_DEATHS.remove(entityId);
                    } else if (retries <= 1) {
                        PENDING_DEATHS.remove(entityId);
                        clearProxiedRagdoll(entityId);
                        debugRagdoll("expired entity={}", entityId);
                    } else {
                        PENDING_DEATHS.put(entityId, retries - 1);
                    }
                }
            }
        }

        @Override
        public void map(Ragdoll ragdoll, Entity entity, EntityModel entityModel) {
            if (entity instanceof Player player) {
                HookData old = RAGDOLL_MAP.remove(player.getUUID());
                if (old != null) {
                    PhysicsModCompat.remove(old.getRagdoll());
                }

                RAGDOLL_MAP.put(player.getUUID(), new HookData(ragdoll, entity.getId()));
            }
        }

        public void remove(UUID uuid) {
            HookData data = RAGDOLL_MAP.get(uuid);
            if (data != null) {
                PhysicsModCompat.remove(data.getRagdoll());
                clearProxiedRagdoll(data.getEntityId());
            }
            del(uuid);
        }

        private void del(UUID uuid) {
            RAGDOLL_MAP.remove(uuid);
        }

        public void tick() {
            if (RAGDOLL_MAP.isEmpty()) return;

            for (Map.Entry<UUID, HookData> entry : RAGDOLL_MAP.entrySet()) {
                HookData data = entry.getValue();
                Ragdoll ragdoll = data.getRagdoll();
                if (ragdoll.btBodies.isEmpty()) {
                    continue;
                }

                if (ragdoll.isFrozen()) {
                    continue;
                }

                data.tick();

                int sleepingCount = 0;
                int totalDynamicBodies = 0;

                for (IRigidBody body : ragdoll.btBodies) {
                    if (body.getRigidBody() instanceof PxRigidDynamic dynamicBody) {
                        totalDynamicBodies++;
                        if (body.hasTransformationChanged()) {
                            if (data.livingTicks >= 20) {
                                Matrix4d current = body.getEntity().getTransformation();
                                Matrix4d old = body.getEntity().getOldTransformation();

                                Vector3d currentPos = new Vector3d();
                                Vector3d oldPos = new Vector3d();
                                current.getTranslation(currentPos);
                                old.getTranslation(oldPos);

                                double positionChange = currentPos.distance(oldPos);

                                if (positionChange < 0.005f) {
                                    sleepingCount++;
                                } else {
                                    sleepingCount = 0;
                                    break;
                                }
                            }
                        } else {
                            if (dynamicBody.isSleeping()) {
                                sleepingCount++;
                            }
                        }
                    }
                }

                if (totalDynamicBodies > 0 && sleepingCount == totalDynamicBodies) {
                    try {
                        ragdoll.setFrozen(true);
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        @Override
        public void filterCuboidsFromEntities(List<PhysicsEntity> list, Entity entity, EntityModel entityModel) {

        }

        public static class HookData {
            public int livingTicks = 0;
            public final Ragdoll ragdoll;
            public final int entityId;


            public HookData(Ragdoll ragdoll, int entityId) {
                this.ragdoll = ragdoll;
                this.entityId = entityId;
            }

            public void tick() {
                livingTicks++;
            }

            public int getLivingTicks() {
                return livingTicks;
            }

            public Ragdoll getRagdoll() {
                return ragdoll;
            }

            public int getEntityId() {
                return entityId;
            }
        }
    }

}
