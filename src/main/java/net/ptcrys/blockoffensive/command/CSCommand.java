package net.ptcrys.blockoffensive.command;

import net.ptcrys.blockoffensive.map.CSGameMap;
import net.ptcrys.fpsmatch.common.event.register.RegisterFPSMCommandEvent;
import net.ptcrys.fpsmatch.core.FPSMCore;
import net.ptcrys.fpsmatch.core.map.BaseMap;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;

import com.mojang.brigadier.arguments.StringArgumentType;

import java.util.Optional;

public class CSCommand {

    private static final String[] MAP_COMMANDS = { "pause", "p", "unpause", "up", "agree", "a", "disagree", "da", "drop", "d" };

    public static void register(RegisterFPSMCommandEvent event) {
        for (String command : MAP_COMMANDS) {
            if (command.equals("pause")) continue;
            event.addPlayerChild(Commands.literal(command)
                    .requires(source -> source.getEntity() instanceof ServerPlayer)
                    .executes(context -> handleAction(context.getSource(), command)));
            String action = switch (command) {
                case "p" -> "pause";
                case "up" -> "unpause";
                case "a" -> "agree";
                case "da" -> "disagree";
                case "d" -> "drop";
                default -> command;
            };
            event.registerHelp("fpsm " + command, "commands.blockoffensive.help." + action);
        }
    }

    /** Explicitly retained standalone entry points. */
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("cs2")
                .then(Commands.argument("action", StringArgumentType.string())
                        .executes(context -> handleAction(context.getSource(), StringArgumentType.getString(context, "action")))));
        event.getDispatcher().register(Commands.literal("pause")
                .executes(context -> handleAction(context.getSource(), "pause")));
    }

    private static int handleAction(CommandSourceStack source, String action) {
        if (source.getEntity() instanceof ServerPlayer player) {
            Optional<BaseMap> optional = FPSMCore.getInstance().getMapByPlayer(player);
            if (optional.isPresent() && optional.get() instanceof CSGameMap csGameMap) {
                csGameMap.handleChatCommand(action, player);
            } else {
                source.sendFailure(Component.translatable("command.cs.noMap"));
            }
        } else {
            source.sendFailure(Component.translatable("command.cs.onlyPlayer"));
        }
        return 1;
    }
}
