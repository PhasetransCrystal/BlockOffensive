package com.ptcrys.blockoffensive.client.spec;

import com.ptcrys.fpsmatch.common.client.camera.CameraFrame;
import com.ptcrys.fpsmatch.common.client.camera.CameraPose;
import com.ptcrys.fpsmatch.common.client.camera.CameraRig;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Stateless sampling keeps position, rotation and roll on the same presentation clock. */
public record DeathCameraRig(Vec3 origin, Vec3 pullDirection, double maxPull,
                             float yaw, float pitch) implements CameraRig {
    @Override public CameraFrame sample(double ticks) {
        float fall = smooth((ticks - DeathCameraTimeline.FALL_START_TICKS)
                / (DeathCameraTimeline.FALL_END_TICKS - DeathCameraTimeline.FALL_START_TICKS));
        float shake = 1 - smooth(ticks / DeathCameraTimeline.HIT_TICKS);
        double yawRad = Math.toRadians(yaw);
        Vec3 pos = origin.add(pullDirection.scale(maxPull * fall))
                .add(-Math.cos(yawRad) * 0.34 * fall, -0.85 * fall, -Math.sin(yawRad) * 0.34 * fall)
                .add(Math.sin(ticks * 31) * 0.045 * shake,
                        Math.cos(ticks * 37) * 0.035 * shake, Math.sin(ticks * 27 + 0.7) * 0.045 * shake);
        return CameraFrame.independent(new CameraPose(pos,
                yaw + (float) Math.sin(ticks * 22) * 1.4F * shake,
                pitch + (float) Math.cos(ticks * 25) * 1.2F * shake,
                -12 * fall + (float) Math.sin(ticks * 29) * 1.8F * shake, Double.NaN));
    }

    private static float smooth(double value) {
        float t = (float) Mth.clamp(value, 0, 1);
        return t * t * (3 - 2 * t);
    }
}
