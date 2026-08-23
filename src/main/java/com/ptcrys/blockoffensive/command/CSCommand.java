package com.ptcrys.blockoffensive.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.ptcrys.blockoffensive.map.CSGameMap;
import com.ptcrys.fpsmatch.core.FPSMCore;
import com.ptcrys.fpsmatch.core.map.BaseMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.util.Optional;

public class CSCommand {
    private static final String[] MAP_COMMANDS = {"pause", "p", "unpause", "up", "agree", "a", "disagree", "da", "drop", "d"};

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        LiteralArgumentBuilder<CommandSourceStack> literal = Commands.literal("cs2").then(Commands.argument("action",StringArgumentType.string()).executes(context -> {
            String action = StringArgumentType.getString(context,"action");
            return handleAction(context.getSource(), action);
        }));

        dispatcher.register(literal);
        for (String command : MAP_COMMANDS) {
            dispatcher.register(Commands.literal(command).executes(context -> handleAction(context.getSource(), command)));
        }
    }

    private static int handleAction(CommandSourceStack source, String action) {
        if(source.getEntity() instanceof ServerPlayer player){
            Optional<BaseMap> optional = FPSMCore.getInstance().getMapByPlayer(player);
            if (optional.isPresent() && optional.get() instanceof CSGameMap csGameMap){
                csGameMap.handleChatCommand(action,player);
            }else{
                source.sendFailure(Component.translatable("command.cs.noMap"));
            }
        }else{
            source.sendFailure(Component.translatable("command.cs.onlyPlayer"));
        }
        return 1;
    }
}
