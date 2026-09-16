package com.ptcrys.blockoffensive.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.ptcrys.blockoffensive.BlockOffensive;
import com.ptcrys.blockoffensive.data.DeathMessage;
import com.ptcrys.blockoffensive.intro.IntroCommand;
import com.ptcrys.blockoffensive.net.DeathMessageS2CPacket;
import com.ptcrys.blockoffensive.sound.MVPMusicManager;
import com.ptcrys.fpsmatch.common.command.FPSMHelpManager;
import com.ptcrys.fpsmatch.common.event.register.RegisterFPSMCommandEvent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.PacketDistributor;

import java.util.Collection;
import java.util.UUID;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE, modid = BlockOffensive.MODID)
public class BOCommandRegister {

    @SubscribeEvent
    public static void onFPSMCommandRegister(RegisterFPSMCommandEvent event) {
        CSCommand.register(event);
        event.addChild(Commands.literal("mvp")
                .then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("sound", ResourceLocationArgument.id())
                                .suggests(SuggestionProviders.AVAILABLE_SOUNDS)
                                .executes(BOCommandRegister::handleMvp)
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(BOCommandRegister::handleMvpWithName)))));
        FPSMHelpManager.getInstance().registerCommandHelp("fpsm mvp", Component.translatable("commands.blockoffensive.mvp.description"));
        FPSMHelpManager.getInstance().registerCommandParameters("fpsm mvp", "*targets", "*sound", "[name]");

        event.addChild(CloneDataCommand.build());
        CloneDataCommand.registerHelp();

        event.addChild(Commands.literal("blockoffensive")
                .then(IntroCommand.build()));
        event.registerHelp("fpsm blockoffensive", "commands.blockoffensive.help.root");
        IntroCommand.registerHelp(event);

        if (!FMLEnvironment.production) {
            for (String prefix : new String[]{"fpsm ", "fpsm debug "}) {
                event.registerHelp(prefix + "tacz_live_fire_test", "commands.blockoffensive.help.live_fire");
                event.registerHelp(prefix + "physics_ragdoll_test", "commands.blockoffensive.help.ragdoll");
            }
            event.registerHelp("fpsm debug_death_icons", "commands.blockoffensive.help.death_icons");
            event.registerHelp("fpsm debug death_icons", "commands.blockoffensive.help.death_icons");
            event.addChild(BOTaczLiveFireDebugCommand.fpsmCommand());
            event.addChild(BOPhysicsRagdollDebugCommand.fpsmCommand());
            event.addChild(Commands.literal("debug_death_icons")
                    .requires(source -> source.hasPermission(2))
                    .executes(BOCommandRegister::handleDebugDeathIconsSelf)
                    .then(Commands.argument("targets", EntityArgument.players())
                            .executes(BOCommandRegister::handleDebugDeathIcons)));

            event.addChild(Commands.literal("debug")
                    .then(BOTaczLiveFireDebugCommand.fpsmCommand())
                    .then(BOPhysicsRagdollDebugCommand.fpsmCommand())
                    .then(Commands.literal("death_icons")
                            .requires(source -> source.hasPermission(2))
                            .executes(BOCommandRegister::handleDebugDeathIconsSelf)
                            .then(Commands.argument("targets", EntityArgument.players())
                                    .executes(BOCommandRegister::handleDebugDeathIcons))));
        }
    }

    private static int handleMvp(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "targets");
        ResourceLocation sound = ResourceLocationArgument.getId(context, "sound");
        players.forEach(player -> MVPMusicManager.getInstance().addMvpMusic(player.getUUID().toString(), sound, sound.toString()));
        context.getSource().sendSuccess(() -> Component.translatable("commands.blockoffensive.mvp.success", players.size(), sound.toString()), true);
        return 1;
    }

    private static int handleMvpWithName(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "targets");
        ResourceLocation sound = ResourceLocationArgument.getId(context, "sound");
        String name = StringArgumentType.getString(context, "name");
        players.forEach(player -> MVPMusicManager.getInstance().addMvpMusic(player.getUUID().toString(), sound, name));
        context.getSource().sendSuccess(() -> Component.translatable("commands.blockoffensive.mvp.success", players.size(), name), true);
        return 1;
    }

    private static int handleDebugDeathIconsSelf(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return sendDebugDeathIcons(context, java.util.List.of(context.getSource().getPlayerOrException()));
    }

    private static int handleDebugDeathIcons(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return sendDebugDeathIcons(context, EntityArgument.getPlayers(context, "targets"));
    }

    private static int sendDebugDeathIcons(CommandContext<CommandSourceStack> context, Collection<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            DeathMessage message = new DeathMessage.Builder(
                    Component.literal("DevKiller"),
                    player.getUUID(),
                    Component.literal("IconVictim"),
                    UUID.randomUUID(),
                    new ItemStack(Items.DIAMOND_SWORD)
            )
                    .setHeadShot(true)
                    .setThroughSmoke(true)
                    .setThroughWall(true)
                    .build();
            BlockOffensive.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new DeathMessageS2CPacket(message));
        }

        context.getSource().sendSuccess(() -> Component.literal("Sent debug death icon message to " + players.size() + " player(s)."), true);
        return players.size();
    }

}
