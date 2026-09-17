package net.ptcrys.blockoffensive.client.mvp;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

/**
 * MVP 本地音乐的格式检测 / 时长解析 / 转码 / 元数据读取。
 * 魔数检测（不信任扩展名）：OGG=OggS、MP3=ID3 或 0xFFEx、WAV=RIFF+WAVE。
 * MP3 解码：JLayer（javazoom.jl.*，手动解码为 PCM，不依赖会冲突的 mp3spi SPI）；
 * WAV 解码：JDK 原生 JavaSound；
 * OGG 编码：VorbisSPI（javazoom.spi.vorbis）+ JOrbis，经 AudioSystem.write 输出；
 * 元数据：MP3 手写解析 ID3v2（TIT2/TPE1）与 ID3v1；OGG 读 Vorbis Comments。
 * 所有操作在 JVM 内完成，不调用外部进程。
 */
@OnlyIn(Dist.CLIENT)
public final class MvpMusicConverter {

    private static final Logger LOGGER = LogUtils.getLogger();

    public enum Format {
        OGG,
        MP3,
        WAV
    }

    public record Meta(String title, String artist) {}

    private MvpMusicConverter() {}

    /** 读取文件头魔数并判断真实格式；无法识别返回 null。 */
    public static Format detectFormat(Path source) throws IOException {
        if (!Files.isRegularFile(source)) {
            throw new IOException("不是普通文件");
        }
        byte[] head;
        try (InputStream in = Files.newInputStream(source)) {
            head = in.readNBytes(12);
        }
        if (head.length < 12) {
            throw new IOException("文件过小，无法识别格式");
        }
        if (head[0] == 'O' && head[1] == 'g' && head[2] == 'g' && head[3] == 'S') {
            return Format.OGG;
        }
        if (head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F' && head[8] == 'W' && head[9] == 'A' && head[10] == 'V' && head[11] == 'E') {
            return Format.WAV;
        }
        if ((head[0] == 'I' && head[1] == 'D' && head[2] == '3') || (head[0] == (byte) 0xFF && (head[1] & 0xE0) == 0xE0)) {
            return Format.MP3;
        }
        return null;
    }

    /** 解析音频时长（秒，向上取整）。无法解析时返回 0（调用方不再当作错误拒绝）。 */
    public static int durationSeconds(Path source, Format format) {
        try {
            switch (format) {
                case OGG -> {
                    AudioFileFormat aff = AudioSystem.getAudioFileFormat(source.toFile());
                    Object dur = aff.properties().get("duration");
                    if (dur instanceof Number n && n.doubleValue() > 0) {
                        // 兼容微秒（如 12000000）与秒（如 12.5）两种单位
                        double v = n.doubleValue();
                        if (v > 10000.0D) {
                            v /= 1_000_000.0D;
                        }
                        return (int) Math.ceil(v);
                    }
                    // VorbisSPI 未提供 duration 时，尝试从流 frameLength 推算
                    try (AudioInputStream ais = AudioSystem.getAudioInputStream(source.toFile())) {
                        long frames = ais.getFrameLength();
                        float rate = ais.getFormat().getFrameRate();
                        if (frames > 0 && rate > 0) {
                            return (int) Math.ceil(frames / rate);
                        }
                    }
                }
                case WAV -> {
                    try (AudioInputStream ais = AudioSystem.getAudioInputStream(source.toFile())) {
                        long frames = ais.getFrameLength();
                        float rate = ais.getFormat().getFrameRate();
                        if (frames > 0 && rate > 0) {
                            return (int) Math.ceil(frames / rate);
                        }
                    }
                }
                case MP3 -> {
                    return mp3DurationSeconds(source);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("[MvpMusic] duration parse failed for {}: {}", source.getFileName(), e.toString());
        }
        return 0;
    }

    /** 用 JLayer 扫描 MP3 帧头统计总时长（秒，向上取整）。 */
    private static int mp3DurationSeconds(Path source) throws Exception {
        try (InputStream in = Files.newInputStream(source)) {
            Bitstream bs = new Bitstream(new BufferedInputStream(in));
            long totalMs = 0;
            while (true) {
                Header header;
                try {
                    header = bs.readFrame();
                } catch (Exception e) {
                    break;
                }
                if (header == null) {
                    break;
                }
                float ms = header.ms_per_frame();
                if (ms > 0) {
                    totalMs += ms; // 跳过 free-format 等返回 -1/0 的帧
                }
                bs.closeFrame();
            }
            return (int) Math.ceil(totalMs / 1000.0D);
        }
    }

    /**
     * 将 MP3/WAV 解码为 PCM 并编码为 OGG Vorbis，输出到目标文件。
     * 依赖随 Mod 打包的 JLayer / VorbisSPI / JOrbis / Tritonus（纯 Java）。
     */
    public static void convertToOgg(Path source, Format format, Path out) throws Exception {
        AudioInputStream pcm = switch (format) {
            case WAV -> AudioSystem.getAudioInputStream(source.toFile());
            case MP3 -> decodeMp3ToPcm(source);
            default -> throw new IOException("OGG 无需转换");
        };
        try (pcm) {
            AudioFormat src = pcm.getFormat();
            AudioFormat pcm16 = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    src.getSampleRate(),
                    16,
                    src.getChannels(),
                    src.getChannels() * 2,
                    src.getSampleRate(),
                    false);
            AudioInputStream toWrite = src.matches(pcm16) ? pcm : AudioSystem.getAudioInputStream(pcm16, pcm);
            AudioFileFormat.Type oggType = new AudioFileFormat.Type("OGG", "ogg");
            AudioSystem.write(toWrite, oggType, out.toFile());
            if (toWrite != pcm) {
                toWrite.close();
            }
        }
    }

    /** JLayer 将 MP3 解码为 16bit PCM AudioInputStream。 */
    private static AudioInputStream decodeMp3ToPcm(Path mp3) throws Exception {
        try (InputStream in = Files.newInputStream(mp3)) {
            Bitstream bitstream = new Bitstream(new BufferedInputStream(in));
            Decoder decoder = new Decoder();
            ByteArrayOutputStream pcmOut = new ByteArrayOutputStream(1 << 20);
            int sampleRate = 44100;
            int channels = 2;
            while (true) {
                Header header;
                try {
                    header = bitstream.readFrame();
                } catch (Exception e) {
                    break;
                }
                if (header == null) {
                    break;
                }
                SampleBuffer buffer = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                if (buffer == null) {
                    break;
                }
                sampleRate = decoder.getOutputFrequency();
                channels = decoder.getOutputChannels();
                int sampleCount = buffer.getBufferLength();
                short[] pcm = buffer.getBuffer();
                ByteBuffer bb = ByteBuffer.allocate(sampleCount * 2).order(ByteOrder.LITTLE_ENDIAN);
                for (int i = 0; i < sampleCount; i++) {
                    bb.putShort(pcm[i]);
                }
                pcmOut.write(bb.array());
                bitstream.closeFrame();
            }
            byte[] data = pcmOut.toByteArray();
            AudioFormat fmt = new AudioFormat((float) sampleRate, 16, channels, true, false);
            int frameSize = fmt.getFrameSize();
            return new AudioInputStream(new ByteArrayInputStream(data), fmt, frameSize > 0 ? data.length / frameSize : -1);
        }
    }

    /** 从音频元数据读取标题与艺术家（MP3 ID3 / OGG Vorbis Comments；WAV 一般无）。 */
    public static Meta readMeta(Path source, Format format) {
        try {
            if (format == Format.MP3) {
                return readMp3Meta(source);
            }
            if (format == Format.OGG) {
                AudioFileFormat aff = AudioSystem.getAudioFileFormat(source.toFile());
                Map<String, Object> props = aff.properties();
                String title = stringProp(props.get("TITLE"));
                String artist = stringProp(props.get("ARTIST"));
                if (title == null) title = stringProp(props.get("title"));
                if (artist == null) artist = stringProp(props.get("artist"));
                return (title == null && artist == null) ? null : new Meta(title, artist);
            }
        } catch (Exception e) {
            LOGGER.warn("[MvpMusic] meta read failed for {}: {}", source.getFileName(), e.toString());
        }
        return null;
    }

    // ================= MP3 ID3 元数据（手写解析） =================

    private static Meta readMp3Meta(Path mp3) {
        Meta v2 = readId3v2(mp3);
        if (v2 != null && (v2.title() != null || v2.artist() != null)) {
            return v2;
        }
        return readId3v1(mp3);
    }

    private static Meta readId3v2(Path mp3) {
        try (InputStream in = Files.newInputStream(mp3)) {
            byte[] head = in.readNBytes(10);
            if (head.length < 10 || head[0] != 'I' || head[1] != 'D' || head[2] != '3') {
                return null;
            }
            int size = ((head[6] & 0x7F) << 21) | ((head[7] & 0x7F) << 14) | ((head[8] & 0x7F) << 7) | (head[9] & 0x7F);
            if (size <= 0) {
                return null;
            }
            byte[] body = new byte[Math.min(size, 1 << 20)];
            int read = in.read(body);
            if (read <= 0) {
                return null;
            }
            ByteBuffer buf = ByteBuffer.wrap(body, 0, read);
            String title = null;
            String artist = null;
            while (buf.remaining() >= 10) {
                byte[] fid = new byte[4];
                buf.get(fid);
                if (fid[0] == 0) {
                    break;
                }
                int fsize = ((buf.get() & 0xFF) << 24) | ((buf.get() & 0xFF) << 16) | ((buf.get() & 0xFF) << 8) | (buf.get() & 0xFF);
                buf.getShort(); // flags
                if (fsize < 0 || fsize > buf.remaining()) {
                    break;
                }
                byte[] fdata = new byte[fsize];
                buf.get(fdata);
                String id = new String(fid, StandardCharsets.US_ASCII);
                if ("TIT2".equals(id) && title == null) {
                    title = decodeId3Text(fdata);
                } else if ("TPE1".equals(id) && artist == null) {
                    artist = decodeId3Text(fdata);
                }
            }
            return (title == null && artist == null) ? null : new Meta(title, artist);
        } catch (Exception e) {
            return null;
        }
    }

    private static String decodeId3Text(byte[] data) {
        if (data.length < 1) {
            return null;
        }
        int enc = data[0] & 0xFF;
        byte[] text = Arrays.copyOfRange(data, 1, data.length);
        String s = switch (enc) {
            case 1 -> new String(text, StandardCharsets.UTF_16);
            case 2 -> new String(text, StandardCharsets.UTF_16BE);
            case 3 -> new String(text, StandardCharsets.UTF_8);
            default -> new String(text, StandardCharsets.ISO_8859_1);
        };
        s = s.replace("\u0000", "").trim();
        return s.isEmpty() ? null : s;
    }

    private static Meta readId3v1(Path mp3) {
        try (RandomAccessFile raf = new RandomAccessFile(mp3.toFile(), "r")) {
            long len = raf.length();
            if (len < 128) {
                return null;
            }
            raf.seek(len - 128);
            byte[] tag = new byte[128];
            raf.readFully(tag);
            if (tag[0] != 'T' || tag[1] != 'A' || tag[2] != 'G') {
                return null;
            }
            String title = new String(tag, 3, 30, StandardCharsets.ISO_8859_1).trim();
            String artist = new String(tag, 33, 30, StandardCharsets.ISO_8859_1).trim();
            if (title.isEmpty() && artist.isEmpty()) {
                return null;
            }
            return new Meta(title.isEmpty() ? null : title, artist.isEmpty() ? null : artist);
        } catch (Exception e) {
            return null;
        }
    }

    private static String stringProp(Object o) {
        return o instanceof String s && !s.isBlank() ? s.trim() : null;
    }
}
