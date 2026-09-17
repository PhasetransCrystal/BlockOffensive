package net.ptcrys.blockoffensive.client.spec;

/** Standalone regression checks: javac/Java only, no running client required. */
public final class DeathCameraTimelineCheck {
    public static void main(String[] args) {
        DeathCameraTimeline clock = new DeathCameraTimeline();
        check(!clock.active() && clock.maskProgress(0.5F) == 0, "idle must not obscure world");
        clock.start();
        check(clock.maskProgress(0) == 0 && clock.fadeProgress(0) == 0, "death starts visible");
        for (int tick = 0; tick < 5; ++tick) {
            check(clock.showInformation(), "card remains in the presentation");
            check(clock.hitProgress(0.5F) > 0, "hit filter starts immediately");
            clock.tick();
        }
        for (int tick = 5; tick < 40; ++tick) {
            check(clock.showInformation(), "card visible through the two-second presentation");
            check(clock.maskProgress(0.5F) <= 1, "fade is bounded");
            check(clock.active(), "death filter remains active during fade");
            clock.tick();
        }
        check(clock.waitingForTarget() && clock.shouldRequestTarget(), "attach at 1.3s");
        for (int tick = 0; tick < 40; ++tick) {
            check(!clock.targetWaitExpired(), "allow bounded target delivery");
            check(clock.maskProgress(0) == 1, "no free-camera flash while awaiting server");
            check(clock.shouldRequestTarget() == (tick % 5 == 0), "rate-limited retries");
            clock.tick();
        }
        check(clock.targetWaitExpired(), "missing target must fall back, never stay black forever");
        clock.reset();
        check(!clock.active() && !clock.showInformation() && !clock.shouldRequestTarget(), "respawn clears presentation");
        check(clock.maskProgress(1) == 0, "respawn uncovers world");
        clock.start();
        check(clock.maskProgress(0) == 0 && !clock.waitingForTarget(), "next death starts cleanly");
        System.out.println("Death camera timing checks passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
