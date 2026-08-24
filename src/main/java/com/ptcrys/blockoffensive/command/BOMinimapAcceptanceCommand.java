package com.ptcrys.blockoffensive.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.ptcrys.blockoffensive.minimap.acceptance.AcceptanceFixtureRegistry;
import com.ptcrys.blockoffensive.minimap.acceptance.BOMinimapAcceptanceFixture;
import com.ptcrys.blockoffensive.net.acceptance.BOMinimapAcceptanceAck;
import com.ptcrys.blockoffensive.net.acceptance.BOMinimapAcceptanceEditorContext;
import com.ptcrys.fpsmatch.core.map.BaseMap;
import com.ptcrys.fpsmatch.core.FPSMCore;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.util.Optional;
import java.util.UUID;

public final class BOMinimapAcceptanceCommand {
    private static final String[] ACCEPTANCE_SCENES = {
            "hud_crowded", "tactical", "editor_dirty", "editor_publishing",
            "editor_error", "map_room_error"
    };
    private static final AcceptanceFixtureRegistry<BOMinimapAcceptanceFixture> FIXTURES =
            new AcceptanceFixtureRegistry<>();

    private BOMinimapAcceptanceCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bo_minimap_acceptance")
                .requires(source -> !FMLEnvironment.production && source.hasPermission(2))
                .then(Commands.literal("create")
                        .then(Commands.argument("mode", StringArgumentType.word())
                                .executes(BOMinimapAcceptanceCommand::create)))
                .then(Commands.literal("scene")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider
                                        .suggest(ACCEPTANCE_SCENES, builder))
                                .executes(BOMinimapAcceptanceCommand::scene)))
                .then(Commands.literal("status").executes(BOMinimapAcceptanceCommand::status))
                .then(Commands.literal("cleanup").executes(BOMinimapAcceptanceCommand::cleanup)));
    }

    private static int create(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        return withCommandFailure(context, () -> {
            if (FMLEnvironment.production) {
                throw new IllegalStateException(
                        "Minimap acceptance fixtures are disabled in production."
                );
            }
            ServerPlayer player = context.getSource().getPlayerOrException();
            cleanupPlayer(player.getUUID());
            BaseMap activeMap = FPSMCore.initialized()
                    ? FPSMCore.getInstance().getMapByPlayerWithSpec(player).orElse(null)
                    : null;
            BOMinimapAcceptanceFixture fixture = FIXTURES.createIfNoMatch(
                    player.getUUID(),
                    candidate -> activeMap != null && candidate.matchesMap(activeMap),
                    () -> BOMinimapAcceptanceFixture.prepare(
                            player.server,
                            player,
                            StringArgumentType.getString(context, "mode")
                    )
            );
            context.getSource().sendSuccess(() -> Component.literal(fixture.status()), false);
            return 1;
        });
    }

    private static int scene(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        return withFixture(context, fixture -> {
            String result = fixture.showScene(StringArgumentType.getString(context, "name"));
            context.getSource().sendSuccess(() -> Component.literal(result), false);
            return 1;
        });
    }

    private static int status(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        return withFixture(context, fixture -> {
            context.getSource().sendSuccess(() -> Component.literal(fixture.status()), false);
            return 1;
        });
    }

    private static int cleanup(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        return withCommandFailure(context, () -> {
            UUID playerId = context.getSource().getPlayerOrException().getUUID();
            boolean removed = cleanupPlayer(playerId);
            context.getSource().sendSuccess(() -> Component.literal(
                    removed ? "Minimap acceptance fixture cleaned." : "No active fixture."), false);
            return removed ? 1 : 0;
        });
    }

    public static boolean cleanupPlayer(UUID playerId) {
        return FIXTURES.cleanup(playerId);
    }

    public static int cleanupMap(BaseMap map) {
        return FIXTURES.cleanupMatching(fixture -> fixture.matchesMap(map));
    }

    public static int cleanupAll() {
        return FIXTURES.cleanupAll();
    }

    public static boolean acceptAck(
            ServerPlayer player,
            BOMinimapAcceptanceAck acknowledgement
    ) {
        if (acknowledgement == null || player == null
                || !player.getUUID().equals(acknowledgement.ownerId())) {
            return false;
        }
        return activeFixture(player)
                .filter(fixture -> fixture.acceptAck(player, acknowledgement))
                .isPresent();
    }

    public static boolean acceptEditorContext(
            ServerPlayer player,
            BOMinimapAcceptanceEditorContext editorContext
    ) {
        if (editorContext == null || player == null
                || !player.getUUID().equals(editorContext.ownerId())) {
            return false;
        }
        return activeFixture(player)
                .filter(fixture -> fixture.acceptEditorContext(player, editorContext))
                .isPresent();
    }

    /** Advances only acceptance deadlines; real fixture cleanup stays lifecycle-owned. */
    public static void tick() {
        if (FMLEnvironment.production) {
            return;
        }
        FIXTURES.snapshot().forEach(BOMinimapAcceptanceFixture::tick);
    }

    private static Optional<BOMinimapAcceptanceFixture> activeFixture(ServerPlayer player) {
        if (FMLEnvironment.production || player.server == null || player.connection == null
                || player.server.getPlayerList().getPlayer(player.getUUID()) != player) {
            return Optional.empty();
        }
        return FIXTURES.find(player.getUUID())
                .filter(fixture -> fixture.matchesConnection(player));
    }

    private static BOMinimapAcceptanceFixture requireFixture(ServerPlayer player) {
        return FIXTURES.find(player.getUUID()).orElseThrow(() ->
                new IllegalStateException("Create a minimap acceptance fixture first."));
    }

    private static int withFixture(
            CommandContext<CommandSourceStack> context,
            FixtureAction action
    ) throws CommandSyntaxException {
        return withCommandFailure(context, () -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            return action.run(requireFixture(player));
        });
    }

    private static int withCommandFailure(
            CommandContext<CommandSourceStack> context,
            CommandAction action
    ) throws CommandSyntaxException {
        try {
            return action.run();
        } catch (RuntimeException failure) {
            String message = failure.getMessage();
            context.getSource().sendFailure(Component.literal(
                    message == null || message.isBlank()
                            ? "Minimap acceptance command failed."
                            : message
            ));
            return 0;
        }
    }

    @FunctionalInterface
    private interface FixtureAction {
        int run(BOMinimapAcceptanceFixture fixture);
    }

    @FunctionalInterface
    private interface CommandAction {
        int run() throws CommandSyntaxException;
    }
}
