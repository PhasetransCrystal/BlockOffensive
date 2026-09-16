package com.ptcrys.blockoffensive.spectator;

import com.mojang.logging.LogUtils;
import com.ptcrys.blockoffensive.BlockOffensive;
import com.ptcrys.blockoffensive.entity.CompositionC4Entity;
import com.ptcrys.blockoffensive.item.BOItemRegister;
import com.ptcrys.blockoffensive.net.spec.KillCamS2CPacket;
import com.ptcrys.blockoffensive.net.spec.RequestKillCamFallbackC2SPacket;
import com.ptcrys.blockoffensive.net.spec.SwitchSpectateC2SPacket;
import com.ptcrys.fpsmatch.FPSMatch;
import com.ptcrys.fpsmatch.common.client.spec.SpectateMode;
import com.ptcrys.fpsmatch.common.client.spec.SpectateTarget;
import com.ptcrys.fpsmatch.common.client.spec.SpectatorSwitchDirection;
import com.ptcrys.fpsmatch.common.client.spec.SpectatorSwitchInputEvent;
import com.ptcrys.fpsmatch.common.entity.MatchDropEntity;
import com.ptcrys.fpsmatch.common.packet.spec.SpectatorTargetS2CPacket;
import com.ptcrys.fpsmatch.common.packet.spec.SpectateModeS2CPacket;
import com.ptcrys.fpsmatch.core.FPSMCore;
import com.ptcrys.fpsmatch.core.map.BaseMap;
import com.ptcrys.fpsmatch.core.team.ServerTeam;
import com.ptcrys.fpsmatch.util.FPSMUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BOSpecManager {
    private static final Logger LOG = LogUtils.getLogger();
    private static final float ORBIT_RADIUS = 4.0F;
    private static final long DEDUP_NS = 250_000_000L;
    private static final long KILLCAM_CONTEXT_TTL_TICKS = 200L;
    // 26 ticks of presentation plus time for client delivery/acknowledgement.
    private static final long KILLCAM_WINDOW_TICKS = 80L;
    private static final long KILLCAM_PRESENTATION_TICKS = 26L;
    private static final Map<UUID, SpectateMode> MODES = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> TARGET_ENTITY_IDS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_KILLCAM_NS = new ConcurrentHashMap<>();
    private static final Map<UUID, KillCamDeathContext> DEATH_CONTEXTS = new ConcurrentHashMap<>();
    /** 每个死亡玩家应在该 tick 之后才允许自动接管相机（击杀回放结束）。 */
    private static final Map<UUID, Long> ATTACH_AFTER_TICK = new ConcurrentHashMap<>();

    private BOSpecManager() {
    }

    /** End the old session synchronously; team switches may never tick in adventure mode. */
    public static void resetSpectating(ServerPlayer player) {
        clearSpectatorState(player.getUUID());
        player.setCamera(player);
        FPSMatch.sendToPlayer(player, new SpectateModeS2CPacket(SpectateMode.FREE));
    }

    private static void clearSpectatorState(UUID id) {
        MODES.remove(id);
        TARGET_ENTITY_IDS.remove(id);
        ATTACH_AFTER_TICK.remove(id);
        LAST_KILLCAM_NS.remove(id);
        DEATH_CONTEXTS.remove(id);
        DamagePosTracker.clearDeathPose(id);
    }

    public static void startSpectating(ServerPlayer spectator) {
        // 死亡入口：仅记录死亡位姿，不立即绑定目标。
        // 自动接管由 onPlayerTick 在击杀回放窗口结束后执行；
        // 显式附着请求(击杀回放 FADE 结束)走 requestAttachTeammate 立即接管。
        if (spectator == null || !spectator.isSpectator()) return;
        DamagePosTracker.recordDeathPose(spectator);
    }

    public static void recordKillCamContext(ServerPlayer dead, ServerPlayer killer, BaseMap map) {
        if (dead == null || killer == null || map == null) return;
        DEATH_CONTEXTS.put(dead.getUUID(), new KillCamDeathContext(
                killer.getUUID(), map.getGameType(), map.getMapName(), dead.serverLevel().getGameTime()));
    }

    public static Optional<ServerPlayer> getRecordedKiller(UUID victimId) {
        KillCamDeathContext context = victimId == null ? null : DEATH_CONTEXTS.get(victimId);
        if (context == null) return Optional.empty();
        ServerPlayer victim = FPSMCore.getInstance().getPlayerByUUID(victimId).orElse(null);
        if (victim == null || victim.serverLevel().getGameTime() - context.createdTick() > KILLCAM_CONTEXT_TTL_TICKS) {
            DEATH_CONTEXTS.remove(victimId);
            return Optional.empty();
        }
        return FPSMCore.getInstance().getPlayerByUUID(context.killerId());
    }

    public static boolean matchesRecordedMap(UUID victimId, BaseMap map) {
        KillCamDeathContext context = victimId == null ? null : DEATH_CONTEXTS.get(victimId);
        return context != null && map != null
                && context.gameType().equals(map.getGameType())
                && context.mapName().equals(map.getMapName());
    }

    public static void sendKillCamAndAttach(ServerPlayer dead, DamageSource source) {
        ServerPlayer killer = FPSMUtil.getKiller(dead, source);
        sendKillCamAndAttach(dead, killer == null ? dead : killer, FPSMUtil.getKillerWeapon(source));
    }

    public static void sendKillCamAndAttach(ServerPlayer dead, ServerPlayer killer, ItemStack weapon) {
        if (dead == null || killer == null) return;
        recordKillCamContext(dead, killer, FPSMCore.getInstance().getMapByPlayer(dead).orElse(null));
        Vec3 killerEye = killer.getEyePosition(1.0F);
        Vec3 victimEye = DamagePosTracker.consumeVictimEye(dead).orElseGet(() -> dead.getEyePosition(1.0F));
        sendKillCamAndAttach(dead, killer, weapon, killerEye, victimEye);
    }

    public static void sendKillCamAndAttach(ServerPlayer dead, ServerPlayer killer, ItemStack weapon,
                                            Vec3 killerEye, Vec3 victimEye) {
        if (dead == null || killer == null) return;
        long now = System.nanoTime();
        Long previous = LAST_KILLCAM_NS.put(dead.getUUID(), now);
        if (previous != null && now - previous < DEDUP_NS) return;
        // 击杀回放窗口开启：在窗口结束前，服务端不自动接管该玩家的相机
        DamagePosTracker.recordDeathPose(dead);
        MODES.remove(dead.getUUID());
        TARGET_ENTITY_IDS.remove(dead.getUUID());
        dead.setCamera(dead);
        ATTACH_AFTER_TICK.put(dead.getUUID(), dead.serverLevel().getGameTime() + KILLCAM_WINDOW_TICKS);
        ItemStack copy = weapon == null ? ItemStack.EMPTY : weapon.copy();
        if (!copy.isEmpty()) copy.setCount(1);
        LOG.debug("Sending killcam to {} from {}", dead.getGameProfile().getName(), killer.getGameProfile().getName());
        FPSMatch.sendToPlayer(dead, new KillCamS2CPacket(
                killer.getUUID(), killer.getName().getString(), copy,
                killerEye.x, killerEye.y, killerEye.z,
                victimEye.x, victimEye.y, victimEye.z));
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.side.isClient() || event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer spectator)) return;
        UUID id = spectator.getUUID();
        if (!spectator.isSpectator()) {
            clearSpectatorState(id);
            return;
        }
        // 击杀回放窗口内不自动接管相机，避免附着包抢先触发打断回放
        Long attachAfter = ATTACH_AFTER_TICK.get(id);
        if (attachAfter != null) {
            if (spectator.serverLevel().getGameTime() < attachAfter) {
                return;
            }
            ATTACH_AFTER_TICK.remove(id);
        }
        SpectateMode mode = MODES.get(id);
        if (mode == null) {
            selectAndApplyTarget(spectator);
        } else if (mode == SpectateMode.TEAMMATE && !isCameraOnTeammate(spectator)) {
            selectAndApplyTarget(spectator);
        } else if (mode == SpectateMode.C4_ORBIT && !isCameraOnC4(spectator)) {
            selectAndApplyTarget(spectator);
        }
    }

    @SubscribeEvent
    public static void onSpectatorSwitch(SpectatorSwitchInputEvent event) {
        switchTeammate(event.player(), event.direction() == SpectatorSwitchDirection.NEXT
                ? SwitchSpectateC2SPacket.SwitchDirection.NEXT
                : SwitchSpectateC2SPacket.SwitchDirection.PREV);
    }

    public static void requestAttachTeammate(ServerPlayer spectator) {
        // 显式附着请求(击杀回放 FADE 结束)立即接管，并清除自动接管窗口
        if (spectator == null) return;
        Long deadline = ATTACH_AFTER_TICK.get(spectator.getUUID());
        if (deadline != null && spectator.serverLevel().getGameTime()
                < deadline - KILLCAM_WINDOW_TICKS + KILLCAM_PRESENTATION_TICKS) return;
        ATTACH_AFTER_TICK.remove(spectator.getUUID());
        if (spectator.isSpectator()) {
            selectAndApplyTarget(spectator);
        }
    }

    public static void switchTeammate(ServerPlayer spectator, SwitchSpectateC2SPacket.SwitchDirection direction) {
        if (spectator == null || !spectator.isSpectator()) return;
        if (ATTACH_AFTER_TICK.containsKey(spectator.getUUID())) return;
        Optional<BaseMap> map = FPSMCore.getInstance().getMapByPlayer(spectator);
        if (map.isEmpty()) return;
        ServerTeam team = map.get().getMapTeams().getTeamByPlayer(spectator).orElse(null);
        if (team == null) return;
        List<ServerPlayer> teammates = livingTeammates(spectator, team);
        if (teammates.isEmpty()) return;

        int current = -1;
        if (MODES.get(spectator.getUUID()) == SpectateMode.TEAMMATE) {
            int targetId = TARGET_ENTITY_IDS.getOrDefault(spectator.getUUID(), -1);
            for (int i = 0; i < teammates.size(); ++i) {
                if (teammates.get(i).getId() == targetId) {
                    current = i;
                    break;
                }
            }
        }
        int next;
        if (current < 0) {
            next = direction == SwitchSpectateC2SPacket.SwitchDirection.NEXT
                    ? 0 : teammates.size() - 1;
        } else {
            next = Math.floorMod(current + (direction == SwitchSpectateC2SPacket.SwitchDirection.NEXT ? 1 : -1), teammates.size());
        }
        ServerPlayer teammate = teammates.get(next);
        applyTarget(spectator, new SpectateTarget(SpectateMode.TEAMMATE, teammate.getId(), teammate.position(),
                teammate.getYRot(), teammate.getXRot(), ORBIT_RADIUS));
    }

    private static void selectAndApplyTarget(ServerPlayer spectator) {
        List<SpectateTarget> targets = availableTargets(spectator);
        if (!targets.isEmpty()) applyTarget(spectator, targets.get(0));
    }

    private static List<SpectateTarget> availableTargets(ServerPlayer spectator) {
        // Only death-managed spectators are restricted; manual spectator mode is unaffected.
        if (spectator == null || DamagePosTracker.getDeathPose(spectator).isEmpty()) return List.of();
        Optional<BaseMap> map = FPSMCore.getInstance().getMapByPlayer(spectator);
        if (map.isEmpty()) return List.of();
        List<SpectateTarget> targets = new java.util.ArrayList<>();
        ServerTeam team = map.get().getMapTeams().getTeamByPlayer(spectator).orElse(null);
        if (team != null) {
            for (ServerPlayer teammate : livingTeammates(spectator, team)) {
                targets.add(new SpectateTarget(SpectateMode.TEAMMATE, teammate.getId(), teammate.position(),
                        teammate.getYRot(), teammate.getXRot(), ORBIT_RADIUS));
            }
        }
        Entity c4 = findC4(spectator.serverLevel(), map.get().mapArea.aabb());
        if (c4 != null) {
            targets.add(new SpectateTarget(SpectateMode.C4_ORBIT, c4.getId(), c4.position(),
                    spectator.getYRot(), 0.0F, ORBIT_RADIUS));
        }
        targets.add(new SpectateTarget(SpectateMode.DEATH_SPOT, spectator.getId(),
                DamagePosTracker.getDeathPose(spectator).orElseThrow(),
                DamagePosTracker.getDeathYaw(spectator), DamagePosTracker.getDeathPitch(spectator), ORBIT_RADIUS));
        return targets;
    }

    private static List<ServerPlayer> livingTeammates(ServerPlayer spectator, ServerTeam team) {
        return team.getPlayerList().stream()
                .map(uuid -> spectator.server.getPlayerList().getPlayer(uuid))
                .filter(player -> player != null && player != spectator && player.isAlive() && !player.isSpectator() && player.serverLevel() == spectator.serverLevel())
                .sorted(Comparator.comparing(player -> player.getUUID().toString()))
                .toList();
    }

    private static void applyTarget(ServerPlayer spectator, SpectateTarget target) {
        MODES.put(spectator.getUUID(), target.mode());
        TARGET_ENTITY_IDS.put(spectator.getUUID(), target.entityId());
        if (target.mode() == SpectateMode.TEAMMATE) {
            Entity entity = spectator.serverLevel().getEntity(target.entityId());
            if (entity == null) return;
            spectator.setCamera(entity);
        } else {
            spectator.setCamera(spectator);
        }
        FPSMatch.sendToPlayer(spectator, new SpectatorTargetS2CPacket(
                target.mode(), target.entityId(), target.anchor(), target.yaw(), target.pitch(), target.orbitRadius()));
    }

    private static boolean isCameraOnTeammate(ServerPlayer spectator) {
        Entity camera = spectator.getCamera();
        if (!(camera instanceof ServerPlayer player) || !player.isAlive() || player.isSpectator()) return false;
        Optional<BaseMap> map = FPSMCore.getInstance().getMapByPlayer(spectator);
        return map.isPresent() && map.get().getMapTeams().isSameTeam(spectator, player);
    }

    private static boolean isCameraOnC4(ServerPlayer spectator) {
        Entity target = spectator.serverLevel().getEntity(TARGET_ENTITY_IDS.getOrDefault(spectator.getUUID(), -1));
        if (target == null || target.isRemoved()) return false;
        if (target instanceof CompositionC4Entity) return true;
        if (target instanceof MatchDropEntity drop) return drop.getItem().is(BOItemRegister.C4.get());
        return target instanceof ItemEntity item && item.getItem().is(BOItemRegister.C4.get());
    }

    private static Entity findC4(ServerLevel level, AABB bounds) {
        Entity placed = level.getEntitiesOfClass(CompositionC4Entity.class, bounds).stream()
                .filter(entity -> !entity.isRemoved()).findFirst().orElse(null);
        if (placed != null) return placed;
        Entity matchDrop = level.getEntitiesOfClass(MatchDropEntity.class, bounds).stream()
                .filter(entity -> entity.getItem().is(BOItemRegister.C4.get())).findFirst().orElse(null);
        if (matchDrop != null) return matchDrop;
        return level.getEntitiesOfClass(ItemEntity.class, bounds).stream()
                .filter(entity -> entity.getItem().is(BOItemRegister.C4.get())).findFirst().orElse(null);
    }

    @OnlyIn(Dist.CLIENT)
    public static void requestKillCamFallback(@NotNull UUID killer) {
        BlockOffensive.INSTANCE.sendToServer(new RequestKillCamFallbackC2SPacket(killer));
    }

    private record KillCamDeathContext(UUID killerId, String gameType, String mapName, long createdTick) {
    }

    @OnlyIn(Dist.CLIENT)
    public static void sendSwitchSpectate(SwitchSpectateC2SPacket.SwitchDirection direction) {
        BlockOffensive.INSTANCE.sendToServer(new SwitchSpectateC2SPacket(direction));
    }
}
