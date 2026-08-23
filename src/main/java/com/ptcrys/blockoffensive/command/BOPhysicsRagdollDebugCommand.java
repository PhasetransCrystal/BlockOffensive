package com.ptcrys.blockoffensive.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.ptcrys.blockoffensive.BlockOffensive;
import com.ptcrys.blockoffensive.map.CSGameMap;
import com.ptcrys.fpsmatch.FPSMatch;
import com.ptcrys.fpsmatch.core.FPSMCore;
import com.ptcrys.fpsmatch.core.data.AreaData;
import com.ptcrys.fpsmatch.core.data.SpawnPointData;
import com.ptcrys.fpsmatch.core.team.MapTeams;
import com.ptcrys.fpsmatch.core.team.ServerTeam;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
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
public final class BOPhysicsRagdollDebugCommand {
    private static final String TEST_MAP_PREFIX = "__bo_physics_ragdoll_";
    private static final int KILL_DELAY_TICKS = 40;
    private static final int MAX_TEST_TICKS = 260;
    private static final int CLEANUP_DELAY_TICKS = 120;
    private static final Map<UUID, RagdollTestRun> RUNS = new HashMap<>();

    private BOPhysicsRagdollDebugCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(command("bo_physics_ragdoll_test"));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> fpsmCommand() {
        return command("physics_ragdoll_test");
    }

    private static LiteralArgumentBuilder<CommandSourceStack> command(String name) {
        return Commands.literal(name)
                .requires(source -> !FMLEnvironment.production && source.hasPermission(2))
                .executes(BOPhysicsRagdollDebugCommand::handle);
    }

    private static int handle(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer victim = source.getPlayerOrException();

        if (FMLEnvironment.production) {
            source.sendFailure(Component.literal("Physics ragdoll debug command is disabled in production."));
            return 0;
        }
        if (!ModList.get().isLoaded("physicsmod")) {
            source.sendFailure(Component.literal("PhysicsMod is not loaded; cannot run ragdoll test."));
            return 0;
        }
        if (!FPSMCore.initialized()) {
            source.sendFailure(Component.literal("FPSMatch core is not initialized."));
            return 0;
        }

        RagdollTestRun previous = RUNS.remove(victim.getUUID());
        if (previous != null) {
            previous.cleanup("replaced");
        }

        RagdollTestRun run = new RagdollTestRun(source, victim);
        if (!run.start()) {
            run.cleanup("start_failed");
            return 0;
        }

        RUNS.put(victim.getUUID(), run);
        source.sendSuccess(() -> Component.literal("Started BO physics ragdoll test. Watch [BO_RAGDOLL_TEST] and [BO_PHYSICS_RAGDOLL] in latest.log."), true);
        return 1;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || RUNS.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<UUID, RagdollTestRun>> iterator = RUNS.entrySet().iterator();
        while (iterator.hasNext()) {
            RagdollTestRun run = iterator.next().getValue();
            if (run.tick()) {
                iterator.remove();
            }
        }
    }

    private static final class RagdollTestRun {
        private final CommandSourceStack source;
        private final ServerPlayer victim;
        private final ServerLevel level;
        private final Vec3 oldPosition;
        private final float oldYaw;
        private final float oldPitch;
        private final GameType oldGameMode;
        private final InventorySnapshot oldInventory;
        private final Map<BlockPos, BlockState> originalBlocks = new HashMap<>();

        private CSGameMap map;
        private DamageableFakePlayer killer;
        private Vec3 killerFeet;
        private Vec3 victimFeet;
        private long startTick;
        private int cleanupCountdown = -1;
        private boolean deathTriggered;
        private boolean cleaned;

        private RagdollTestRun(CommandSourceStack source, ServerPlayer victim) {
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
                this.killerFeet = new Vec3(origin.getX() + 4.5D, origin.getY(), origin.getZ() + 0.5D);
                this.victimFeet = new Vec3(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D);

                prepareRange(origin);

                AreaData area = new AreaData(origin.offset(-8, -2, -8), origin.offset(8, 8, 8));
                this.map = new CSGameMap(level, TEST_MAP_PREFIX + victim.getUUID().toString().substring(0, 8), area);
                this.killer = new DamageableFakePlayer(level, new GameProfile(UUID.randomUUID(), "BO_Ragdoll_Killer"));
                this.killer.setNoGravity(true);
                this.killer.setInvulnerable(false);
                this.killer.setHealth(this.killer.getMaxHealth());
                moveKillerToRange();
                if (!level.addFreshEntity(killer)) {
                    fail("failed to add ragdoll fake killer to level");
                    return false;
                }

                addSpawnPoint(map.getCT(), killerFeet, 90.0F, 0.0F);
                addSpawnPoint(map.getT(), victimFeet, -90.0F, 0.0F);

                MapTeams.JoinTeamResult killerJoin = map.join("ct", killer);
                MapTeams.JoinTeamResult victimJoin = map.join("t", victim);
                if (!killerJoin.isSuccess() || !victimJoin.isSuccess()) {
                    fail("join failed killer=" + killerJoin.status() + " victim=" + victimJoin.status());
                    return false;
                }
                if (!map.start()) {
                    fail("classic test map failed to start");
                    return false;
                }

                teleportVictimToRange();
                moveKillerToRange();
                this.startTick = level.getGameTime();
                FPSMatch.LOGGER.info("[BO_RAGDOLL_TEST] started map={} killer={} victim={} killerPos={} victimPos={} victimGameMode={}",
                        map.getMapName(),
                        killer.getGameProfile().getName(),
                        victim.getGameProfile().getName(),
                        killerFeet,
                        victimFeet,
                        victim.gameMode.getGameModeForPlayer());
                return true;
            } catch (Exception e) {
                FPSMatch.LOGGER.error("[BO_RAGDOLL_TEST] failed to start", e);
                source.sendFailure(Component.literal("BO physics ragdoll test failed to start: " + e.getMessage()));
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

            if (!deathTriggered && elapsed >= KILL_DELAY_TICKS) {
                triggerDeath();
                return false;
            }

            if (deathTriggered && victim.isSpectator()) {
                FPSMatch.LOGGER.info("[BO_RAGDOLL_TEST] classic death handled spectator=true health={} gameMode={}",
                        victim.getHealth(),
                        victim.gameMode.getGameModeForPlayer());
                source.sendSuccess(() -> Component.literal("BO physics ragdoll test triggered classic death. Watch [BO_PHYSICS_RAGDOLL] success in latest.log."), true);
                cleanupCountdown = CLEANUP_DELAY_TICKS;
                return false;
            }

            if (elapsed > MAX_TEST_TICKS) {
                fail("timeout deathTriggered=" + deathTriggered
                        + " spectator=" + victim.isSpectator()
                        + " health=" + victim.getHealth()
                        + " gameMode=" + victim.gameMode.getGameModeForPlayer());
                cleanup("timeout");
                return true;
            }

            return false;
        }

        private void triggerDeath() {
            deathTriggered = true;
            teleportVictimToRange();
            moveKillerToRange();
            victim.getAbilities().invulnerable = false;
            victim.setInvulnerable(false);
            victim.setHealth(victim.getMaxHealth());

            DamageSource source = level.damageSources().playerAttack(killer);
            boolean hurt = victim.hurt(source, victim.getMaxHealth() * 20.0F);
            FPSMatch.LOGGER.info("[BO_RAGDOLL_TEST] damage applied hurt={} victimHealth={} victimGameMode={}",
                    hurt,
                    victim.getHealth(),
                    victim.gameMode.getGameModeForPlayer());
        }

        private void prepareRange(BlockPos origin) {
            for (int x = -6; x <= 6; x++) {
                for (int z = -6; z <= 6; z++) {
                    rememberAndSet(origin.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState());
                    for (int y = 0; y <= 5; y++) {
                        rememberAndSet(origin.offset(x, y, z), Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }

        private void rememberAndSet(BlockPos pos, BlockState state) {
            originalBlocks.putIfAbsent(pos.immutable(), level.getBlockState(pos));
            level.setBlock(pos, state, 3);
        }

        private void addSpawnPoint(ServerTeam team, Vec3 pos, float yaw, float pitch) {
            map.getMapTeams().addSpawnPoint(team, new SpawnPointData(level.dimension(), pos, yaw, pitch));
        }

        private void teleportVictimToRange() {
            victim.teleportTo(level, victimFeet.x, victimFeet.y, victimFeet.z, -90.0F, 0.0F);
            victim.setYRot(-90.0F);
            victim.setXRot(0.0F);
            victim.setYHeadRot(-90.0F);
            victim.setYBodyRot(-90.0F);
            victim.setOnGround(true);
            victim.setDeltaMovement(Vec3.ZERO);
        }

        private void moveKillerToRange() {
            killer.moveTo(killerFeet.x, killerFeet.y, killerFeet.z, 90.0F, 0.0F);
            killer.setPos(killerFeet.x, killerFeet.y, killerFeet.z);
            killer.setYRot(90.0F);
            killer.setXRot(0.0F);
            killer.setYHeadRot(90.0F);
            killer.setYBodyRot(90.0F);
            killer.setOnGround(true);
            killer.setDeltaMovement(Vec3.ZERO);
        }

        private void fail(String reason) {
            FPSMatch.LOGGER.error("[BO_RAGDOLL_TEST] failed: {}", reason);
            source.sendFailure(Component.literal("BO physics ragdoll test failed: " + reason));
        }

        private void cleanup(String reason) {
            if (cleaned) {
                return;
            }
            cleaned = true;
            FPSMatch.LOGGER.info("[BO_RAGDOLL_TEST] cleanup reason={}", reason);

            try {
                if (map != null) {
                    map.reset();
                }
                FPSMCore.checkAndLeaveTeam(victim);
            } catch (Exception e) {
                FPSMatch.LOGGER.error("[BO_RAGDOLL_TEST] map cleanup failed", e);
            }
            if (killer != null) {
                killer.discard();
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
}
