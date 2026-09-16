package com.ptcrys.blockoffensive.client.spec;

import net.minecraft.world.phys.Vec3;

public final class DeathCameraRigCheck {
    public static void main(String[] args) {
        DeathCameraRig rig = new DeathCameraRig(new Vec3(1, 2, 3), new Vec3(0, 0, 1), 0.8, 30, 10);
        var first = rig.sample(7.5).pose();
        for (int i = 0; i < 300; ++i) rig.sample(i * 0.13);
        check(first.equals(rig.sample(7.5).pose()), "sampling is independent of frame history");
        check(!first.position().equals(rig.sample(7.75).pose().position()), "death motion interpolates between ticks");
        check(Math.abs(rig.sample(14).pose().roll() + 12) < 0.001, "fall reaches authored roll");
        check(rig.sample(40).pose().equals(rig.sample(80).pose()), "target wait holds final camera without drift");
        System.out.println("Death camera rig checks passed");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
