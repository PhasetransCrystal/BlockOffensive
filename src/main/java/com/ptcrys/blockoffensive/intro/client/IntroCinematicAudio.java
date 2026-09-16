package com.ptcrys.blockoffensive.intro.client;

import com.ptcrys.blockoffensive.intro.IntroSoundEvents;
import com.ptcrys.blockoffensive.intro.IntroTeamSide;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

public final class IntroCinematicAudio {
    private IntroCinematicAudio() {
    }

    public static SoundInstance play(Minecraft minecraft, IntroTeamSide side) {
        SoundEvent event = IntroSoundEvents.forSide(side);
        SimpleSoundInstance instance = SimpleSoundInstance.forUI(event, 1.0F, 1.0F);
        minecraft.getSoundManager().play(instance);
        return instance;
    }

    public static boolean stop(Minecraft minecraft, SoundInstance instance) {
        if (minecraft == null || instance == null) {
            return false;
        }
        minecraft.getSoundManager().stop(instance);
        return true;
    }

    public static ResourceLocation idForSide(IntroTeamSide side) {
        return IntroSoundEvents.idForSide(side);
    }
}
