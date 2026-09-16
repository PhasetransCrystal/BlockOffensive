package com.ptcrys.blockoffensive.gametest;

import com.mojang.authlib.GameProfile;
import com.ptcrys.blockoffensive.map.CSGameMap;
import com.ptcrys.blockoffensive.map.team.capability.ColoredPlayerCapability;
import com.ptcrys.blockoffensive.net.CSScoreboardSync;
import com.ptcrys.blockoffensive.util.PlayerColorAssignments;
import com.ptcrys.fpsmatch.common.packet.team.TeamPlayerStatsS2CPacket;
import com.ptcrys.fpsmatch.core.data.AreaData;
import com.ptcrys.fpsmatch.core.data.MatchClock;
import com.ptcrys.fpsmatch.core.data.PlayerData;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.*;

@GameTestHolder("blockoffensive")
@PrefixGameTestTemplate(false)
public final class ScoreboardStatsGameTests {
    @GameTest(template = "empty")
    public static void liveStatsSurviveSettlementAndWireRoundTrip(GameTestHelper h) {
        PlayerData data = new PlayerData(UUID.randomUUID(), Component.literal("stats"), true);
        data.addKill(); data.addDeath(); data.addAssist(); data.addHeadshotKill();
        data.addDamage(37); data.addUtilityDamage(12); data.addFlashedEnemy();
        assertStats(h, data, 1, 37, 12, 1);
        data.saveRoundData();
        assertStats(h, data, 1, 37, 12, 1);
        data.addKill(); data.addDamage(8); data.addUtilityDamage(8); data.addFlashedEnemy();
        assertStats(h, data, 2, 45, 20, 2);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var packet = new TeamPlayerStatsS2CPacket(data.getOwner(), "ct", data.name(), data.getScores(),
                    data.getKills(), data.getDeaths(), data.getAssists(), data.getDamage(), 0, true,
                    data.getHeadshotKills(), 1, data.getUtilityDamage(), data.getFlashedEnemies(), data.getTempKills());
            TeamPlayerStatsS2CPacket.encode(packet, buf);
            var decoded = TeamPlayerStatsS2CPacket.decode(buf);
            h.assertTrue(decoded.getKills() == 2 && decoded.getDamage() == 45 && decoded.getUtilityDamage() == 20
                    && decoded.getFlashedEnemies() == 2 && decoded.getRoundKills() == 1, "all counters survive wire encoding");
            h.assertTrue(buf.readableBytes() == 0, "packet consumes all fields");
        } finally { buf.release(); }
        data.saveRoundData(); data.saveRoundData();
        assertStats(h, data, 2, 45, 20, 2);
        data.reset();
        assertStats(h, data, 0, 0, 0, 0);
        PlayerData dm = new PlayerData(UUID.randomUUID(), Component.empty(), false);
        dm.addKill(); dm.addDamage(7); dm.addUtilityDamage(7); dm.addFlashedEnemy(); dm.saveRoundData();
        assertStats(h, dm, 1, 7, 7, 1);
        h.succeed();
    }

    private static void assertStats(GameTestHelper h, PlayerData p, int kills, float damage, float utility, int flashed) {
        h.assertTrue(p.getKills() == kills && p.getDamage() == damage && p.getUtilityDamage() == utility
                && p.getFlashedEnemies() == flashed, "live and settled counters agree");
    }

    @GameTest(template = "empty")
    public static void clockAndScoreboardSnapshot(GameTestHelper h) {
        MatchClock clock = new MatchClock();
        for (int i = 0; i < 1200; i++) clock.tick(true);
        for (int i = 0; i < 100; i++) clock.tick(false);
        h.assertTrue(clock.seconds() == 60, "inactive ticks do not advance the match clock");
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var packet = new CSScoreboardSync("test", new int[]{1, -4, 3}, 12, clock.seconds(), 2, 4);
            CSScoreboardSync.encode(packet, buf);
            var decoded = CSScoreboardSync.decode(buf);
            h.assertTrue(Arrays.equals(packet.rounds(), decoded.rounds()) && decoded.elapsedSeconds() == 60
                    && decoded.halfRounds() == 12 && decoded.ctLoss() == 2 && decoded.tLoss() == 4,
                    "round history, loss bonuses and match time survive wire encoding");
        } finally { buf.release(); }
        clock.reset();
        h.assertTrue(clock.ticks() == 0, "rematch resets the clock");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void rosterColorsAreUniqueAndStable(GameTestHelper h) {
        PlayerColorAssignments colors = new PlayerColorAssignments();
        List<UUID> roster = new ArrayList<>();
        for (int i = 0; i < 16; i++) roster.add(new UUID(0, i));
        colors.reconcile(roster);
        var snapshot = colors.snapshot();
        h.assertTrue(new HashSet<>(snapshot.values()).size() == 16, "extended rosters retain unique colors");
        Collections.reverse(roster);
        colors.reconcile(roster);
        h.assertTrue(snapshot.equals(colors.snapshot()), "sorting the scoreboard cannot change colors");
        roster.remove(0); colors.reconcile(roster);
        for (UUID id : roster) h.assertTrue(colors.get(id) == snapshot.get(id), "leaving keeps teammates stable");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void colorsSynchronizeAndFollowSideSwitch(GameTestHelper h) {
        TestMap map = new TestMap(h);
        FakePlayer first = player(h), second = player(h), enemy = player(h);
        try {
            map.join("ct", first); map.join("ct", second); map.join("t", enemy);
            var ct = map.getCT().getCapabilityMap().get(ColoredPlayerCapability.class).orElseThrow();
            var t = map.getT().getCapabilityMap().get(ColoredPlayerCapability.class).orElseThrow();
            int firstColor = ct.getPlayerColor(first.getUUID()), secondColor = ct.getPlayerColor(second.getUUID());
            h.assertTrue(firstColor != secondColor && ct.snapshot().size() == 2 && t.snapshot().size() == 1,
                    "team events cannot allocate colors on unrelated teams");
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            try {
                ct.writeToBuf(buf);
                h.assertTrue(ct.isDirty(), "initial sync to one player cannot consume the pending broadcast");
                var received = buf.readMap(FriendlyByteBuf::readUUID, FriendlyByteBuf::readInt);
                h.assertTrue(received.equals(ct.snapshot()), "all players receive the authoritative palette");
            } finally { buf.release(); }
            map.switchTeams();
            h.assertTrue(t.getPlayerColor(first.getUUID()) == firstColor && t.getPlayerColor(second.getUUID()) == secondColor,
                    "halftime preserves personal colors");
            h.succeed();
        } finally { cleanup(h, map, first, second, enemy); }
    }

    private static FakePlayer player(GameTestHelper h) {
        UUID id = UUID.randomUUID();
        return FakePlayerFactory.get(h.getLevel(), new GameProfile(id, "Stats" + id.toString().substring(0, 8)));
    }

    private static void cleanup(GameTestHelper h, TestMap map, FakePlayer... players) {
        for (FakePlayer player : players) map.leave(player);
        map.getMapTeams().shutdown(h.getLevel().getScoreboard());
    }

    private static final class TestMap extends CSGameMap {
        TestMap(GameTestHelper h) {
            super(h.getLevel(), "stats_" + UUID.randomUUID().toString().substring(0, 8),
                    new AreaData(h.absolutePos(BlockPos.ZERO), h.absolutePos(new BlockPos(8, 8, 8))));
        }
        void begin() { isStart = true; }
        @Override public void loadConfig() {}
    }
}
