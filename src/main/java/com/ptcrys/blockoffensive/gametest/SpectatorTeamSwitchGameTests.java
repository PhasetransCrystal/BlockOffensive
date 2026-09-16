package com.ptcrys.blockoffensive.gametest;

import com.mojang.authlib.GameProfile;
import com.ptcrys.blockoffensive.map.CSGameMap;
import com.ptcrys.blockoffensive.spectator.BOSpecManager;
import com.ptcrys.blockoffensive.spectator.DamagePosTracker;
import com.ptcrys.fpsmatch.common.capability.map.GameEndTeleportCapability;
import com.ptcrys.fpsmatch.common.capability.team.SpawnPointCapability;
import com.ptcrys.fpsmatch.core.data.AreaData;
import com.ptcrys.fpsmatch.core.data.PlayerData;
import com.ptcrys.fpsmatch.core.data.SpawnPointData;
import com.ptcrys.fpsmatch.core.match.RoundPhase;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;
import java.util.Optional;

@GameTestHolder("blockoffensive")
@PrefixGameTestTemplate(false)
public final class SpectatorTeamSwitchGameTests {
    @GameTest(template = "empty")
    public static void twoCtPlayersSwitchAndStartNextRound(GameTestHelper helper) {
        TestMap map = new TestMap(helper);
        FakePlayer remainingCt = player(helper);
        FakePlayer switching = player(helper);
        Vec3 exit = switching.position().add(20, 0, 0);
        map.getCapabilityMap().get(GameEndTeleportCapability.class).orElseThrow()
                .setPoint(new SpawnPointData(helper.getLevel().dimension(), exit, 0, 0));
        try {
            helper.assertTrue(map.join("ct", remainingCt).isSuccess(), "first CT joins");
            helper.assertTrue(map.join("ct", switching).isSuccess(), "second CT joins");
            map.beginRound();
            Vec3 beforeSwitch = switching.position();
            helper.assertTrue(map.join("t", switching).isSuccess(), "CT to T switch succeeds");
            helper.assertTrue(switching.position().distanceToSqr(beforeSwitch) < 0.01,
                    "same-map switch must not teleport to the match exit");
            helper.assertTrue(switching.isSpectator(), "mid-round join waits for the next round");
            BOSpecManager.requestAttachTeammate(switching);
            helper.assertTrue(DamagePosTracker.getDeathPose(switching).isPresent(), "orbit anchor is seeded without a killcam");

            map.makeOnline(remainingCt);
            map.makeOnline(switching);
            map.finishCurrentRound(helper);
            map.startNewRound();
            helper.assertTrue(!switching.isSpectator(), "new round restores the player's game mode");
            helper.assertTrue(switching.getCamera() == switching, "new round restores the player's camera");
            helper.assertTrue(DamagePosTracker.getDeathPose(switching).isEmpty(), "new round clears the previous orbit anchor synchronously");
            helper.assertTrue(map.getMapTeams().getPlayerData(switching).orElseThrow().isLiving(), "new round marks T alive");
            helper.assertTrue(switching.position().distanceToSqr(map.tSpawn) < 0.01, "T returns to its spawn point");
            BOSpecManager.requestAttachTeammate(switching);
            helper.assertTrue(switching.getCamera() == switching && DamagePosTracker.getDeathPose(switching).isEmpty(),
                    "late spectator attach cannot restore the old session");

            map.leave(switching);
            helper.assertTrue(switching.position().distanceToSqr(exit) < 0.01, "an actual leave still teleports to the match exit");
            helper.succeed();
        } finally {
            map.leave(switching);
            map.leave(remainingCt);
            map.getMapTeams().shutdown(helper.getLevel().getScoreboard());
            helper.getLevel().removePlayerImmediately(switching, Entity.RemovalReason.DISCARDED);
            helper.getLevel().removePlayerImmediately(remainingCt, Entity.RemovalReason.DISCARDED);
        }
    }

    @GameTest(template = "empty")
    public static void switchDuringKillCamClearsOldSession(GameTestHelper helper) {
        TestMap map = new TestMap(helper);
        FakePlayer player = player(helper);
        try {
            helper.assertTrue(map.join("t", player).isSuccess(), "initial join succeeds");
            map.beginRound();
            player.setGameMode(GameType.SPECTATOR);
            BOSpecManager.sendKillCamAndAttach(player, player, ItemStack.EMPTY);
            helper.assertTrue(BOSpecManager.matchesRecordedMap(player.getUUID(), map), "death context is seeded");

            // No tick between the death and the leave/rejoin transaction.
            helper.assertTrue(map.join("ct", player).isSuccess(), "switch during killcam succeeds");
            helper.assertTrue(player.isSpectator(), "mid-round switch still waits for the next round");
            helper.assertTrue(!BOSpecManager.matchesRecordedMap(player.getUUID(), map), "old killer context must not survive a switch");
            helper.assertTrue(player.getCamera() == player, "old camera is detached");
            helper.assertTrue(DamagePosTracker.getDeathPose(player).isPresent(), "new spectator session has an anchor");

            map.leave(player);
            helper.assertTrue(DamagePosTracker.getDeathPose(player).isEmpty(), "leaving clears the new anchor immediately");
            BOSpecManager.requestAttachTeammate(player);
            helper.assertTrue(player.getCamera() == player, "late attach request cannot reattach after leaving");
            helper.succeed();
        } finally {
            map.leave(player);
            map.getMapTeams().shutdown(helper.getLevel().getScoreboard());
            helper.getLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
        }
    }

    @GameTest(template = "empty")
    public static void consecutiveSpectatorSwitchesDetachOldCamera(GameTestHelper helper) {
        TestMap map = new TestMap(helper);
        FakePlayer player = player(helper);
        FakePlayer oldTarget = player(helper);
        try {
            helper.assertTrue(map.join("t", player).isSuccess(), "initial join succeeds");
            map.beginRound();
            for (String team : new String[]{"ct", "t", "ct"}) {
                player.setGameMode(GameType.SPECTATOR);
                BOSpecManager.startSpectating(player);
                BOSpecManager.requestAttachTeammate(player);
                player.setCamera(oldTarget);
                helper.assertTrue(player.getCamera() == oldTarget, "old attached view is seeded");
                helper.assertTrue(map.join(team, player).isSuccess(), "consecutive team switch succeeds");
                helper.assertTrue(player.getCamera() == player, "switch detaches old view without a tick");
                helper.assertTrue(map.getMapTeams().getTeamByPlayer(player).orElseThrow().getName().equals(team), "new team owns the spectator");
            }
            helper.succeed();
        } finally {
            map.leave(player);
            map.getMapTeams().shutdown(helper.getLevel().getScoreboard());
            helper.getLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
            helper.getLevel().removePlayerImmediately(oldTarget, Entity.RemovalReason.DISCARDED);
        }
    }

    private static FakePlayer player(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(id, "Cam" + id.toString().substring(0, 8)));
        BlockPos pos = helper.absolutePos(BlockPos.ZERO);
        player.setPos(pos.getX(), pos.getY() + 2, pos.getZ());
        helper.getLevel().addNewPlayer(player);
        return player;
    }

    private static final class TestMap extends CSGameMap {
        private final Vec3 tSpawn;

        TestMap(GameTestHelper helper) {
            super(helper.getLevel(), "camera_test_" + UUID.randomUUID().toString().substring(0, 8),
                    new AreaData(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(8, 8, 8))));
            Vec3 ctSpawn = Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
            tSpawn = Vec3.atCenterOf(helper.absolutePos(new BlockPos(6, 2, 6)));
            getCT().getCapabilityMap().get(SpawnPointCapability.class).orElseThrow()
                    .addSpawnPointData(new SpawnPointData(helper.getLevel().dimension(), ctSpawn, 0, 0));
            getT().getCapabilityMap().get(SpawnPointCapability.class).orElseThrow()
                    .addSpawnPointData(new SpawnPointData(helper.getLevel().dimension(), tSpawn, 0, 0));
        }

        void beginRound() {
            isStart = true;
            rebuildRoundLifecycle();
        }

        @Override
        public boolean teleportToPoint(ServerPlayer player, SpawnPointData point) {
            boolean teleported = super.teleportToPoint(player, point);
            // Forge's fake connection deliberately ignores teleport packets, including
            // their server position update. Complete that transport step for this fixture.
            if (teleported && player instanceof FakePlayer) {
                player.setPos(point.getPosition());
            }
            return teleported;
        }

        void makeOnline(FakePlayer player) {
            // Fake players are absent from PlayerList; expose them through the normal roster lookup.
            var team = getMapTeams().getTeamByPlayer(player).orElseThrow();
            var previous = team.getPlayerData(player.getUUID()).orElseThrow();
            PlayerData data = new PlayerData(player) {
                @Override
                public boolean isOnline() {
                    return true;
                }

                @Override
                public Optional<ServerPlayer> getPlayer() {
                    return Optional.of(player);
                }
            };
            data.setLiving(previous.isLiving());
            team.getPlayers().put(player.getUUID(), data);
        }

        void finishCurrentRound(GameTestHelper helper) {
            for (int tick = 0; tick < 300; tick++) {
                roundLifecycle.tick(createRoundContext());
            }
            roundLifecycle.tick(createRoundContext());
            helper.assertTrue(roundLifecycle.phase() == RoundPhase.ROUND_END_WAITING,
                    "the round with no living T ends before the next round starts");
        }

        @Override
        public void loadConfig() {
            // Keep the transient fixture independent of persisted map settings.
        }
    }
}
