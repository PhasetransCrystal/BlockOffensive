package net.ptcrys.blockoffensive;

import net.ptcrys.blockoffensive.map.OvertimeMode;
import net.ptcrys.fpsmatch.core.map.VoteObj;

import net.minecraftforge.common.ForgeConfigSpec;

import org.apache.commons.lang3.tuple.Pair;

public class BOConfig {

    public static class Client {

        public final ForgeConfigSpec.BooleanValue killMessageHudEnabled;
        public final ForgeConfigSpec.IntValue killMessageHudPosition;
        public final ForgeConfigSpec.IntValue messageShowTime;
        public final ForgeConfigSpec.IntValue maxShowCount;

        public final ForgeConfigSpec.BooleanValue killIconHudEnabled;

        public final ForgeConfigSpec.BooleanValue spectatorBombHudEnabled;
        public final ForgeConfigSpec.BooleanValue spectatorRosterEnabled;

        private Client(ForgeConfigSpec.Builder builder) {
            builder.push("kill message");
            {
                killMessageHudEnabled = builder.comment("Kill message enabled").define("hudEnabled", true);
                killMessageHudPosition = builder.comment("Kill message position").defineInRange("hudPosition", 2, 1, 4);
                messageShowTime = builder.comment("Per message show time").defineInRange("messageShowTime", 5, 1, 60);
                maxShowCount = builder.comment("Max show count").defineInRange("maxShowCount", 5, 1, 10);
            }

            builder.pop();

            builder.push("kill icon");
            {
                killIconHudEnabled = builder.comment("Kill icon enabled").define("killIconEnabled", true);
            }

            builder.pop();

            builder.push("spectator");
            {
                spectatorBombHudEnabled = builder.comment(
                        "观战时显示 C4 引信倒计时 HUD",
                        "Show the C4 fuse countdown HUD while spectating").define("spectatorBombHudEnabled", true);
                spectatorRosterEnabled = builder.comment(
                        "观战时显示观战者名单侧栏",
                        "Show the spectator roster side panel while spectating").define("spectatorRosterEnabled", true);
            }

            builder.pop();
        }
    }

    public static class Common {

        public final ForgeConfigSpec.DoubleValue teammateMuffledStepVolume;
        public final ForgeConfigSpec.DoubleValue teammateStepVolume;
        public final ForgeConfigSpec.DoubleValue enemyMuffledStepVolume;
        public final ForgeConfigSpec.DoubleValue enemyStepVolume;

        public final ForgeConfigSpec.IntValue fuseTime;
        // Ping 标记
        public final ForgeConfigSpec.IntValue pingTtlSeconds;
        public final ForgeConfigSpec.DoubleValue pingMaxDistance;
        // 新增的游戏规则配置项
        public final ForgeConfigSpec.BooleanValue keepInventory;
        public final ForgeConfigSpec.BooleanValue immediateRespawn;
        public final ForgeConfigSpec.BooleanValue daylightCycle;
        public final ForgeConfigSpec.BooleanValue weatherCycle;
        public final ForgeConfigSpec.BooleanValue mobSpawning;
        public final ForgeConfigSpec.BooleanValue naturalRegeneration;
        public final ForgeConfigSpec.BooleanValue hardDifficulty;

        public final ForgeConfigSpec.BooleanValue webServerEnabled;
        public final ForgeConfigSpec.IntValue webServerPort;

        // 加时赛 / 投票
        public final ForgeConfigSpec.EnumValue<OvertimeMode> overtimeMode;
        public final ForgeConfigSpec.IntValue overtimeStartMoney;
        public final ForgeConfigSpec.DoubleValue overtimeVoteThreshold;
        public final ForgeConfigSpec.IntValue overtimeVoteSeconds;
        public final ForgeConfigSpec.IntValue overtimeMaxSegments;
        public final ForgeConfigSpec.EnumValue<VoteObj.TimeoutPolicy> voteTimeoutPolicy;
        public final ForgeConfigSpec.EnumValue<VoteObj.AbstentionPolicy> voteAbstentionPolicy;
        public final ForgeConfigSpec.DoubleValue unpauseVoteThreshold;

        private Common(ForgeConfigSpec.Builder builder) {
            builder.push("step sound");
            {
                teammateMuffledStepVolume = builder.comment("Teammate Muffled Step Volume").defineInRange("teammateMuffledStepVolume", 0.05D, 0, 10);
                teammateStepVolume = builder.comment("Teammate Step Volume").defineInRange("teammateStepVolume", 0.15D, 0, 10);
                enemyMuffledStepVolume = builder.comment("Enemy Muffled Step Volume").defineInRange("enemyMuffledStepVolume", 0.4D, 0, 10);
                enemyStepVolume = builder.comment("Enemy Step Volume").defineInRange("enemyStepVolume", 1.2D, 0, 10);
            }
            builder.pop();

            builder.push("c4");
            {
                fuseTime = builder.comment("Fuse Time").defineInRange("Fuse Time", 800, 1, 3200);
            }
            builder.pop();

            builder.push("ping");
            {
                pingTtlSeconds = builder.comment(
                        "队友 Ping 标记存活时长（秒），到期自动消失",
                        "Teammate ping marker lifetime in seconds; expires automatically").defineInRange("pingTtlSeconds", 6, 3, 30);

                pingMaxDistance = builder.comment(
                        "Ping 标记最大显示距离（格），更远不渲染",
                        "Max ping marker render distance in blocks").defineInRange("pingMaxDistance", 256.0D, 16.0D, 1024.0D);
            }
            builder.pop();

            // 新增的游戏规则配置部分
            builder.push("inGameRules");
            {
                keepInventory = builder.comment("Whether players keep their inventory after death")
                        .define("keepInventory", true);

                immediateRespawn = builder.comment("Whether players respawn immediately without showing the death screen")
                        .define("immediateRespawn", true);

                daylightCycle = builder.comment("Whether the daylight cycle and moon phases progress")
                        .define("daylightCycle", false);

                weatherCycle = builder.comment("Whether the weather can change naturally")
                        .define("weatherCycle", false);

                mobSpawning = builder.comment("Whether mobs can spawn naturally")
                        .define("mobSpawning", false);

                naturalRegeneration = builder.comment("Whether players regenerate health naturally when hunger is full")
                        .define("naturalRegeneration", false);

                hardDifficulty = builder.comment("Whether to set game difficulty to Hard")
                        .define("hardDifficulty", true);
            }
            builder.pop();

            builder.push("web server");
            {
                webServerEnabled = builder.comment("Web server enabled").define("webServerEnabled", false);
                webServerPort = builder.comment("Web server port").defineInRange("webServerPort", 8080, 1, 65535);
            }
            builder.pop();

            builder.push("overtime");
            {
                overtimeMode = builder.comment(
                        "加时赛策略：VOTE=12-12时投票决定(默认/向后兼容)，AUTO=直接进入加时，DISABLED=直接判平局。",
                        "Overtime strategy: VOTE (legacy default), AUTO (enter overtime directly), DISABLED (12-12 counts as draw).").defineEnum("overtimeMode", OvertimeMode.VOTE);

                overtimeStartMoney = builder.comment(
                        "加时赛每段开局起始金钱(原硬编码 10000)。",
                        "Overtime starting money per segment (was hardcoded 10000).").defineInRange("overtimeStartMoney", 10000, 0, 100000);

                overtimeVoteThreshold = builder.comment(
                        "VOTE 模式加时投票通过门槛(0~1)。",
                        "Overtime vote pass threshold in VOTE mode.").defineInRange("overtimeVoteThreshold", 0.6D, 0.0D, 1.0D);

                overtimeVoteSeconds = builder.comment(
                        "VOTE 模式加时投票时长(秒)。",
                        "Overtime vote duration in seconds in VOTE mode.").defineInRange("overtimeVoteSeconds", 20, 5, 120);

                overtimeMaxSegments = builder.comment(
                        "最多加时段数，0=无限(防止无限加时，达上限则判平局)。",
                        "Max overtime segments, 0 = unlimited (reaching the cap results in a draw).").defineInRange("overtimeMaxSegments", 0, 0, 10);
            }
            builder.pop();

            builder.push("vote");
            {
                voteTimeoutPolicy = builder.comment(
                        "投票超时策略：FAIL=一律判失败(默认/向后兼容)，PASS_IF_MAJORITY=超时按已投票的多数决结算。",
                        "Vote timeout policy: FAIL (legacy) or PASS_IF_MAJORITY (majority of cast votes on timeout).").defineEnum("voteTimeoutPolicy", VoteObj.TimeoutPolicy.FAIL);

                voteAbstentionPolicy = builder.comment(
                        "弃权策略：COUNT_AS_NO=弃权视为反对(默认)，IGNORE=分母只算已投票人数。",
                        "Abstention policy: COUNT_AS_NO (legacy) or IGNORE (denominator = voters only).").defineEnum("voteAbstentionPolicy", VoteObj.AbstentionPolicy.COUNT_AS_NO);

                unpauseVoteThreshold = builder.comment(
                        "取消暂停投票通过门槛(0.5~1.0，原硬编码 1.0 全票)。",
                        "Unpause vote threshold (was hardcoded 1.0 unanimous).").defineInRange("unpauseVoteThreshold", 1.0D, 0.5D, 1.0D);
            }
            builder.pop();
        }
    }

    public static final Client client;
    public static final ForgeConfigSpec clientSpec;
    public static final Common common;
    public static final ForgeConfigSpec commonSpec;

    static {
        final Pair<Client, ForgeConfigSpec> clientSpecPair = new ForgeConfigSpec.Builder().configure(Client::new);
        client = clientSpecPair.getLeft();
        clientSpec = clientSpecPair.getRight();
        final Pair<Common, ForgeConfigSpec> serverSpecPair = new ForgeConfigSpec.Builder().configure(Common::new);
        common = serverSpecPair.getLeft();
        commonSpec = serverSpecPair.getRight();
    }
}
