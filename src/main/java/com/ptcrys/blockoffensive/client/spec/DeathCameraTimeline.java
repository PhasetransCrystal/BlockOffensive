package com.ptcrys.blockoffensive.client.spec;

/** Client game-time clock for the two-second death presentation. */
public final class DeathCameraTimeline {
    public static final int HIT_TICKS = 5;
    public static final int FALL_START_TICKS = 3;
    public static final int FALL_END_TICKS = 14;
    public static final int FADE_START_TICKS = 14;
    public static final int PRESENTATION_TICKS = 40;
    public static final int INFORMATION_TICKS = 20;
    private static final int TARGET_WAIT_TICKS = 40;
    private int ticks;
    private boolean active;

    public void start() { ticks = 0; active = true; }
    public void reset() { ticks = 0; active = false; }
    public void tick() { if (active) ++ticks; }
    public boolean active() { return active; }
    public boolean showInformation() { return active && ticks < PRESENTATION_TICKS; }
    public boolean waitingForTarget() { return active && ticks >= PRESENTATION_TICKS; }
    public boolean targetWaitExpired() { return active && ticks >= PRESENTATION_TICKS + TARGET_WAIT_TICKS; }
    public boolean shouldRequestTarget() { return waitingForTarget() && (ticks - PRESENTATION_TICKS) % 5 == 0; }

    /** Overall scene fade: no black during the hit/fall, then a smooth 0.7-2.0s fade. */
    public float fadeProgress(float partialTick) {
        if (!active) return 0;
        float time = ticks + Math.max(0, Math.min(1, partialTick));
        return smooth((time - FADE_START_TICKS) / (PRESENTATION_TICKS - FADE_START_TICKS));
    }

    public float presentationProgress(float partialTick) {
        if (!active) return 0;
        float time = ticks + Math.max(0, Math.min(1, partialTick));
        return smooth(time / PRESENTATION_TICKS);
    }

    public float hitProgress(float partialTick) {
        if (!active) return 0;
        float time = ticks + Math.max(0, Math.min(1, partialTick));
        return 1.0F - smooth(time / HIT_TICKS);
    }

    public float fallProgress(float partialTick) {
        if (!active) return 0;
        float time = ticks + Math.max(0, Math.min(1, partialTick));
        return smooth((time - FALL_START_TICKS) / (FALL_END_TICKS - FALL_START_TICKS));
    }

    public float shakeProgress(float partialTick) {
        if (!active) return 0;
        float time = ticks + Math.max(0, Math.min(1, partialTick));
        return time < HIT_TICKS ? 1.0F - smooth(time / HIT_TICKS) : 0.0F;
    }

    public float elapsed(float partialTick) {
        return active ? ticks + Math.max(0, Math.min(1, partialTick)) : 0.0F;
    }

    /** Kept as an API alias for the world-mask renderer. */
    public float maskProgress(float partialTick) {
        return fadeProgress(partialTick);
    }

    private static float smooth(float value) {
        value = Math.max(0.0F, Math.min(1.0F, value));
        return value * value * (3.0F - 2.0F * value);
    }
}
