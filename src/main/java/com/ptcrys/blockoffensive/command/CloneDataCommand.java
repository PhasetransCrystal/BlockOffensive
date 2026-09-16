package com.ptcrys.blockoffensive.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.logging.LogUtils;
import com.ptcrys.blockoffensive.map.CSGameMap;
import com.ptcrys.fpsmatch.common.capability.team.ShopCapability;
import com.ptcrys.fpsmatch.common.command.FPSMCommandSuggests;
import com.ptcrys.fpsmatch.common.command.FPSMHelpManager;
import com.ptcrys.fpsmatch.core.FPSMCore;
import com.ptcrys.fpsmatch.core.shop.FPSMShop;
import com.ptcrys.fpsmatch.core.shop.INamedType;
import com.ptcrys.fpsmatch.core.shop.slot.ShopSlot;
import com.ptcrys.fpsmatch.core.team.ServerTeam;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class CloneDataCommand {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String SOURCE_MAP_ARG = "source_map";
    private static final String TARGET_MAP_ARG = "target_map";

    private static final SuggestionProvider<CommandSourceStack> CS_MAP_SUGGESTIONS =
            new FPSMCommandSuggests.FPSMSuggestionProvider((context, builder) ->
                    FPSMCommandSuggests.getSuggestions(builder,
                            FPSMCore.getInstance().getMapNames(CSGameMap.TYPE)));

    private CloneDataCommand() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("clonedata")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("cs")
                        .then(Commands.argument(SOURCE_MAP_ARG, StringArgumentType.string())
                                .suggests(CS_MAP_SUGGESTIONS)
                                .then(Commands.literal("shopdata")
                                        .then(Commands.argument(TARGET_MAP_ARG, StringArgumentType.string())
                                                .suggests(CS_MAP_SUGGESTIONS)
                                                .executes(CloneDataCommand::copyShopData)))
                                .then(Commands.literal("gamedata")
                                        .then(Commands.argument(TARGET_MAP_ARG, StringArgumentType.string())
                                                .suggests(CS_MAP_SUGGESTIONS)
                                                .executes(CloneDataCommand::copyGameData)))));
    }

    static void registerHelp() {
        FPSMHelpManager help = FPSMHelpManager.getInstance();
        help.registerCommandHelp("fpsm clonedata", Component.translatable("commands.blockoffensive.clonedata.description"));
        help.registerCommandHelp("fpsm clonedata cs shopdata", "commands.blockoffensive.help.clone_shop");
        help.registerCommandHelp("fpsm clonedata cs gamedata", "commands.blockoffensive.help.clone_game");
        help.registerCommandParameters("fpsm clonedata", "*cs", "*source_map", "*shopdata|gamedata", "*target_map");
    }

    private static int copyShopData(CommandContext<CommandSourceStack> context) {
        Optional<CSGameMap> source = getMap(context, SOURCE_MAP_ARG);
        if (source.isEmpty()) {
            return mapNotFound(context, SOURCE_MAP_ARG);
        }
        Optional<CSGameMap> target = getMap(context, TARGET_MAP_ARG);
        if (target.isEmpty()) {
            return mapNotFound(context, TARGET_MAP_ARG);
        }

        int copiedTeams = 0;
        try {
            for (ServerTeam sourceTeam : source.get().getMapTeams().getNormalTeams()) {
                Optional<ServerTeam> targetTeam = target.get().getMapTeams().getTeamByName(sourceTeam.getName());
                Optional<FPSMShop<?>> sourceShop = ShopCapability.getShop(sourceTeam);
                Optional<FPSMShop<?>> targetShop = targetTeam.flatMap(ShopCapability::getShop);
                if (sourceShop.isEmpty() || targetShop.isEmpty()) continue;

                copyDefaultShopData(sourceShop.get(), targetShop.get());
                targetShop.get().sync();
                copiedTeams++;
            }
        } catch (RuntimeException exception) {
            LOG.error("Failed to clone shop data from {} to {}",
                    source.get().getMapName(), target.get().getMapName(), exception);
            context.getSource().sendFailure(Component.translatable("commands.blockoffensive.clonedata.failed"));
            return 0;
        }

        if (copiedTeams == 0) {
            context.getSource().sendFailure(Component.translatable("commands.blockoffensive.clonedata.shop.no_matching_teams"));
            return 0;
        }

        FPSMCore.getInstance().getFPSMDataManager()
                .saveData(target.get(), target.get().getMapName(), true);
        int result = copiedTeams;
        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.blockoffensive.clonedata.shop.success",
                source.get().getMapName(), target.get().getMapName(), result), true);
        return result;
    }

    private static int copyGameData(CommandContext<CommandSourceStack> context) {
        Optional<CSGameMap> source = getMap(context, SOURCE_MAP_ARG);
        if (source.isEmpty()) {
            return mapNotFound(context, SOURCE_MAP_ARG);
        }
        Optional<CSGameMap> target = getMap(context, TARGET_MAP_ARG);
        if (target.isEmpty()) {
            return mapNotFound(context, TARGET_MAP_ARG);
        }

        try {
            target.get().configFromJson(source.get().configToJson());
            target.get().saveConfig();
            FPSMCore.getInstance().getFPSMDataManager()
                    .saveData(target.get(), target.get().getMapName(), true);
        } catch (RuntimeException exception) {
            LOG.error("Failed to clone game data from {} to {}",
                    source.get().getMapName(), target.get().getMapName(), exception);
            context.getSource().sendFailure(Component.translatable("commands.blockoffensive.clonedata.failed"));
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.blockoffensive.clonedata.game.success",
                source.get().getMapName(), target.get().getMapName()), true);
        return 1;
    }

    private static Optional<CSGameMap> getMap(CommandContext<CommandSourceStack> context, String argument) {
        String name = StringArgumentType.getString(context, argument);
        return FPSMCore.getInstance().getMapByTypeWithName(CSGameMap.TYPE, name)
                .filter(CSGameMap.class::isInstance)
                .map(CSGameMap.class::cast);
    }

    private static int mapNotFound(CommandContext<CommandSourceStack> context, String argument) {
        String name = StringArgumentType.getString(context, argument);
        context.getSource().sendFailure(Component.translatable(
                "commands.blockoffensive.clonedata.map_not_found", name));
        return 0;
    }

    private static void copyDefaultShopData(FPSMShop<?> source, FPSMShop<?> target) {
        copyDefaultShopDataTyped(target, source.getDefaultShopDataMapString());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void copyDefaultShopDataTyped(FPSMShop<?> target, Map<String, List<ShopSlot>> sourceData) {
        replaceDefaultShopData((FPSMShop) target, sourceData);
    }

    private static <T extends Enum<T> & INamedType> void replaceDefaultShopData(
            FPSMShop<T> target, Map<String, List<ShopSlot>> sourceData) {
        Map<T, ArrayList<ShopSlot>> copied = new HashMap<>();
        sourceData.forEach((typeName, slots) -> {
            ArrayList<ShopSlot> copiedSlots = new ArrayList<>(slots.size());
            slots.forEach(slot -> copiedSlots.add(slot.copy()));
            copied.put(target.valueOf(typeName), copiedSlots);
        });
        target.setDefaultShopData(copied);
    }
}
