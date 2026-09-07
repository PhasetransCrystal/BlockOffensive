package com.ptcrys.blockoffensive.client.screen;

import com.ptcrys.blockoffensive.client.mvp.MvpLocalMusicManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 从音乐目录（config/blockoffensive/mvp_music/，自动创建）选择音乐文件。
 * 替代难用的系统文件选择器：把 MP3/OGG/WAV 放进该目录即可在此选择。
 */
@OnlyIn(Dist.CLIENT)
public class MvpDirSelectScreen extends Screen {

    private final Screen parent;
    private final List<Path> files;

    public MvpDirSelectScreen(Screen parent) {
        super(Component.translatable("blockoffensive.mvp_music.dir_title"));
        this.parent = parent;
        this.files = scanMusicDir();
    }

    private static List<Path> scanMusicDir() {
        List<Path> list = new ArrayList<>();
        Path dir = MvpLocalMusicManager.getMusicDir();
        if (!Files.isDirectory(dir)) {
            return list;
        }
        try (var stream = Files.list(dir)) {
            stream.filter(p -> {
                String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                // 排除已生成的 mvp_music.ogg / last_mvp_music.ogg 与元数据
                if (n.equals("mvp_music.ogg") || n.equals("last_mvp_music.ogg") || n.endsWith(".json")) {
                    return false;
                }
                return n.endsWith(".mp3") || n.endsWith(".ogg") || n.endsWith(".wav");
            }).sorted().forEach(list::add);
        } catch (Exception e) {
            com.mojang.logging.LogUtils.getLogger().warn("[MvpMusic] scan music dir failed", e);
        }
        return list;
    }

    @Override
    protected void init() {
        int y = 40;
        int btnW = 320;
        for (Path f : files) {
            String name = f.getFileName().toString();
            this.addRenderableWidget(Button.builder(
                            Component.literal(name),
                            btn -> Minecraft.getInstance().execute(() -> {
                                String err = MvpLocalMusicManager.installAndUpload(f);
                                if (err != null) {
                                    status = err;
                                } else {
                                    Minecraft.getInstance().setScreen(parent);
                                }
                            }))
                    .bounds(this.width / 2 - btnW / 2, y, btnW, 20)
                    .build());
            y += 24;
        }
        if (files.isEmpty()) {
            String dir = MvpLocalMusicManager.getMusicDir().toString().replace('\\', '/');
            status = "目录中没有可用的音乐文件。请把 MP3/OGG/WAV 文件放入：\n" + dir + "\n后重新打开本页面（目录已自动创建）。";
        }
        this.addRenderableWidget(Button.builder(
                        Component.literal("返回"),
                        btn -> Minecraft.getInstance().setScreen(parent))
                .bounds(this.width / 2 - 40, this.height - 30, 80, 20)
                .build());
    }

    private String status = "";

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font,
                Component.translatable("blockoffensive.mvp_music.dir_title"),
                this.width / 2, 16, 0xFFFFFFFF);
        if (!status.isEmpty()) {
            graphics.drawCenteredString(this.font, status, this.width / 2, this.height / 2 - 8, 0xAAFFFFFF);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}