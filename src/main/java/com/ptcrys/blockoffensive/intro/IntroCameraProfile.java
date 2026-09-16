package com.ptcrys.blockoffensive.intro;

import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class IntroCameraProfile {
    public static final String PROFILE_ID = "waist_video_reference_dolly";
    private static final double SWEEP_END = 0.40;
    private static final double PUSH_END = 0.70;
    private static final double START_CAMERA_HEIGHT = 1.02;
    private static final double END_CAMERA_HEIGHT = 1.05;
    private static final double LOOK_HEIGHT = 1.31;
    private static final double PLAYER_LOOK_HEIGHT = 1.43;
    private static final double MIN_CAMERA_HEIGHT = 0.90;
    private static final double MAX_CAMERA_HEIGHT = 1.22;

    private IntroCameraProfile() {
    }

    public static Vec3 plannedStart(List<IntroSequence.PlayerPath> paths, IntroPhase phase, int durationTicks, double sideSign) {
        Frame frame = frame(paths, phase, 0, durationTicks);
        return cameraPoint(frame, defaultStartDistance(phase), defaultStartSide(phase) * signedUnit(sideSign), START_CAMERA_HEIGHT);
    }

    public static Vec3 plannedEnd(List<IntroSequence.PlayerPath> paths, IntroPhase phase, int durationTicks, double sideSign) {
        Frame frame = frame(paths, phase, durationTicks, durationTicks);
        return cameraPoint(frame, defaultEndDistance(phase), 0.0 * signedUnit(sideSign), END_CAMERA_HEIGHT);
    }

    public static Pose pose(List<IntroSequence.PlayerPath> paths, IntroPhase phase, int movementTick, int durationTicks, Vec3 cameraStart, Vec3 cameraEnd) {
        int safeDuration = Math.max(1, durationTicks);
        if (paths == null || paths.isEmpty()) {
            return fallbackPose(movementTick, safeDuration, cameraStart, cameraEnd);
        }
        Frame frame = frame(paths, phase, movementTick, safeDuration);
        CameraPlan plan = cameraPlan(paths, phase, safeDuration, cameraStart, cameraEnd);
        double progress = IntroMotionProfile.cameraProgress(movementTick, safeDuration);
        double sweep = smoothstep(Mth.clamp(progress / SWEEP_END, 0.0, 1.0));
        double push = smoothstep(Mth.clamp((progress - SWEEP_END) / (PUSH_END - SWEEP_END), 0.0, 1.0));
        double distance;
        double lateral;
        if (progress <= SWEEP_END) {
            distance = lerp(sweep, plan.startDistance(), plan.midDistance());
            lateral = lerp(sweep, plan.startLateral(), 0.0);
        } else if (progress <= PUSH_END) {
            distance = lerp(push, plan.midDistance(), plan.endDistance());
            lateral = lerp(push, 0.0, plan.endLateral());
        } else {
            distance = plan.endDistance();
            lateral = plan.endLateral();
        }
        double height = Mth.clamp(lerp(push, plan.startHeight(), plan.endHeight()), MIN_CAMERA_HEIGHT, MAX_CAMERA_HEIGHT);
        Vec3 position = cameraPoint(frame, distance, lateral, height);
        Vec3 lookAt = lookAtPoint(frame, progress);
        float yaw = lookYaw(position, lookAt);
        float pitch = Mth.clamp(lookPitch(position, lookAt), -10.0f, 3.0f);
        return new Pose(position, yaw, pitch, lookAt, distance, lateral);
    }

    private static Pose fallbackPose(int movementTick, int durationTicks, Vec3 cameraStart, Vec3 cameraEnd) {
        Vec3 start = cameraStart == null ? Vec3.ZERO : cameraStart;
        Vec3 end = cameraEnd == null ? start : cameraEnd;
        double progress = IntroMotionProfile.cameraProgress(movementTick, durationTicks);
        Vec3 position = start.lerp(end, progress);
        Vec3 lookAt = end;
        float yaw = lookYaw(position, lookAt);
        float pitch = Mth.clamp(lookPitch(position, lookAt), -10.0f, 3.0f);
        return new Pose(position, yaw, pitch, lookAt, position.distanceTo(lookAt), 0.0);
    }

    public static float playerYaw(Vec3 playerFeet, Vec3 cameraPosition) {
        Vec3 target = new Vec3(cameraPosition.x, playerFeet.y, cameraPosition.z);
        return lookYaw(playerFeet, target);
    }

    public static float playerPitch(Vec3 playerFeet, Vec3 cameraPosition) {
        Vec3 head = playerFeet.add(0.0, PLAYER_LOOK_HEIGHT, 0.0);
        return Mth.clamp(lookPitch(head, cameraPosition), -14.0f, 14.0f);
    }

    public static float lookYaw(Vec3 from, Vec3 to) {
        Vec3 delta = to.subtract(from);
        return (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
    }

    public static float lookPitch(Vec3 from, Vec3 to) {
        Vec3 delta = to.subtract(from);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        return (float) (-Math.toDegrees(Math.atan2(delta.y, horizontal)));
    }

    private static CameraPlan cameraPlan(List<IntroSequence.PlayerPath> paths, IntroPhase phase, int durationTicks, Vec3 cameraStart, Vec3 cameraEnd) {
        Frame startFrame = frame(paths, phase, 0, durationTicks);
        Frame endFrame = frame(paths, phase, durationTicks, durationTicks);
        double sideSign = sideSign(cameraStart, startFrame);
        double startDistance = projected(cameraStart, startFrame, startFrame.forward(), defaultStartDistance(phase));
        double endDistance = projected(cameraEnd, endFrame, endFrame.forward(), defaultEndDistance(phase));
        double startLateral = projected(cameraStart, startFrame, startFrame.right(), defaultStartSide(phase) * sideSign);
        double endLateral = projected(cameraEnd, endFrame, endFrame.right(), 0.0);
        double startHeight = projectedHeight(cameraStart, startFrame, START_CAMERA_HEIGHT);
        double endHeight = projectedHeight(cameraEnd, endFrame, END_CAMERA_HEIGHT);
        startDistance = Mth.clamp(startDistance, 5.25, 7.55);
        endDistance = Mth.clamp(endDistance, 2.75, 3.65);
        startLateral = Mth.clamp(startLateral, -3.75, 3.75);
        if (Math.abs(startLateral) < 1.0) {
            startLateral = defaultStartSide(phase) * sideSign;
        }
        endLateral = Mth.clamp(endLateral, -0.35, 0.35);
        double defaultMid = phase == IntroPhase.SWITCH ? 4.55 : 4.85;
        double midDistance = Mth.clamp(defaultMid, endDistance + 0.85, Math.max(endDistance + 0.9, startDistance - 0.55));
        return new CameraPlan(startDistance, midDistance, endDistance, startLateral, endLateral, startHeight, endHeight);
    }

    private static double projected(Vec3 anchor, Frame frame, Vec3 axis, double fallback) {
        if (anchor == null) {
            return fallback;
        }
        double value = anchor.subtract(frame.feetCenter()).dot(axis);
        return Double.isFinite(value) ? value : fallback;
    }

    private static double projectedHeight(Vec3 anchor, Frame frame, double fallback) {
        if (anchor == null) {
            return fallback;
        }
        double value = anchor.y - frame.feetCenter().y;
        return Double.isFinite(value) ? Mth.clamp(value, MIN_CAMERA_HEIGHT, MAX_CAMERA_HEIGHT) : fallback;
    }

    private static double sideSign(Vec3 cameraStart, Frame frame) {
        if (cameraStart == null) {
            return 1.0;
        }
        double lateral = cameraStart.subtract(frame.feetCenter()).dot(frame.right());
        if (Math.abs(lateral) < 0.05 || !Double.isFinite(lateral)) {
            return 1.0;
        }
        return lateral < 0.0 ? -1.0 : 1.0;
    }

    private static Frame frame(List<IntroSequence.PlayerPath> paths, IntroPhase phase, int movementTick, int durationTicks) {
        if (paths == null || paths.isEmpty()) {
            Vec3 forward = new Vec3(0.0, 0.0, 1.0);
            return new Frame(Vec3.ZERO, forward, new Vec3(-forward.z, 0.0, forward.x));
        }
        Vec3 center = Vec3.ZERO;
        Vec3 travel = Vec3.ZERO;
        int count = 0;
        for (IntroSequence.PlayerPath path : paths) {
            Vec3 root = IntroMotionProfile.rootPosition(path, phase, movementTick, durationTicks);
            center = center.add(root);
            Vec3 delta = path.end().subtract(path.start());
            travel = travel.add(new Vec3(delta.x, 0.0, delta.z));
            count++;
        }
        center = center.scale(1.0 / Math.max(1, count));
        Vec3 forward = horizontal(travel);
        if (forward.lengthSqr() < 0.0001) {
            forward = new Vec3(0.0, 0.0, 1.0);
        } else {
            forward = forward.normalize();
        }
        Vec3 right = new Vec3(-forward.z, 0.0, forward.x).normalize();
        return new Frame(center, forward, right);
    }

    private static Vec3 horizontal(Vec3 value) {
        return new Vec3(value.x, 0.0, value.z);
    }

    private static Vec3 cameraPoint(Frame frame, double distance, double lateral, double height) {
        return new Vec3(frame.feetCenter().x, frame.feetCenter().y + height, frame.feetCenter().z)
                .add(frame.forward().scale(distance))
                .add(frame.right().scale(lateral));
    }

    private static Vec3 lookAtPoint(Frame frame, double progress) {
        double lead = lerp(progress, 0.16, 0.04);
        return new Vec3(frame.feetCenter().x, frame.feetCenter().y + LOOK_HEIGHT, frame.feetCenter().z)
                .add(frame.forward().scale(lead));
    }

    private static double defaultStartDistance(IntroPhase phase) {
        return phase == IntroPhase.SWITCH ? 6.05 : 6.65;
    }

    private static double defaultEndDistance(IntroPhase phase) {
        return phase == IntroPhase.SWITCH ? 2.92 : 3.05;
    }

    private static double defaultStartSide(IntroPhase phase) {
        return phase == IntroPhase.SWITCH ? 2.95 : 3.20;
    }

    private static double signedUnit(double value) {
        return value < 0.0 ? -1.0 : 1.0;
    }

    private static double smoothstep(double value) {
        return value * value * (3.0 - 2.0 * value);
    }

    private static double lerp(double amount, double start, double end) {
        return start + (end - start) * amount;
    }

    public record Pose(Vec3 position, float yaw, float pitch, Vec3 lookAt, double distance, double lateral) {
    }

    private record Frame(Vec3 feetCenter, Vec3 forward, Vec3 right) {
    }

    private record CameraPlan(double startDistance, double midDistance, double endDistance, double startLateral, double endLateral, double startHeight, double endHeight) {
    }
}
