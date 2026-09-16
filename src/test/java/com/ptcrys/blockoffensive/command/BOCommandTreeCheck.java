package com.ptcrys.blockoffensive.command;

import com.mojang.brigadier.CommandDispatcher;
import com.ptcrys.fpsmatch.common.command.FPSMHelpManager;
import com.ptcrys.fpsmatch.common.event.register.RegisterFPSMCommandEvent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.util.Set;
import java.util.stream.Collectors;

public final class BOCommandTreeCheck {
    public static void main(String[] args) {
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        var help = FPSMHelpManager.getInstance();
        help.reset();
        var tree = Commands.literal("fpsm");
        var event = new RegisterFPSMCommandEvent(tree, null, help);
        BOCommandRegister.onFPSMCommandRegister(event);
        var root = dispatcher.register(tree);
        CSCommand.onRegisterCommands(new RegisterCommandsEvent(dispatcher, Commands.CommandSelection.ALL, null));
        help.bind(root);
        check(dispatcher.getRoot().getChildren().stream().map(node -> node.getName()).collect(Collectors.toSet())
                .equals(Set.of("fpsm", "cs2", "pause")), "only explicitly retained standalone roots remain");
        check(root.getChild("cs2") == null && root.getChild("pause") == null, "retained roots were not migrated");
        check(dispatcher.getRoot().getChild("cs2").getChild("action").getCommand() != null, "cs2 keeps arbitrary action handler");
        for (String name : new String[]{"p", "unpause", "up", "agree", "a", "disagree", "da", "drop", "d"}) {
            check(root.getChild(name).getCommand() != null, "migrated action is executable: " + name);
        }
        var halftime = root.getChild("blockoffensive").getChild("halftime");
        check(halftime.getChildren().size() == 8, "all intro actions remain registered");
        check(root.getChild("mvp") != null && root.getChild("clonedata") != null, "existing management branches retained");
        String rendered = help.buildCommandTreeHelp().getString();
        check(!rendered.contains("cs2") && !rendered.contains("└─ pause"), "retained roots are excluded from help");
        check(rendered.contains("blockoffensive") && rendered.contains("clonedata"), "BO branches appear in help");
        System.out.println("BO command tree checks passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
