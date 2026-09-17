package net.ptcrys.blockoffensive.client.screen;

import net.ptcrys.blockoffensive.client.mvp.MvpLocalMusicManager;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.io.File;
import java.nio.file.Path;

import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * MVP 本地音乐设置页（按 F9 打开）：
 * <ul>
 * <li>选择音乐文件（MP3/OGG/WAV）→ 魔数检测真实格式 → 时长校验（≤15 秒）→ OGG 直拷或转码；</li>
 * <li>显示名称自动从元数据读取（🎵 标题 - 艺术家），可手动修改并校验格式；</li>
 * <li>试听当前音乐；保存显示名称到 mvp_music.json。</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public class MvpMusicScreen extends Screen {

    private EditBox nameBox;
    private String status = "";
    private boolean error = false;

    public MvpMusicScreen() {
        super(Component.translatable("blockoffensive.mvp_music.title"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;

        // 确保音乐目录存在（无需玩家手动创建）
        MvpLocalMusicManager.ensureMusicDir();

        // 行1（不重叠）：选择音乐文件 | 试听 | 保存
        this.addRenderableWidget(Button.builder(
                Component.translatable("blockoffensive.mvp_music.select"),
                btn -> openFilePicker())
                .bounds(cx - 140, 40, 100, 20)
                .build());
        this.addRenderableWidget(Button.builder(
                Component.translatable("blockoffensive.mvp_music.preview"),
                btn -> MvpLocalMusicManager.preview())
                .bounds(cx - 34, 40, 44, 20)
                .build());
        this.addRenderableWidget(Button.builder(
                Component.translatable("blockoffensive.mvp_music.save"),
                btn -> saveDisplayName())
                .bounds(cx + 14, 40, 60, 20)
                .build());

        // 行2：从音乐目录选择（自动创建目录，替代难用的系统文件选择器）
        this.addRenderableWidget(Button.builder(
                Component.translatable("blockoffensive.mvp_music.dir"),
                btn -> Minecraft.getInstance().setScreen(new MvpDirSelectScreen(this)))
                .bounds(cx - 140, 66, 254, 20)
                .build());

        // 显示名称输入框（标签在 render 中画于 nameBox 上方）
        this.nameBox = new EditBox(this.font, cx - 140, 102, 254, 18, Component.translatable("blockoffensive.mvp_music.name"));
        MvpLocalMusicManager.MvpMusicData data = MvpLocalMusicManager.getData();
        if (data != null && data.displayName() != null) {
            this.nameBox.setValue(data.displayName());
        } else {
            this.nameBox.setValue(MvpLocalMusicManager.DISPLAY_PREFIX);
        }
        this.nameBox.setMaxLength(64);
        this.addRenderableWidget(this.nameBox);
    }

    private void openFilePicker() {
        // Swing 文件选择器必须在 EDT 打开；结果回调回 MC 主线程处理。
        // 捕获 EDT 内异常并回显到 GUI（避免"点击无效"却看不到原因）。
        SwingUtilities.invokeLater(() -> {
            try {
                JFileChooser chooser = new JFileChooser();
                chooser.setDialogTitle("选择 MVP 音乐文件 (MP3/OGG/WAV, ≤22秒)");
                chooser.setFileFilter(new FileNameExtensionFilter("音频文件 (*.mp3;*.ogg;*.wav)", "mp3", "ogg", "wav"));
                int result = chooser.showOpenDialog(null);
                if (result == JFileChooser.APPROVE_OPTION) {
                    File file = chooser.getSelectedFile();
                    if (file != null) {
                        Minecraft.getInstance().execute(() -> handleSelectedFile(file.toPath()));
                    }
                }
            } catch (Throwable t) {
                com.mojang.logging.LogUtils.getLogger().error("[MvpMusic] file picker failed", t);
                Minecraft.getInstance().execute(() -> {
                    status = "无法打开文件选择器：" + t.getMessage();
                    error = true;
                });
            }
        });
    }

    private void handleSelectedFile(Path path) {
        String err = MvpLocalMusicManager.installMusic(path);
        if (err == null) {
            // 上传服务器：本机成为 MVP 时全场播放本机专属音乐
            MvpLocalMusicManager.uploadToServer();
            status = "安装成功并已上传服务器（本机获得 MVP 时，全场玩家将听到本机专属音乐）。";
            error = false;
            MvpLocalMusicManager.MvpMusicData data = MvpLocalMusicManager.getData();
            if (data != null && data.displayName() != null) {
                this.nameBox.setValue(data.displayName());
            }
        } else {
            status = err;
            error = true;
        }
    }

    private void saveDisplayName() {
        String name = this.nameBox.getValue() == null ? "" : this.nameBox.getValue().trim();
        if (!MvpLocalMusicManager.isValidDisplayName(name)) {
            status = "格式错误：显示名称必须以 🎵 开头，后跟 歌曲名 - 作者名（例如：🎵 流萤Neserin - 失忆海）。";
            error = true;
            return;
        }
        MvpLocalMusicManager.MvpMusicData data = MvpLocalMusicManager.getData();
        if (data == null) {
            status = "请先选择音乐文件再保存。";
            error = true;
            return;
        }
        MvpLocalMusicManager.saveData(new MvpLocalMusicManager.MvpMusicData(
                name, data.sourceFileName(), data.durationSeconds(), data.convertedAt(), data.format()));
        // 重传服务器，确保 MVP 横幅显示最新名字（否则服务器仍用上传时的旧名/占位名）
        MvpLocalMusicManager.uploadToServer();
        status = "已保存显示名称并同步服务器（MVP 横幅将显示该名称）。";
        error = false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        int cx = this.width / 2;

        graphics.drawCenteredString(this.font, Component.translatable("blockoffensive.mvp_music.title").getString(),
                cx, 16, 0xFFFFFFFF);

        // 当前音乐信息
        MvpLocalMusicManager.MvpMusicData data = MvpLocalMusicManager.getData();
        String info;
        if (data == null) {
            info = "♬ 当前未设置 MVP 音乐（未播放任何音乐）。";
        } else {
            info = "当前：[" + data.displayName() + "]  时长 " + data.durationSeconds() + "s  格式 " + data.format() + (data.sourceFileName() != null ? "  源文件 " + data.sourceFileName() : "");
        }
        graphics.drawCenteredString(this.font, info, cx, 132, 0xAAFFFFFF);

        // 显示名称标签（nameBox 上方，左对齐）
        graphics.drawString(this.font, "显示名称（格式：♬ 歌曲名 - 作者名）：", cx - 140, 90, 0xAAFFFFFF);

        // 状态 / 错误
        if (!status.isEmpty()) {
            graphics.drawCenteredString(this.font, status, cx, 152,
                    error ? 0xFFFF5555 : 0xFF55FF55);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
