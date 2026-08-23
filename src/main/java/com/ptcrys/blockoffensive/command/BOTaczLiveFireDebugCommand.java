package com.ptcrys.blockoffensive.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.ptcrys.blockoffensive.BlockOffensive;
import com.ptcrys.blockoffensive.data.DeathMessage;
import com.ptcrys.blockoffensive.map.CSDeathMatchMap;
import com.ptcrys.fpsmatch.FPSMatch;
import com.ptcrys.fpsmatch.common.entity.throwable.SmokeShellEntity;
import com.ptcrys.fpsmatch.common.event.FPSMGunDamageEvent;
import com.ptcrys.fpsmatch.common.event.FPSMGunKillEvent;
import com.ptcrys.fpsmatch.core.FPSMCore;
import com.ptcrys.fpsmatch.compat.IPassThroughEntity;
import com.ptcrys.fpsmatch.core.data.AreaData;
import com.ptcrys.fpsmatch.core.data.SpawnPointData;
import com.ptcrys.fpsmatch.core.map.DeathContext;
import com.ptcrys.fpsmatch.core.team.MapTeams;
import com.ptcrys.fpsmatch.core.team.ServerTeam;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = BlockOffensive.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BOTaczLiveFireDebugCommand {
    private static final String TEST_MAP_PREFIX = "__bo_tacz_live_fire_";
    private static final ResourceLocation TEST_GUN_ID = ResourceLocation.tryBuild("tacz", "m95");
    private static final int MAX_TEST_TICKS = 400;
    private static final int MAX_SHOT_ATTEMPTS = 24;
    private static final int CLEANUP_DELAY_TICKS = 100;
    private static final Map<UUID, LiveFireTestRun> RUNS = new HashMap<>();

    private BOTaczLiveFireDebugCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(command("bo_tacz_live_fire_test"));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> fpsmCommand() {
        return command("tacz_live_fire_test");
    }

    public static boolean isLiveFireTestMap(String mapName) {
        return mapName != null && mapName.startsWith(TEST_MAP_PREFIX);
    }

    public static void handleDeathContext(String mapName, DeathContext context) {
        RUNS.values().forEach(run -> {
            if (run.matchesMap(mapName)) {
                run.onDeathContext(context);
            }
        });
    }

    public static void handleDeathMessage(String mapName, DeathMessage message) {
        RUNS.values().forEach(run -> {
            if (run.matchesMap(mapName)) {
                run.onDeathMessage(message);
            }
        });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> command(String name) {
        return Commands.literal(name)
                .requires(source -> !FMLEnvironment.production && source.hasPermission(2))
                .executes(BOTaczLiveFireDebugCommand::handle);
    }

    private static int handle(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer victim = source.getPlayerOrException();

        if (FMLEnvironment.production) {
            source.sendFailure(Component.literal("TacZ live-fire debug command is disabled in production."));
            return 0;
        }
        if (!ModList.get().isLoaded("tacz")) {
            source.sendFailure(Component.literal("TacZ is not loaded; cannot run live-fire test."));
            return 0;
        }
        if (!FPSMCore.initialized()) {
            source.sendFailure(Component.literal("FPSMatch core is not initialized."));
            return 0;
        }
        if (TimelessAPI.getCommonGunIndex(TEST_GUN_ID).isEmpty()) {
            source.sendFailure(Component.literal("TacZ gun index is missing: " + TEST_GUN_ID));
            return 0;
        }

        LiveFireTestRun previous = RUNS.remove(victim.getUUID());
        if (previous != null) {
            previous.cleanup("replaced");
        }

        LiveFireTestRun run = new LiveFireTestRun(source, victim);
        if (!run.start()) {
            run.cleanup("start_failed");
            return 0;
        }

        RUNS.put(victim.getUUID(), run);
        source.sendSuccess(() -> Component.literal("Started BO TacZ live-fire test. Watch [BO_TACZ_TEST] in latest.log."), true);
        return 1;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || RUNS.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<UUID, LiveFireTestRun>> iterator = RUNS.entrySet().iterator();
        while (iterator.hasNext()) {
            LiveFireTestRun run = iterator.next().getValue();
            if (run.tick()) {
                iterator.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onGunDamage(FPSMGunDamageEvent event) {
        RUNS.values().forEach(run -> run.onGunDamage(event));
    }

    @SubscribeEvent
    public static void onGunKill(FPSMGunKillEvent event) {
        RUNS.values().forEach(run -> run.onGunKill(event));
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        RUNS.values().forEach(run -> run.onLivingHurt(event));
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        RUNS.values().forEach(run -> run.onLivingDeath(event));
    }

    private static final class LiveFireTestRun {
        private final CommandSourceStack source;
        private final ServerPlayer victim;
        private final ServerLevel level;
        private final Vec3 oldPosition;
        private final float oldYaw;
        private final float oldPitch;
        private final GameType oldGameMode;
        private final InventorySnapshot oldInventory;
        private final Map<BlockPos, BlockState> originalBlocks = new HashMap<>();

        private CSDeathMatchMap map;
        private DamageableFakePlayer shooter;
        private SmokeShellEntity smoke;
        private Vec3 shooterFeet;
        private Vec3 victimFeet;
        private long startTick;
        private int attempts;
        private int cleanupCountdown = -1;
        private boolean shotSucceeded;
        private boolean deathContextSeen;
        private boolean deathMessageSeen;
        private boolean gunDamageSeen;
        private boolean gunKillSeen;
        private boolean cleaned;

        private LiveFireTestRun(CommandSourceStack source, ServerPlayer victim) {
            this.source = source;
            this.victim = victim;
            this.level = victim.serverLevel();
            this.oldPosition = victim.position();
            this.oldYaw = victim.getYRot();
            this.oldPitch = victim.getXRot();
            this.oldGameMode = victim.gameMode.getGameModeForPlayer();
            this.oldInventory = InventorySnapshot.capture(victim);
        }

        private boolean start() {
            try {
                FPSMCore.checkAndLeaveTeam(victim);

                BlockPos origin = victim.blockPosition().above(4);
                this.shooterFeet = new Vec3(origin.getX() + 0.5D, origin.getY(), origin.getZ() - 6.5D);
                this.victimFeet = new Vec3(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 2.5D);

                prepareRange(origin);

                AreaData area = new AreaData(origin.offset(-14, -2, -12), origin.offset(14, 12, 14));
                this.map = new CSDeathMatchMap(level, TEST_MAP_PREFIX + victim.getUUID().toString().substring(0, 8), area);
                this.shooter = new DamageableFakePlayer(level, new GameProfile(UUID.randomUUID(), "BO_TacZ_Shooter"));
                this.shooter.setNoGravity(true);
                this.shooter.setInvulnerable(false);
                this.shooter.setHealth(this.shooter.getMaxHealth());
                moveFakeShooterTo(shooterFeet, 0.0F, 0.0F);
                if (!level.addFreshEntity(shooter)) {
                    fail("failed to add TacZ fake shooter to level");
                    return false;
                }

                addTestSpawnPoints();

                MapTeams.JoinTeamResult shooterJoin = map.join("1", shooter);
                MapTeams.JoinTeamResult victimJoin = map.join("2", victim);
                if (!shooterJoin.isSuccess() || !victimJoin.isSuccess()) {
                    fail("join failed shooter=" + shooterJoin.status() + " victim=" + victimJoin.status());
                    return false;
                }
                if (!map.start()) {
                    fail("test map failed to start");
                    return false;
                }

                teleportShooterToRange();
                teleportVictimToRange(true);
                this.map.handlePlayerMove(victim.getUUID());
                this.map.handlePlayerFire(shooter.getUUID());

                placePassThroughWall();
                spawnSmoke();
                readyTacZOperatorForShot();

                this.startTick = level.getGameTime();
                FPSMatch.LOGGER.info("[BO_TACZ_TEST] started shooter={} victim={} gun={} shooterPos={} victimPos={} victimGameMode={} victimInvulnerableAbilities={}",
                        shooter.getGameProfile().getName(),
                        victim.getGameProfile().getName(),
                        TEST_GUN_ID,
                        shooterFeet,
                        victimFeet,
                        victim.gameMode.getGameModeForPlayer(),
                        victim.getAbilities().invulnerable);
                return true;
            } catch (Exception e) {
                FPSMatch.LOGGER.error("[BO_TACZ_TEST] failed to start", e);
                source.sendFailure(Component.literal("BO TacZ live-fire test failed to start: " + e.getMessage()));
                return false;
            }
        }

        private boolean tick() {
            if (cleaned) {
                return true;
            }

            long elapsed = level.getGameTime() - startTick;
            if (cleanupCountdown >= 0) {
                cleanupCountdown--;
                if (cleanupCountdown <= 0) {
                    cleanup("completed");
                    return true;
                }
                return false;
            }

            if (deathContextSeen && deathMessageSeen) {
                cleanupCountdown = CLEANUP_DELAY_TICKS;
                return false;
            }

            if (elapsed > MAX_TEST_TICKS) {
                fail("timeout shotSucceeded=" + shotSucceeded
                        + " gunDamageSeen=" + gunDamageSeen
                        + " gunKillSeen=" + gunKillSeen
                        + " victimHealth=" + (victim == null ? "null" : victim.getHealth()));
                cleanup("timeout");
                return true;
            }

            if (elapsed < 40 || attempts >= MAX_SHOT_ATTEMPTS || elapsed % 5 != 0) {
                return false;
            }

            attempts++;
            teleportShooterToRange();
            teleportVictimToRange(false);
            aimAtVictimHead();

            IGunOperator operator = readyTacZOperatorForShot();
            long timestamp = System.currentTimeMillis() - operator.getDataHolder().baseTimestamp;
            float pitch = shooter.getXRot();
            float yaw = shooter.getYRot();
            float aimingProgressBeforeShot = operator.getDataHolder().aimingProgress;
            boolean isAimingBeforeShot = operator.getDataHolder().isAiming;
            float sprintTimeBeforeShot = operator.getDataHolder().sprintTimeS;
            float walkDeltaBeforeShot = Math.abs(shooter.walkDist - shooter.walkDistO);
            ShootResult result = operator.shoot(() -> pitch, () -> yaw, timestamp);
            Vec3 shooterEye = shooter.getEyePosition();
            Vec3 targetHead = victimHead();
            FPSMatch.LOGGER.info("[BO_TACZ_TEST] shoot attempt={} result={} timestamp={} pitch={} yaw={} aimingProgressBefore={} isAimingBefore={} sprintTimeBefore={} walkDeltaBefore={} shooterEye={} targetHead={} delta={} victimHealth={}",
                    attempts,
                    result,
                    timestamp,
                    pitch,
                    yaw,
                    aimingProgressBeforeShot,
                    isAimingBeforeShot,
                    sprintTimeBeforeShot,
                    walkDeltaBeforeShot,
                    shooterEye,
                    targetHead,
                    targetHead.subtract(shooterEye),
                    victim.getHealth());
            if (result == ShootResult.SUCCESS) {
                shotSucceeded = true;
            } else if (result == ShootResult.IS_DRAWING && attempts < 8) {
                return false;
            } else if (result == ShootResult.NEED_BOLT) {
                operator.bolt();
            }

            return false;
        }

        private void onGunDamage(FPSMGunDamageEvent event) {
            if (!matches(event.getAttacker(), event.getHurtEntity())) {
                return;
            }
            boolean passWall = event.getBullet() instanceof IPassThroughEntity passThrough
                    && passThrough.fpsmatch$isWall();
            boolean passSmoke = event.getBullet() instanceof IPassThroughEntity passThrough
                    && passThrough.fpsmatch$isSmoke();
            boolean accepted = event.isHeadShot() && passWall && passSmoke;
            if (accepted) {
                gunDamageSeen = true;
            } else {
                event.setBaseAmount(0.0F);
                event.setHeadshotMultiplier(0.0F);
                teleportVictimToRange(true);
            }
            FPSMatch.LOGGER.info("[BO_TACZ_TEST] FPSMGunDamageEvent amount={} headShot={} passWall={} passSmoke={} accepted={} victimHealth={}",
                    event.getBaseAmount(), event.isHeadShot(), passWall, passSmoke, accepted, victim.getHealth());
        }

        private void onGunKill(FPSMGunKillEvent event) {
            if (!matches(event.getAttacker(), event.getKilledEntity())) {
                return;
            }
            gunKillSeen = true;
            boolean passWall = event.getBullet() instanceof IPassThroughEntity passThrough
                    && passThrough.fpsmatch$isWall();
            boolean passSmoke = event.getBullet() instanceof IPassThroughEntity passThrough
                    && passThrough.fpsmatch$isSmoke();
            FPSMatch.LOGGER.info("[BO_TACZ_TEST] FPSMGunKillEvent headShot={} passWall={} passSmoke={} bullet={}",
                    event.isHeadShot(), passWall, passSmoke,
                    event.getBullet() == null ? "null" : event.getBullet().getType().toString());
        }

        private void onLivingHurt(LivingHurtEvent event) {
            if (!event.getEntity().getUUID().equals(victim.getUUID())) {
                return;
            }
            Entity attacker = event.getSource().getEntity();
            Entity direct = event.getSource().getDirectEntity();
            FPSMatch.LOGGER.info("[BO_TACZ_TEST] LivingHurtEvent amount={} canceled={} source={} attacker={} direct={} victimHealthBefore={}",
                    event.getAmount(),
                    event.isCanceled(),
                    event.getSource().getMsgId(),
                    attacker == null ? "null" : attacker.getStringUUID(),
                    direct == null ? "null" : direct.getStringUUID(),
                    victim.getHealth());
        }

        private void onLivingDeath(LivingDeathEvent event) {
            if (!event.getEntity().getUUID().equals(victim.getUUID())) {
                return;
            }
            Entity attacker = event.getSource().getEntity();
            Entity direct = event.getSource().getDirectEntity();
            FPSMatch.LOGGER.info("[BO_TACZ_TEST] LivingDeathEvent canceled={} source={} attacker={} direct={} victimHealth={}",
                    event.isCanceled(),
                    event.getSource().getMsgId(),
                    attacker == null ? "null" : attacker.getStringUUID(),
                    direct == null ? "null" : direct.getStringUUID(),
                    victim.getHealth());
        }

        private void onDeathContext(DeathContext context) {
            if (!context.getDeadPlayer().getUUID().equals(victim.getUUID())) {
                return;
            }
            deathContextSeen = true;
            boolean ok = context.isHeadShot() && context.isPassWall() && context.isPassSmoke();
            FPSMatch.LOGGER.info("[BO_TACZ_TEST] DeathContext gunKill={} headShot={} passWall={} passSmoke={} scopedKill={} deathItem={} ok={}",
                    context.isGunKill(),
                    context.isHeadShot(),
                    context.isPassWall(),
                    context.isPassSmoke(),
                    context.isScopedKill(),
                    context.getDeathItem().getHoverName().getString(),
                    ok);
            if (!ok) {
                source.sendFailure(Component.literal("BO TacZ live-fire test reached DeathContext but flags were incomplete. See latest.log."));
            }
        }

        private void onDeathMessage(DeathMessage message) {
            if (!message.getDeadUUID().equals(victim.getUUID())) {
                return;
            }
            deathMessageSeen = true;
            boolean ok = message.isHeadShot() && message.isThroughWall() && message.isThroughSmoke();
            FPSMatch.LOGGER.info("[BO_TACZ_TEST] DeathMessage headShot={} passWall={} passSmoke={} noScope={} flying={} ok={}",
                    message.isHeadShot(),
                    message.isThroughWall(),
                    message.isThroughSmoke(),
                    message.isNoScope(),
                    message.isFlying(),
                    ok);
            if (ok && deathContextSeen) {
                source.sendSuccess(() -> Component.literal("BO TacZ live-fire test passed: wall + smoke + headshot flags reached DeathMessage packet."), true);
            } else if (!ok) {
                source.sendFailure(Component.literal("BO TacZ live-fire test built DeathMessage without complete wall/smoke/headshot flags. See latest.log."));
            }
        }

        private boolean matchesMap(String mapName) {
            return map != null && map.getMapName().equals(mapName);
        }

        private boolean matches(Entity attacker, Entity hurt) {
            return attacker != null
                    && hurt != null
                    && attacker.getUUID().equals(shooter.getUUID())
                    && victim != null
                    && hurt.getUUID().equals(victim.getUUID());
        }

        private void prepareRange(BlockPos origin) {
            for (int x = -12; x <= 12; x++) {
                for (int z = -10; z <= 12; z++) {
                    rememberAndSet(origin.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState());
                    for (int y = 0; y <= 10; y++) {
                        rememberAndSet(origin.offset(x, y, z), Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }

        private void rememberAndSet(BlockPos pos, BlockState state) {
            originalBlocks.putIfAbsent(pos.immutable(), level.getBlockState(pos));
            level.setBlock(pos, state, 3);
        }

        private void addTestSpawnPoints() {
            for (ServerTeam team : map.getMapTeams().getNormalTeams()) {
                Vec3 pos = "1".equals(team.getName()) ? shooterFeet : victimFeet;
                float yaw = "1".equals(team.getName()) ? 0.0F : 180.0F;
                addSpawnPoint(team, pos, yaw, 0.0F);
            }
        }

        private void addSpawnPoint(String teamName, Vec3 pos, float yaw, float pitch) {
            map.getMapTeams().getTeamByName(teamName).ifPresent(team ->
                    addSpawnPoint(team, pos, yaw, pitch));
        }

        private void addSpawnPoint(ServerTeam team, Vec3 pos, float yaw, float pitch) {
            map.getMapTeams().addSpawnPoint(team, new SpawnPointData(level.dimension(), pos, yaw, pitch));
        }

        private void placePassThroughWall() {
            Vec3 wallPos = shooter.getEyePosition().lerp(victimHead(), 0.52D);
            BlockPos center = BlockPos.containing(wallPos);
            for (int x = -2; x <= 2; x++) {
                for (int y = -2; y <= 2; y++) {
                    rememberAndSet(center.offset(x, y, 0), Blocks.OAK_LEAVES.defaultBlockState());
                }
            }
        }

        private void spawnSmoke() {
            Vec3 smokePos = shooter.getEyePosition().lerp(victimHead(), 0.35D);
            this.smoke = new SmokeShellEntity(shooter, level);
            this.smoke.setPos(smokePos.x, smokePos.y, smokePos.z);
            this.smoke.setParticleCoolDown(0);
            level.addFreshEntity(smoke);
        }

        private void equipTacZGun() {
            ItemStack gun = GunItemBuilder.create()
                    .setId(TEST_GUN_ID)
                    .setAmmoCount(5)
                    .setAmmoInBarrel(true)
                    .setFireMode(FireMode.SEMI)
                    .forceBuild();
            shooter.setGameMode(GameType.ADVENTURE);
            shooter.setItemInHand(InteractionHand.MAIN_HAND, gun);
            shooter.getInventory().setChanged();
        }

        private IGunOperator readyTacZOperatorForShot() {
            equipTacZGun();
            IGunOperator operator = IGunOperator.fromLivingEntity(shooter);
            operator.initialData();
            operator.draw(shooter::getMainHandItem);
            ShooterDataHolder data = operator.getDataHolder();
            long readyTimestamp = System.currentTimeMillis() - 10_000L;
            data.currentGunItem = shooter::getMainHandItem;
            data.drawTimestamp = readyTimestamp;
            data.shootTimestamp = -1L;
            data.lastShootTimestamp = -1L;
            data.boltTimestamp = -1L;
            data.isBolting = false;
            data.reloadTimestamp = -1L;
            data.isCrawling = false;
            steadyTacZAim(operator);
            return operator;
        }

        private void teleportShooterToRange() {
            moveFakeShooterTo(shooterFeet, 0.0F, 0.0F);
            aimAtVictimHead();
            shooter.setOnGround(true);
        }

        private void steadyTacZAim(IGunOperator operator) {
            operator.aim(true);
            ShooterDataHolder data = operator.getDataHolder();
            data.isAiming = true;
            data.aimingProgress = 1.0F;
            data.aimingTimestamp = System.currentTimeMillis();
            data.sprintTimeS = 0.0F;
            data.sprintTimestamp = System.currentTimeMillis();
            shooter.setSprinting(false);
            shooter.walkDistO = shooter.walkDist;
        }

        private void moveFakeShooterTo(Vec3 pos, float yaw, float pitch) {
            shooter.moveTo(pos.x, pos.y, pos.z, yaw, pitch);
            shooter.setPos(pos.x, pos.y, pos.z);
            shooter.xOld = pos.x;
            shooter.yOld = pos.y;
            shooter.zOld = pos.z;
            shooter.setYRot(yaw);
            shooter.setXRot(pitch);
            shooter.setYHeadRot(yaw);
            shooter.setYBodyRot(yaw);
            shooter.setDeltaMovement(Vec3.ZERO);
        }

        private void teleportVictimToRange(boolean resetHealth) {
            victim.teleportTo(level, victimFeet.x, victimFeet.y, victimFeet.z, 180.0F, 0.0F);
            victim.xOld = victimFeet.x;
            victim.yOld = victimFeet.y;
            victim.zOld = victimFeet.z;
            victim.setYRot(180.0F);
            victim.setXRot(0.0F);
            victim.setYHeadRot(180.0F);
            victim.setYBodyRot(180.0F);
            victim.setOnGround(true);
            victim.setDeltaMovement(Vec3.ZERO);
            victim.fallDistance = 0.0F;
            victim.getAbilities().invulnerable = false;
            victim.setInvulnerable(false);
            victim.setGameMode(GameType.ADVENTURE);
            if (resetHealth) {
                victim.setHealth(victim.getMaxHealth());
            }
        }

        private void aimAtVictimHead() {
            Rotation rotation = rotationTo(shooter.getEyePosition(), victimHead());
            shooter.setYRot(rotation.yaw());
            shooter.setXRot(rotation.pitch());
            shooter.setYHeadRot(rotation.yaw());
            shooter.setYBodyRot(rotation.yaw());
        }

        private Vec3 victimHead() {
            return victim.getEyePosition();
        }

        private static Rotation rotationTo(Vec3 from, Vec3 to) {
            Vec3 delta = to.subtract(from);
            double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
            float yaw = (float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0D);
            float pitch = (float) (-Math.toDegrees(Math.atan2(delta.y, horizontal)));
            return new Rotation(yaw, pitch);
        }

        private void fail(String reason) {
            FPSMatch.LOGGER.error("[BO_TACZ_TEST] failed: {}", reason);
            source.sendFailure(Component.literal("BO TacZ live-fire test failed: " + reason));
        }

        private void cleanup(String reason) {
            if (cleaned) {
                return;
            }
            cleaned = true;
            FPSMatch.LOGGER.info("[BO_TACZ_TEST] cleanup reason={}", reason);

            try {
                if (map != null) {
                    map.reset();
                }
            } catch (Exception e) {
                FPSMatch.LOGGER.error("[BO_TACZ_TEST] map cleanup failed", e);
            }
            if (smoke != null) {
                smoke.discard();
            }
            if (shooter != null) {
                shooter.discard();
            }
            originalBlocks.forEach((pos, state) -> level.setBlock(pos, state, 3));
            victim.setHealth(victim.getMaxHealth());
            oldInventory.restore(victim);
            victim.setGameMode(oldGameMode);
            victim.teleportTo(level, oldPosition.x, oldPosition.y, oldPosition.z, oldYaw, oldPitch);
            victim.setYRot(oldYaw);
            victim.setXRot(oldPitch);
            victim.setYHeadRot(oldYaw);
            victim.setYBodyRot(oldYaw);
        }
    }

    private record InventorySnapshot(
            List<ItemStack> items,
            List<ItemStack> armor,
            List<ItemStack> offhand,
            int selected
    ) {
        private static InventorySnapshot capture(ServerPlayer player) {
            return new InventorySnapshot(
                    copy(player.getInventory().items),
                    copy(player.getInventory().armor),
                    copy(player.getInventory().offhand),
                    player.getInventory().selected
            );
        }

        private static List<ItemStack> copy(List<ItemStack> source) {
            List<ItemStack> copy = new ArrayList<>(source.size());
            for (ItemStack stack : source) {
                copy.add(stack.copy());
            }
            return copy;
        }

        private void restore(ServerPlayer player) {
            restore(player.getInventory().items, items);
            restore(player.getInventory().armor, armor);
            restore(player.getInventory().offhand, offhand);
            player.getInventory().selected = selected;
            player.getInventory().setChanged();
        }

        private static void restore(NonNullList<ItemStack> target, List<ItemStack> source) {
            for (int i = 0; i < target.size() && i < source.size(); i++) {
                target.set(i, source.get(i).copy());
            }
        }
    }

    private static final class DamageableFakePlayer extends FakePlayer {
        private DamageableFakePlayer(ServerLevel level, GameProfile name) {
            super(level, name);
        }

        @Override
        public boolean isInvulnerableTo(DamageSource source) {
            return false;
        }

        @Override
        public boolean canHarmPlayer(Player player) {
            return true;
        }
    }

    private record Rotation(float yaw, float pitch) {
    }
}
