package net.ptcrys.blockoffensive.intro;

import net.ptcrys.blockoffensive.map.CSMap;
import net.ptcrys.fpsmatch.core.FPSMCore;
import net.ptcrys.fpsmatch.core.map.BaseMap;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

public final class IntroCommand {

    private static final String GAME_TYPE = "cs";

    private IntroCommand() {}

    public static void registerHelp(net.ptcrys.fpsmatch.common.event.register.RegisterFPSMCommandEvent event) {
        String path = "fpsm blockoffensive halftime";
        event.registerHelp(path, "commands.blockoffensive.help.halftime");
        for (String action : new String[] { "reload", "select", "facing", "duration", "enable", "preview", "debug_halftime_switch", "clear" }) {
            event.registerHelp(path + " " + action, "commands.blockoffensive.help.halftime." + action);
        }
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("halftime");

        root.then(Commands.literal("reload").requires(IntroCommand::canEdit).executes(ctx -> {
            IntroConfigStore.load(ctx.getSource().getServer());
            ctx.getSource().sendSuccess(() -> Component.literal("[BlockOffensive Halftime] Reloaded " + IntroConfigStore.currentPath(ctx.getSource().getServer())), false);
            return Command.SINGLE_SUCCESS;
        }));

        root.then(Commands.literal("select").requires(IntroCommand::canEdit)
                .then(Commands.argument("map", StringArgumentType.word())
                        .then(Commands.argument("side", StringArgumentType.word())
                                .then(Commands.argument("from", BlockPosArgument.blockPos())
                                        .then(Commands.argument("to", BlockPosArgument.blockPos())
                                                .executes(IntroCommand::selectArea))))));

        root.then(Commands.literal("facing").requires(IntroCommand::canEdit)
                .then(Commands.argument("map", StringArgumentType.word())
                        .then(Commands.argument("side", StringArgumentType.word())
                                .then(Commands.argument("yaw", FloatArgumentType.floatArg(-360.0f, 360.0f))
                                        .executes(ctx -> setFacing(ctx, 0.0f))
                                        .then(Commands.argument("pitch", FloatArgumentType.floatArg(-90.0f, 90.0f))
                                                .executes(ctx -> setFacing(ctx, FloatArgumentType.getFloat(ctx, "pitch"))))))));

        root.then(Commands.literal("duration").requires(IntroCommand::canEdit)
                .then(Commands.argument("map", StringArgumentType.word())
                        .then(Commands.argument("side", StringArgumentType.word())
                                .then(Commands.argument("ticks", IntegerArgumentType.integer(60, 140))
                                        .executes(IntroCommand::setDuration)))));

        root.then(Commands.literal("enable").requires(IntroCommand::canEdit)
                .then(Commands.argument("map", StringArgumentType.word())
                        .then(Commands.argument("phase", StringArgumentType.word())
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(IntroCommand::setEnabled)))));

        root.then(Commands.literal("preview").requires(IntroCommand::canEdit)
                .then(Commands.argument("map", StringArgumentType.word())
                        .then(Commands.argument("side", StringArgumentType.word())
                                .executes(IntroCommand::preview))));

        root.then(Commands.literal("debug_halftime_switch").requires(IntroCommand::canEdit)
                .then(Commands.argument("map", StringArgumentType.word())
                        .executes(ctx -> debugHalftimeSwitch(ctx, null, null))
                        .then(Commands.argument("ct_score", IntegerArgumentType.integer(0))
                                .then(Commands.argument("t_score", IntegerArgumentType.integer(0))
                                        .executes(ctx -> debugHalftimeSwitch(
                                                ctx,
                                                IntegerArgumentType.getInteger(ctx, "ct_score"),
                                                IntegerArgumentType.getInteger(ctx, "t_score")))))));

        root.then(Commands.literal("clear").requires(IntroCommand::canEdit)
                .then(Commands.argument("map", StringArgumentType.word())
                        .then(Commands.argument("side", StringArgumentType.word())
                                .executes(IntroCommand::clear))));

        return root;
    }

    private static int selectArea(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String mapName = StringArgumentType.getString(ctx, "map");
        IntroTeamSide side = parseSide(ctx.getSource(), StringArgumentType.getString(ctx, "side"));
        if (side == null || requireCsMap(ctx.getSource(), mapName) == null) {
            return 0;
        }
        BlockPos from = BlockPosArgument.getLoadedBlockPos(ctx, "from");
        BlockPos to = BlockPosArgument.getLoadedBlockPos(ctx, "to");
        if (from.equals(to)) {
            ctx.getSource().sendFailure(Component.literal("[BlockOffensive Halftime] area must contain more than one block position."));
            return 0;
        }
        IntroConfigStore.SideConfig config = IntroConfigStore.getOrCreate(GAME_TYPE, mapName, side);
        config.area = new IntroConfigStore.AreaBox(from, to);
        IntroConfigStore.save(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal("[BlockOffensive Halftime] " + mapName + " " + side.id() + " area saved."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int setFacing(CommandContext<CommandSourceStack> ctx, float pitch) {
        String mapName = StringArgumentType.getString(ctx, "map");
        IntroTeamSide side = parseSide(ctx.getSource(), StringArgumentType.getString(ctx, "side"));
        if (side == null || requireCsMap(ctx.getSource(), mapName) == null) {
            return 0;
        }
        IntroConfigStore.SideConfig config = IntroConfigStore.getOrCreate(GAME_TYPE, mapName, side);
        config.yaw = FloatArgumentType.getFloat(ctx, "yaw");
        config.pitch = pitch;
        IntroConfigStore.save(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal("[BlockOffensive Halftime] facing saved."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int setDuration(CommandContext<CommandSourceStack> ctx) {
        String mapName = StringArgumentType.getString(ctx, "map");
        IntroTeamSide side = parseSide(ctx.getSource(), StringArgumentType.getString(ctx, "side"));
        if (side == null || requireCsMap(ctx.getSource(), mapName) == null) {
            return 0;
        }
        IntroConfigStore.SideConfig config = IntroConfigStore.getOrCreate(GAME_TYPE, mapName, side);
        config.durationTicks = IntegerArgumentType.getInteger(ctx, "ticks");
        IntroConfigStore.save(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal("[BlockOffensive Halftime] duration saved."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int setEnabled(CommandContext<CommandSourceStack> ctx) {
        String mapName = StringArgumentType.getString(ctx, "map");
        if (requireCsMap(ctx.getSource(), mapName) == null) {
            return 0;
        }
        String phase = StringArgumentType.getString(ctx, "phase");
        boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
        boolean changed = false;
        for (IntroTeamSide side : IntroTeamSide.values()) {
            IntroConfigStore.SideConfig config = IntroConfigStore.getOrCreate(GAME_TYPE, mapName, side);
            if ("switch".equalsIgnoreCase(phase)) {
                config.switchEnabled = enabled;
                changed = true;
            }
        }
        if (!changed) {
            ctx.getSource().sendFailure(Component.literal("[BlockOffensive Halftime] phase must be switch."));
            return 0;
        }
        IntroConfigStore.save(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal("[BlockOffensive Halftime] " + phase + " enabled=" + enabled), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int preview(CommandContext<CommandSourceStack> ctx) {
        String mapName = StringArgumentType.getString(ctx, "map");
        IntroTeamSide side = parseSide(ctx.getSource(), StringArgumentType.getString(ctx, "side"));
        CSMap map = requireCsMap(ctx.getSource(), mapName);
        if (side == null || map == null) {
            return 0;
        }
        boolean started = IntroRuntimeController.triggerPreview(map, side);
        if (!started) {
            ctx.getSource().sendFailure(Component.literal("[BlockOffensive Halftime] No valid sequence for " + mapName + " " + side.id()));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("[BlockOffensive Halftime] Preview started."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int debugHalftimeSwitch(CommandContext<CommandSourceStack> ctx, Integer explicitCtScore, Integer explicitTScore) {
        String mapName = StringArgumentType.getString(ctx, "map");
        CSMap map = requireCsMap(ctx.getSource(), mapName);
        if (map == null) {
            return 0;
        }
        int maxNormalScore = winnerRound(map) - 1;
        if (maxNormalScore <= 0) {
            ctx.getSource().sendFailure(Component.literal("[BlockOffensive Halftime] winnerRound must be greater than 1."));
            return 0;
        }
        int ctScore = explicitCtScore == null ? maxNormalScore / 2 : explicitCtScore;
        int tScore = explicitTScore == null ? maxNormalScore - ctScore : explicitTScore;
        if (ctScore + tScore != maxNormalScore) {
            ctx.getSource().sendFailure(Component.literal("[BlockOffensive Halftime] debug halftime scores must sum to winnerRound - 1 (" + maxNormalScore + ")."));
            return 0;
        }
        map.getCT().setScores(ctScore);
        map.getT().setScores(tScore);
        // Test/admin hook: let BO's own startNewRound -> cleanupMap -> switchTeams path fire the switch intro.
        // Calling cleanupMap here as well would run the half-time switch twice.
        map.startNewRound();
        ctx.getSource().sendSuccess(() -> Component.literal("[BlockOffensive Halftime] Debug halftime switch requested for " + mapName + " scores ct=" + ctScore + " t=" + tScore + "."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int winnerRound(CSMap map) {
        for (net.ptcrys.fpsmatch.core.data.Setting<?> setting : ((BaseMap) map).settings()) {
            if ("winnerRound".equals(setting.getConfigName()) && setting.get() instanceof Integer value) {
                return value;
            }
        }
        return 13;
    }

    private static int clear(CommandContext<CommandSourceStack> ctx) {
        String mapName = StringArgumentType.getString(ctx, "map");
        IntroTeamSide side = parseSide(ctx.getSource(), StringArgumentType.getString(ctx, "side"));
        if (side == null) {
            return 0;
        }
        IntroRuntimeController.ensureConfigLoaded(ctx.getSource().getServer());
        boolean changed = IntroConfigStore.clear(GAME_TYPE, mapName, side);
        IntroConfigStore.save(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal("[BlockOffensive Halftime] clear " + mapName + " " + side.id() + " changed=" + changed), false);
        return Command.SINGLE_SUCCESS;
    }

    private static boolean canEdit(CommandSourceStack source) {
        return source.hasPermission(2);
    }

    private static IntroTeamSide parseSide(CommandSourceStack source, String value) {
        return IntroTeamSide.parse(value).orElseGet(() -> {
            source.sendFailure(Component.literal("[BlockOffensive Halftime] side must be ct or t."));
            return null;
        });
    }

    private static CSMap requireCsMap(CommandSourceStack source, String mapName) {
        MinecraftServer server = source.getServer();
        IntroRuntimeController.ensureConfigLoaded(server);
        if (!FPSMCore.initialized()) {
            source.sendFailure(Component.literal("[BlockOffensive Halftime] FPSM is not initialized."));
            return null;
        }
        CSMap map = FPSMCore.getInstance().getMapByTypeWithName(GAME_TYPE, mapName)
                .filter(BaseMap.class::isInstance)
                .filter(CSMap.class::isInstance)
                .map(CSMap.class::cast)
                .orElse(null);
        if (map == null) {
            source.sendFailure(Component.literal("[BlockOffensive Halftime] CS map not found: " + mapName));
        }
        return map;
    }
}
