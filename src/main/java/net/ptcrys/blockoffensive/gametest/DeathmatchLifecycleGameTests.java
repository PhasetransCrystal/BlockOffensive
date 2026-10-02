package net.ptcrys.blockoffensive.gametest;

import net.ptcrys.blockoffensive.map.CSDeathMatchMap;
import net.ptcrys.blockoffensive.map.CSGameEvents;
import net.ptcrys.fpsmatch.FPSMatch;
import net.ptcrys.fpsmatch.common.FPSMEvents;
import net.ptcrys.fpsmatch.common.capability.team.SpawnPointCapability;
import net.ptcrys.fpsmatch.common.capability.team.StartKitsCapability;
import net.ptcrys.fpsmatch.common.event.FPSMEventHook;
import net.ptcrys.fpsmatch.common.event.FPSMapEvent;
import net.ptcrys.fpsmatch.common.packet.FPSMatchGameTypeS2CPacket;
import net.ptcrys.fpsmatch.common.packet.FPSMatchStatsResetS2CPacket;
import net.ptcrys.fpsmatch.common.packet.team.FPSMAddTeamS2CPacket;
import net.ptcrys.fpsmatch.common.packet.team.TeamPlayerStatsS2CPacket;
import net.ptcrys.fpsmatch.core.data.AreaData;
import net.ptcrys.fpsmatch.core.data.PlayerData;
import net.ptcrys.fpsmatch.core.data.SpawnPointData;
import net.ptcrys.fpsmatch.core.map.DeathContext;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.BusBuilder;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.network.NetworkDirection;

import com.mojang.authlib.GameProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@GameTestHolder("blockoffensive")
@PrefixGameTestTemplate(false)
public final class DeathmatchLifecycleGameTests {

    @GameTest(template = "empty")
    public static void missingSpawnPointsDoesNotDestroyLobby(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            FakePlayer player = fixture.join("ct");
            player.getInventory().add(new ItemStack(Items.DIAMOND));
            fixture.map.setReady(player.getUUID(), true);
            helper.assertTrue(!fixture.map.start(), "a map without spawn points cannot start");
            helper.assertTrue(fixture.map.checkGameHasPlayer(player), "a failed start preserves the lobby roster");
            helper.assertTrue(player.getInventory().countItem(Items.DIAMOND) == 1, "a failed start preserves inventory");
            helper.assertTrue(fixture.map.isReady(player.getUUID()), "a failed start preserves ready state");
            fixture.addSpawnPoint();
            helper.assertTrue(fixture.map.start(), "the same lobby can start after configuring a spawn point");
            helper.succeed();
        }
    }

    @GameTest(template = "empty")
    public static void spectatorJoinIsNotRedirectedIntoCombat(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            fixture.addSpawnPoint();
            fixture.join("ct");
            helper.assertTrue(fixture.map.start(), "match starts");
            FakePlayer spectator = fixture.join("spectator");
            helper.assertTrue(fixture.map.checkSpecHasPlayer(spectator), "the requested spectator team is preserved");
            helper.assertTrue(spectator.isSpectator(), "mid-match spectators remain in spectator mode");
            helper.assertTrue(fixture.map.getDMPlayerData(spectator.getUUID()).isEmpty(), "joining as a spectator does not respawn");
            MinecraftForge.EVENT_BUS.post(new FPSMapEvent.PlayerEvent.LoggedInEvent(fixture.map, spectator));
            fixture.map.respawnPlayer(spectator);
            fixture.map.handleDeath(new DeathContext(spectator, null, spectator.damageSources().generic(), ItemStack.EMPTY, helper.getLevel().getGameTime()));
            helper.assertTrue(spectator.isSpectator(), "reconnect and respawn callbacks keep observers out of combat");
            helper.assertTrue(fixture.map.getMapTeams().getPlayerData(spectator).orElseThrow().getDeaths() == 0, "spectators are excluded from death statistics");
            helper.succeed();
        }
    }

    @GameTest(template = "empty")
    public static void reconnectRestoresCombatAndKitsWithoutResettingStats(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            fixture.addSpawnPoint();
            FakePlayer player = fixture.join("ct");
            fixture.map.getCT().getCapabilityMap().get(StartKitsCapability.class).orElseThrow().addKit(new ItemStack(Items.APPLE));
            helper.assertTrue(fixture.map.start(), "match starts");
            PlayerData data = fixture.map.getMapTeams().getPlayerData(player).orElseThrow();
            data.addScore(18);
            data.addKill();
            MinecraftForge.EVENT_BUS.post(new FPSMapEvent.PlayerEvent.LoggedOutEvent(fixture.map, player));
            player.setPos(player.position().add(20, 0, 0));
            login(player);
            helper.assertTrue(!player.isSpectator() && data.isLiving(), "reconnecting players immediately re-enter deathmatch");
            helper.assertTrue(player.position().equals(fixture.spawn), "reconnect uses a deathmatch spawn");
            helper.assertTrue(player.getInventory().countItem(Items.APPLE) == 1, "reconnect restores kits cleared by logout");
            helper.assertTrue(data.getKills() == 1 && data.getScores() == 18, "reconnect retains accumulated statistics");
            helper.assertTrue(fixture.map.isInSpawnProtection(player.getUUID()), "reconnect grants fresh protection");
            helper.succeed();
        }
    }

    @GameTest(template = "empty")
    public static void spectatorsStayOutOfPlayerInitialization(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            fixture.addSpawnPoint();
            fixture.join("ct");
            FakePlayer spectator = fixture.join("spectator");
            helper.assertTrue(spectator.isSpectator(), "lobby spectators enter spectator mode immediately");
            Vec3 position = spectator.position();
            helper.assertTrue(fixture.map.start(), "match starts with a spectator in the lobby");
            helper.assertTrue(spectator.isSpectator(), "starting a match must not turn a spectator into a combatant");
            helper.assertTrue(spectator.position().equals(position), "starting a match does not teleport spectators to combat spawns");
            helper.assertTrue(fixture.map.getDMPlayerData(spectator.getUUID()).isEmpty(), "spectators have no combat respawn protection");
            helper.succeed();
        }
    }

    @GameTest(template = "empty")
    public static void deathRespawnTimeoutAndRematch(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            fixture.addSpawnPoint();
            FakePlayer player = fixture.join("ct");
            fixture.map.settings().stream().filter(setting -> setting.getConfigName().equals("matchTimeLimit"))
                    .findFirst().orElseThrow().parse("4");
            helper.assertTrue(fixture.map.start(), "match starts");
            fixture.map.handlePlayerFire(player.getUUID());
            helper.assertTrue(!fixture.map.isInSpawnProtection(player.getUUID()), "firing removes spawn protection");
            fixture.map.handleDeath(new DeathContext(player, null, player.damageSources().generic(), ItemStack.EMPTY, helper.getLevel().getGameTime()));
            PlayerData data = fixture.map.getMapTeams().getPlayerData(player).orElseThrow();
            helper.assertTrue(data.isLiving() && data.getDeaths() == 1 && !player.isSpectator(), "death immediately respawns without losing death statistics");
            helper.assertTrue(fixture.map.isInSpawnProtection(player.getUUID()), "each respawn restores protection");
            fixture.map.tick();
            fixture.map.tick();
            helper.assertTrue(fixture.map.getClientTime() < 4 && fixture.map.isStart(), "the match clock advances");
            for (int tick = 0; tick < 5; tick++) fixture.map.tick();
            helper.assertTrue(!fixture.map.isStart() && fixture.map.getClientTime() == 0, "timeout ends and resets the match");
            helper.assertTrue(fixture.map.getDMPlayerData(player.getUUID()).isEmpty(), "reset removes old respawn state");
            helper.assertTrue(!fixture.map.checkGameHasPlayer(player), "timeout removes the finished match roster");
            Vec3 position = player.position();
            fixture.map.respawnPlayer(player);
            helper.assertTrue(player.position().equals(position), "a late respawn cannot teleport players after timeout");
            helper.assertTrue(fixture.map.join("ct", player).isSuccess(), "players can join the next match");
            fixture.makeOnline(player);
            helper.assertTrue(fixture.map.start() && fixture.map.getClientTime() == 4, "a rematch gets a fresh lifecycle and time limit");
            helper.succeed();
        }
    }

    @GameTest(template = "empty")
    public static void respawnDoesNotAffectPlayersWhoLeft(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            fixture.addSpawnPoint();
            FakePlayer player = fixture.join("ct");
            helper.assertTrue(fixture.map.start(), "match starts");
            fixture.map.leave(player);
            player.setGameMode(GameType.SPECTATOR);
            player.setPos(player.position().add(20, 0, 0));
            Vec3 position = player.position();
            fixture.map.respawnPlayer(player);
            helper.assertTrue(player.isSpectator() && player.position().equals(position), "late respawn callbacks cannot pull departed players back into combat");
            helper.succeed();
        }
    }

    @GameTest(template = "empty")
    public static void midMatchJoinWorksInBothDeathmatchModes(GameTestHelper helper) {
        for (boolean teamMode : List.of(false, true)) {
            try (Fixture fixture = new Fixture(helper)) {
                fixture.map.settings().stream().filter(setting -> setting.getConfigName().equals("isTDM"))
                        .findFirst().orElseThrow().parse(Boolean.toString(teamMode));
                fixture.addSpawnPoint();
                FakePlayer first = fixture.join("ct");
                fixture.map.getT().getCapabilityMap().get(StartKitsCapability.class).orElseThrow().addKit(new ItemStack(Items.APPLE));
                helper.assertTrue(fixture.map.start(), "match starts in either deathmatch mode");
                FakePlayer joining = fixture.join("t");
                helper.assertTrue(!joining.isSpectator() && fixture.map.getMapTeams().getPlayerData(joining).orElseThrow().isLiving(), "mid-match players can immediately fight");
                helper.assertTrue(joining.position().equals(fixture.spawn), "late join uses the shared deathmatch spawn pool");
                helper.assertTrue(joining.getInventory().countItem(Items.APPLE) == 1, "late join receives its own team kits");
                helper.assertTrue(fixture.map.isInSpawnProtection(joining.getUUID()), "late join grants spawn protection");
                helper.assertTrue(fixture.map.getCT().getPlayerTeam().isAllowFriendlyFire() == !teamMode, "deathmatch combat rules follow the selected mode");
                fixture.map.handlePlayerMove(joining.getUUID());
                helper.assertTrue(!fixture.map.isInSpawnProtection(joining.getUUID()), "movement cancels spawn protection");
                fixture.map.handleDeath(new DeathContext(joining, first, joining.damageSources().playerAttack(first), ItemStack.EMPTY, helper.getLevel().getGameTime()));
                helper.assertTrue(!joining.isSpectator() && fixture.map.isInSpawnProtection(joining.getUUID()), "both modes respawn immediately after a kill");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void reconnectSendsFullSnapshotAfterClientReset(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            fixture.addSpawnPoint();
            FakePlayer player = fixture.join("ct");
            helper.assertTrue(fixture.map.start(), "match starts");
            List<Integer> packets = new ArrayList<>();
            Connection connection = new Connection(PacketFlow.SERVERBOUND) {

                @Override
                public void send(Packet<?> packet) {
                    if (packet instanceof ClientboundCustomPayloadPacket payload && payload.getIdentifier().toString().equals("fpsmatch:main")) {
                        FriendlyByteBuf data = new FriendlyByteBuf(payload.getData().copy());
                        try {
                            packets.add(data.readVarInt());
                        } finally {
                            data.release();
                        }
                    }
                }
            };
            player.connection = new ServerGamePacketListenerImpl(helper.getLevel().getServer(), connection, player);
            int resetType = packetType(new FPSMatchStatsResetS2CPacket());
            int mapType = packetType(new FPSMatchGameTypeS2CPacket(fixture.map.getMapName(), "csdm", false, false));
            int teamType = packetType(FPSMAddTeamS2CPacket.of(fixture.map.getCT()));
            int statsType = packetType(TeamPlayerStatsS2CPacket.of(fixture.map.getCT(), fixture.map.getMapTeams().getPlayerData(player).orElseThrow()));
            login(player);
            int resetIndex = packets.lastIndexOf(resetType);
            helper.assertTrue(resetIndex >= 0, "the full login chain includes the client reset");
            helper.assertTrue(packets.lastIndexOf(mapType) > resetIndex, "map identity is restored after client reset");
            helper.assertTrue(packets.lastIndexOf(teamType) > resetIndex, "team definitions are restored after client reset");
            helper.assertTrue(packets.lastIndexOf(statsType) > resetIndex, "player statistics are restored after client reset");
            helper.succeed();
        }
    }

    private static void login(ServerPlayer player) {
        var bus = BusBuilder.builder().build();
        bus.register(FPSMEventHook.class);
        bus.register(FPSMEvents.class);
        bus.register(CSGameEvents.class);
        bus.post(new PlayerEvent.PlayerLoggedInEvent(player));
    }

    private static int packetType(Object message) {
        ClientboundCustomPayloadPacket packet = (ClientboundCustomPayloadPacket) FPSMatch.INSTANCE.toVanillaPacket(message, NetworkDirection.PLAY_TO_CLIENT);
        FriendlyByteBuf data = new FriendlyByteBuf(packet.getData().copy());
        try {
            return data.readVarInt();
        } finally {
            data.release();
        }
    }

    private static final class Fixture implements AutoCloseable {

        private final GameTestHelper helper;
        private final TestMap map;
        private final List<FakePlayer> players = new ArrayList<>();
        private final Vec3 spawn;

        private Fixture(GameTestHelper helper) {
            this.helper = helper;
            this.map = new TestMap(helper);
            this.spawn = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
        }

        private void addSpawnPoint() {
            map.getCT().getCapabilityMap().get(SpawnPointCapability.class).orElseThrow()
                    .addSpawnPointData(new SpawnPointData(helper.getLevel().dimension(), spawn, 0, 0));
        }

        private FakePlayer player() {
            UUID id = UUID.randomUUID();
            FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(id, "DM" + id.toString().substring(0, 8)));
            player.setPos(Vec3.atCenterOf(helper.absolutePos(BlockPos.ZERO)).add(0, 2, 0));
            helper.getLevel().addNewPlayer(player);
            players.add(player);
            return player;
        }

        private FakePlayer join(String team) {
            FakePlayer player = player();
            helper.assertTrue(map.join(team, player).isSuccess(), "player joins " + team);
            makeOnline(player);
            return player;
        }

        private void makeOnline(FakePlayer player) {
            var team = map.getMapTeams().getTeamByPlayer(player).orElseThrow();
            PlayerData previous = team.getPlayerData(player.getUUID()).orElseThrow();
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

        @Override
        public void close() {
            for (FakePlayer player : players) {
                map.leave(player);
                helper.getLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
            }
            map.getMapTeams().shutdown(helper.getLevel().getScoreboard());
        }
    }

    private static final class TestMap extends CSDeathMatchMap {

        private TestMap(GameTestHelper helper) {
            super(helper.getLevel(), "dm_test_" + UUID.randomUUID().toString().substring(0, 8),
                    new AreaData(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(8, 8, 8))));
        }

        @Override
        public void loadConfig() {}

        @Override
        public boolean teleportToPoint(ServerPlayer player, SpawnPointData point) {
            boolean teleported = super.teleportToPoint(player, point);
            if (teleported && player instanceof FakePlayer) player.setPos(point.getPosition());
            return teleported;
        }
    }
}
