package com.ptcrys.blockoffensive.intro;

import com.ptcrys.blockoffensive.BlockOffensive;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class IntroSoundEvents {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, BlockOffensive.MODID);
    public static final RegistryObject<SoundEvent> INTRO_CT = register("intro.ct");
    public static final RegistryObject<SoundEvent> INTRO_T = register("intro.t");

    private IntroSoundEvents() {
    }

    public static SoundEvent forSide(IntroTeamSide side) {
        return (side == IntroTeamSide.T ? INTRO_T : INTRO_CT).get();
    }

    public static ResourceLocation idForSide(IntroTeamSide side) {
        return ResourceLocation.tryBuild(BlockOffensive.MODID, side == IntroTeamSide.T ? "intro.t" : "intro.ct");
    }

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.tryBuild(BlockOffensive.MODID, name)));
    }
}
