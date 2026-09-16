package com.ptcrys.blockoffensive.intro;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class IntroMotionProfile {
    public static final int AUTHORED_STRIDE_TICKS = 112;
    public static final int MAX_ANIMATION_TICK = 140;

    private IntroMotionProfile() {
    }

    public static double linearProgress(int movementTick, int durationTicks) {
        return Mth.clamp(movementTick / (double) Math.max(1, durationTicks), 0.0, 1.0);
    }

    public static double cameraProgress(int movementTick, int durationTicks) {
        double value = linearProgress(movementTick, durationTicks);
        return value < 0.5 ? 2.0 * value * value : 1.0 - Math.pow(-2.0 * value + 2.0, 2.0) / 2.0;
    }

    public static double strideProgress(IntroPhase phase, int movementTick, int durationTicks) {
        double p = linearProgress(movementTick, durationTicks);
        double rampIn = phase == IntroPhase.SWITCH ? 0.12 : 0.10;
        double rampOut = phase == IntroPhase.SWITCH ? 0.17 : 0.15;
        double minVelocity = phase == IntroPhase.SWITCH ? 0.50 : 0.46;
        double startDistance = rampIntegral(rampIn, rampIn, minVelocity);
        double middleEnd = 1.0 - rampOut;
        double distance;
        if (p <= rampIn) {
            distance = rampIntegral(p, rampIn, minVelocity);
        } else if (p <= middleEnd) {
            distance = startDistance + (p - rampIn);
        } else {
            double middleDistance = startDistance + Math.max(0.0, middleEnd - rampIn);
            distance = middleDistance + stopRampIntegral(p - middleEnd, rampOut, minVelocity);
        }
        double total = startDistance + Math.max(0.0, middleEnd - rampIn) + stopRampIntegral(rampOut, rampOut, minVelocity);
        return Mth.clamp(distance / Math.max(0.0001, total), 0.0, 1.0);
    }

    public static Vec3 rootPosition(IntroSequence.PlayerPath path, IntroPhase phase, int movementTick, int durationTicks) {
        return path.start().lerp(path.end(), strideProgress(phase, movementTick, durationTicks));
    }

    public static int animationEndTick(int durationTicks) {
        return durationTicks > AUTHORED_STRIDE_TICKS ? Math.min(MAX_ANIMATION_TICK, Math.max(AUTHORED_STRIDE_TICKS, durationTicks)) : AUTHORED_STRIDE_TICKS;
    }

    public static int animationTick(IntroPhase phase, int formationIndex, int movementTick, int durationTicks) {
        int endTick = animationEndTick(durationTicks);
        int baseTick = Mth.clamp((int) Math.round(strideProgress(phase, movementTick, durationTicks) * endTick), 0, endTick);
        int phaseWindowEnd = Math.max(1, Math.min(AUTHORED_STRIDE_TICKS - 20, endTick - 28));
        if (baseTick >= phaseWindowEnd) {
            return baseTick;
        }
        int offset = formationPhaseOffset(phase, formationIndex);
        return Mth.clamp(baseTick + offset, 0, phaseWindowEnd - 1);
    }

    public static int formationPhaseOffset(IntroPhase phase, int formationIndex) {
        if (formationIndex < 0) {
            return 0;
        }
        int[] offsets = phase == IntroPhase.SWITCH
                ? new int[]{4, 1, 6, 2, 7}
                : new int[]{0, 4, 7, 2, 5};
        return offsets[Math.floorMod(formationIndex, offsets.length)];
    }

    private static double rampIntegral(double progress, double length, double minVelocity) {
        double q = Mth.clamp(progress / Math.max(0.0001, length), 0.0, 1.0);
        return minVelocity * progress + (1.0 - minVelocity) * length * smoothstepIntegral(q);
    }

    private static double stopRampIntegral(double progress, double length, double minVelocity) {
        double q = Mth.clamp(progress / Math.max(0.0001, length), 0.0, 1.0);
        return progress - (1.0 - minVelocity) * length * smoothstepIntegral(q);
    }

    private static double smoothstepIntegral(double value) {
        return value * value * value - 0.5 * value * value * value * value;
    }
}
