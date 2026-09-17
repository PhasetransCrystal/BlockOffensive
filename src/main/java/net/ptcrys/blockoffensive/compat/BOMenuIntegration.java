package net.ptcrys.blockoffensive.compat;

import net.ptcrys.blockoffensive.BOConfig;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import org.jetbrains.annotations.Nullable;

public class BOMenuIntegration {

    public static ConfigBuilder getConfigBuilder() {
        ConfigBuilder root = ConfigBuilder.create()
                .setTitle(Component.translatable("config.blockoffensive.title"))
                .setTransparentBackground(true)
                .setDoesConfirmSave(true);

        root.setGlobalized(true);
        root.setGlobalizedExpanded(false);

        ConfigEntryBuilder entryBuilder = root.entryBuilder();

        // 初始化所有配置
        initClient(root, entryBuilder);
        initCommon(root, entryBuilder);

        return root;
    }

    private static void initClient(ConfigBuilder root, ConfigEntryBuilder entryBuilder) {
        ConfigCategory clientCategory = root.getOrCreateCategory(Component.translatable("config.blockoffensive.client"));

        // Kill Message 子分类
        ConfigCategory killMessageCategory = root.getOrCreateCategory(Component.translatable("config.blockoffensive.client.kill_message"));

        killMessageCategory.addEntry(entryBuilder.startBooleanToggle(
                Component.translatable("config.blockoffensive.client.kill_message.hud_enabled"),
                BOConfig.client.killMessageHudEnabled.get())
                .setDefaultValue(true)
                .setTooltip(Component.translatable("config.blockoffensive.client.kill_message.hud_enabled.tooltip"))
                .setSaveConsumer(BOConfig.client.killMessageHudEnabled::set)
                .build());

        killMessageCategory.addEntry(entryBuilder.startIntSlider(
                Component.translatable("config.blockoffensive.client.kill_message.position"),
                BOConfig.client.killMessageHudPosition.get(), 1, 4)
                .setDefaultValue(2)
                .setTextGetter(value -> Component.literal(getPositionName(value)))
                .setTooltip(Component.translatable("config.blockoffensive.client.kill_message.position.tooltip"))
                .setSaveConsumer(BOConfig.client.killMessageHudPosition::set)
                .build());

        killMessageCategory.addEntry(entryBuilder.startIntSlider(
                Component.translatable("config.blockoffensive.client.kill_message.show_time"),
                BOConfig.client.messageShowTime.get(), 1, 60)
                .setDefaultValue(5)
                .setTooltip(Component.translatable("config.blockoffensive.client.kill_message.show_time.tooltip"))
                .setSaveConsumer(BOConfig.client.messageShowTime::set)
                .build());

        killMessageCategory.addEntry(entryBuilder.startIntSlider(
                Component.translatable("config.blockoffensive.client.kill_message.max_count"),
                BOConfig.client.maxShowCount.get(), 1, 10)
                .setDefaultValue(5)
                .setTooltip(Component.translatable("config.blockoffensive.client.kill_message.max_count.tooltip"))
                .setSaveConsumer(BOConfig.client.maxShowCount::set)
                .build());

        // Kill Icon 子分类
        ConfigCategory killIconCategory = root.getOrCreateCategory(Component.translatable("config.blockoffensive.client.kill_icon"));

        killIconCategory.addEntry(entryBuilder.startBooleanToggle(
                Component.translatable("config.blockoffensive.client.kill_icon.enabled"),
                BOConfig.client.killIconHudEnabled.get())
                .setDefaultValue(true)
                .setTooltip(Component.translatable("config.blockoffensive.client.kill_icon.enabled.tooltip"))
                .setSaveConsumer(BOConfig.client.killIconHudEnabled::set)
                .build());
    }

    private static void initCommon(ConfigBuilder root, ConfigEntryBuilder entryBuilder) {
        // 脚步声配置
        ConfigCategory stepCategory = root.getOrCreateCategory(Component.translatable("config.blockoffensive.common.step_sound"));

        stepCategory.addEntry(entryBuilder.startDoubleField(
                Component.translatable("config.blockoffensive.common.step_sound.teammate_muffled"),
                BOConfig.common.teammateMuffledStepVolume.get())
                .setMin(0.0).setMax(10.0)
                .setDefaultValue(0.05)
                .setTooltip(Component.translatable("config.blockoffensive.common.step_sound.teammate_muffled.tooltip"))
                .setSaveConsumer(BOConfig.common.teammateMuffledStepVolume::set)
                .build());

        stepCategory.addEntry(entryBuilder.startDoubleField(
                Component.translatable("config.blockoffensive.common.step_sound.teammate_normal"),
                BOConfig.common.teammateStepVolume.get())
                .setMin(0.0).setMax(10.0)
                .setDefaultValue(0.15)
                .setTooltip(Component.translatable("config.blockoffensive.common.step_sound.teammate_normal.tooltip"))
                .setSaveConsumer(BOConfig.common.teammateStepVolume::set)
                .build());

        stepCategory.addEntry(entryBuilder.startDoubleField(
                Component.translatable("config.blockoffensive.common.step_sound.enemy_muffled"),
                BOConfig.common.enemyMuffledStepVolume.get())
                .setMin(0.0).setMax(10.0)
                .setDefaultValue(0.4)
                .setTooltip(Component.translatable("config.blockoffensive.common.step_sound.enemy_muffled.tooltip"))
                .setSaveConsumer(BOConfig.common.enemyMuffledStepVolume::set)
                .build());

        stepCategory.addEntry(entryBuilder.startDoubleField(
                Component.translatable("config.blockoffensive.common.step_sound.enemy_normal"),
                BOConfig.common.enemyStepVolume.get())
                .setMin(0.0).setMax(10.0)
                .setDefaultValue(1.2)
                .setTooltip(Component.translatable("config.blockoffensive.common.step_sound.enemy_normal.tooltip"))
                .setSaveConsumer(BOConfig.common.enemyStepVolume::set)
                .build());

        // C4 配置
        ConfigCategory c4Category = root.getOrCreateCategory(Component.translatable("config.blockoffensive.common.c4"));

        c4Category.addEntry(entryBuilder.startIntSlider(
                Component.translatable("config.blockoffensive.common.c4.fuse_time"),
                BOConfig.common.fuseTime.get(), 1, 3200)
                .setDefaultValue(800)
                .setTextGetter(value -> Component.literal(value + " ticks (" + (value / 20) + "s)"))
                .setTooltip(Component.translatable("config.blockoffensive.common.c4.fuse_time.tooltip"))
                .setSaveConsumer(BOConfig.common.fuseTime::set)
                .build());

        // 游戏规则配置
        ConfigCategory gameRulesCategory = root.getOrCreateCategory(Component.translatable("config.blockoffensive.common.game_rules"));

        gameRulesCategory.addEntry(entryBuilder.startBooleanToggle(
                Component.translatable("config.blockoffensive.common.game_rules.keep_inventory"),
                BOConfig.common.keepInventory.get())
                .setDefaultValue(true)
                .setTooltip(Component.translatable("config.blockoffensive.common.game_rules.keep_inventory.tooltip"))
                .setSaveConsumer(BOConfig.common.keepInventory::set)
                .build());

        gameRulesCategory.addEntry(entryBuilder.startBooleanToggle(
                Component.translatable("config.blockoffensive.common.game_rules.immediate_respawn"),
                BOConfig.common.immediateRespawn.get())
                .setDefaultValue(true)
                .setTooltip(Component.translatable("config.blockoffensive.common.game_rules.immediate_respawn.tooltip"))
                .setSaveConsumer(BOConfig.common.immediateRespawn::set)
                .build());

        gameRulesCategory.addEntry(entryBuilder.startBooleanToggle(
                Component.translatable("config.blockoffensive.common.game_rules.daylight_cycle"),
                BOConfig.common.daylightCycle.get())
                .setDefaultValue(false)
                .setTooltip(Component.translatable("config.blockoffensive.common.game_rules.daylight_cycle.tooltip"))
                .setSaveConsumer(BOConfig.common.daylightCycle::set)
                .build());

        gameRulesCategory.addEntry(entryBuilder.startBooleanToggle(
                Component.translatable("config.blockoffensive.common.game_rules.weather_cycle"),
                BOConfig.common.weatherCycle.get())
                .setDefaultValue(false)
                .setTooltip(Component.translatable("config.blockoffensive.common.game_rules.weather_cycle.tooltip"))
                .setSaveConsumer(BOConfig.common.weatherCycle::set)
                .build());

        gameRulesCategory.addEntry(entryBuilder.startBooleanToggle(
                Component.translatable("config.blockoffensive.common.game_rules.mob_spawning"),
                BOConfig.common.mobSpawning.get())
                .setDefaultValue(false)
                .setTooltip(Component.translatable("config.blockoffensive.common.game_rules.mob_spawning.tooltip"))
                .setSaveConsumer(BOConfig.common.mobSpawning::set)
                .build());

        gameRulesCategory.addEntry(entryBuilder.startBooleanToggle(
                Component.translatable("config.blockoffensive.common.game_rules.natural_regeneration"),
                BOConfig.common.naturalRegeneration.get())
                .setDefaultValue(false)
                .setTooltip(Component.translatable("config.blockoffensive.common.game_rules.natural_regeneration.tooltip"))
                .setSaveConsumer(BOConfig.common.naturalRegeneration::set)
                .build());

        gameRulesCategory.addEntry(entryBuilder.startBooleanToggle(
                Component.translatable("config.blockoffensive.common.game_rules.hard_difficulty"),
                BOConfig.common.hardDifficulty.get())
                .setDefaultValue(true)
                .setTooltip(Component.translatable("config.blockoffensive.common.game_rules.hard_difficulty.tooltip"))
                .setSaveConsumer(BOConfig.common.hardDifficulty::set)
                .build());

        // Web服务器配置
        ConfigCategory webCategory = root.getOrCreateCategory(Component.translatable("config.blockoffensive.common.web_server"));

        webCategory.addEntry(entryBuilder.startBooleanToggle(
                Component.translatable("config.blockoffensive.common.web_server.enabled"),
                BOConfig.common.webServerEnabled.get())
                .setDefaultValue(false)
                .setTooltip(Component.translatable("config.blockoffensive.common.web_server.enabled.tooltip"))
                .setSaveConsumer(BOConfig.common.webServerEnabled::set)
                .build());

        webCategory.addEntry(entryBuilder.startIntField(
                Component.translatable("config.blockoffensive.common.web_server.port"),
                BOConfig.common.webServerPort.get())
                .setMin(1).setMax(65535)
                .setDefaultValue(8080)
                .setTooltip(Component.translatable("config.blockoffensive.common.web_server.port.tooltip"))
                .setSaveConsumer(BOConfig.common.webServerPort::set)
                .build());
    }

    private static String getPositionName(int position) {
        return switch (position) {
            case 1 -> "Top Left";
            case 2 -> "Top Right";
            case 3 -> "Bottom Left";
            case 4 -> "Bottom Right";
            default -> "Unknown";
        };
    }

    @SuppressWarnings("removal")
    public static void registerModsPage() {
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class, () -> new ConfigScreenHandler.ConfigScreenFactory((client, parent) -> getConfigScreen(parent)));
    }

    public static Screen getConfigScreen(@Nullable Screen parent) {
        return getConfigBuilder().setParentScreen(parent).build();
    }
}
