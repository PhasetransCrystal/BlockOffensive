package net.ptcrys.blockoffensive.sound;

import net.ptcrys.blockoffensive.BlockOffensive;
import net.ptcrys.fpsmatch.common.event.register.RegisterFPSMSaveDataEvent;
import net.ptcrys.fpsmatch.core.persistence.FPSMDataManager;
import net.ptcrys.fpsmatch.core.persistence.SaveHolder;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.google.common.collect.Maps;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

@SuppressWarnings("all")
@Mod.EventBusSubscriber(modid = BlockOffensive.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MVPMusicManager {

    public static final Codec<MVPMusicManager> CODEC = Codec.unboundedMap(Codec.STRING, Entry.CODEC).xmap(MVPMusicManager::new,
            (manager) -> manager.mvpMusicMap);
    private final Map<String, Entry> mvpMusicMap;
    private static volatile MVPMusicManager INSTANCE = new MVPMusicManager();

    public static MVPMusicManager getInstance() {
        return INSTANCE;
    }

    public MVPMusicManager() {
        mvpMusicMap = Maps.newHashMap();
    }

    public MVPMusicManager(Map<String, Entry> mvpMusicMap) {
        this.mvpMusicMap = Maps.newHashMap();
        this.mvpMusicMap.putAll(mvpMusicMap);
    }

    public void addMvpMusic(String uuid, ResourceLocation music) {
        addMvpMusic(uuid, music, music.toString());
    }

    public void addMvpMusic(String uuid, ResourceLocation music, String musicName) {
        mvpMusicMap.put(uuid, new Entry(music, musicName));
    }

    public ResourceLocation getMvpMusic(String uuid) {
        return this.mvpMusicMap.getOrDefault(uuid, Entry.EMPTY).music();
    }

    public String getMvpMusicName(String uuid) {
        return this.mvpMusicMap.getOrDefault(uuid, Entry.EMPTY).name();
    }

    public boolean playerHasMvpMusic(String uuid) {
        return this.mvpMusicMap.containsKey(uuid);
    }

    @SubscribeEvent
    public static void onDataRegister(RegisterFPSMSaveDataEvent event) {
        event.registerData(MVPMusicManager.class, "MvpMusicData",
                new SaveHolder.Builder<>(CODEC)
                        .withLoadHandler(MVPMusicManager::read)
                        .withSaveHandler(MVPMusicManager::write)
                        .withMergeHandler(MVPMusicManager::merge)
                        .withVersion(0)
                        .isGlobal(true)
                        .build());
    }

    private void read() {
        INSTANCE = this;
    }

    private void read(MVPMusicManager mvpMusicManager) {
        this.mvpMusicMap.putAll(mvpMusicManager.mvpMusicMap);
    }

    public static void write(FPSMDataManager manager) {
        manager.saveData(INSTANCE, "data", false);
    }

    public static MVPMusicManager merge(@Nullable MVPMusicManager old, MVPMusicManager newer) {
        if (old == null) {
            return newer;
        } else {
            old.read(newer);
            return old;
        }
    }

    public record Entry(ResourceLocation music, String name) {

        private static final Entry EMPTY = new Entry(ResourceLocation.tryBuild("fpsmatch", "empty"), "fpsmatch:empty");
        private static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("music").forGetter(Entry::music),
                Codec.STRING.fieldOf("name").forGetter(Entry::name)).apply(instance, Entry::new));
    }
}
