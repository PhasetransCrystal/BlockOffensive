package net.ptcrys.blockoffensive.client.spec;

import net.ptcrys.blockoffensive.mixin.client.PostChainAccessor;
import net.ptcrys.blockoffensive.mixin.client.PostPassAccessor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EffectInstance;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;

import com.mojang.blaze3d.shaders.Uniform;

/** Owns the native world post-effect used by the death presentation. */
public final class DeathWorldMask {

    private static final ResourceLocation EFFECT = ResourceLocation.tryBuild(
            "blockoffensive", "shaders/post/death_world_fade.json");
    private static boolean active;
    private static PostChain ownedEffect;

    private DeathWorldMask() {}

    public static void begin() {
        Minecraft client = Minecraft.getInstance();
        if (client.gameRenderer == null) return;
        if (active && client.gameRenderer.currentEffect() == ownedEffect && ownedEffect != null) return;
        active = false;
        client.gameRenderer.loadEffect(EFFECT);
        ownedEffect = client.gameRenderer.currentEffect();
        active = ownedEffect != null;
    }

    /** Native GameRenderer processes the effect at the correct world-render boundary. */
    public static void render(float partialTick) {
        // Kept as a compatibility entry point for older callers. The mixin no longer
        // invokes this method because the native post-effect owns the framebuffer flow.
    }

    public static void updateProgress(float partialTick) {
        if (!active) return;
        Minecraft client = Minecraft.getInstance();
        PostChain current = client.gameRenderer == null ? null : client.gameRenderer.currentEffect();
        if (current == null || current != ownedEffect) return;
        for (PostPass pass : ((PostChainAccessor) current).blockoffensive$getPasses()) {
            EffectInstance effect = ((PostPassAccessor) pass).blockoffensive$getEffect();
            set(effect, "PresentationProgress", KillCamManager.presentationProgress(partialTick));
            set(effect, "FadeProgress", KillCamManager.maskProgress(partialTick));
        }
    }

    private static void set(EffectInstance effect, String name, float value) {
        Uniform uniform = effect.getUniform(name);
        if (uniform != null) uniform.set(value);
    }

    public static void close() {
        Minecraft client = Minecraft.getInstance();
        if (active && client.gameRenderer != null && client.gameRenderer.currentEffect() == ownedEffect) {
            client.gameRenderer.shutdownEffect();
        }
        active = false;
        ownedEffect = null;
    }
}
