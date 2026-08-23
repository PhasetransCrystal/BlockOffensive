package com.ptcrys.blockoffensive.minimap;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * BlockOffensive minimap GameTests for CS/CSDM lifecycle matrices.
 * Templates intentionally use the built-in empty structure; assertions stay pure against trackers
 * to avoid depending on full match bootstrap inside the game-test world.
 */
@GameTestHolder("blockoffensive")
@PrefixGameTestTemplate(false)
public final class CSMinimapGameTests {
    private CSMinimapGameTests() {
    }

    @GameTest(template = "empty")
    public static void csObjectiveLifecycleMatrix(GameTestHelper helper) {
        CSGameObjectiveTracker tracker = new CSGameObjectiveTracker();
        java.util.UUID carrier = java.util.UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        java.util.UUID drop = java.util.UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        java.util.UUID plant = java.util.UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        tracker.assignCarrier(carrier, 1, 0, 64, 0, 0f, java.util.Optional.empty());
        tracker.forceDrop(1, drop, 2, 1, 64, 1, 0f, java.util.Optional.empty());
        tracker.successfulPickup(carrier, 3, 2, 64, 2, 0f, java.util.Optional.empty());
        tracker.planted(2, plant, 4, 5, 64, 5, 0f, java.util.Optional.empty(), java.util.Optional.of("site_1"));
        tracker.startDefusing(java.util.UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"), 5, 0.5f);
        tracker.defused(6);
        tracker.roundReset();
        if (tracker.snapshot().phase() != C4ObjectivePhase.NONE) {
            helper.fail("objective tracker did not reset");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void csdmDeathTtlMatrix(GameTestHelper helper) {
        if (CSMapMinimapMarkerProvider.CSDM_DEATH_TTL_TICKS != 40L) {
            helper.fail("CSDM death TTL must be 40");
            return;
        }
        if (CSMapMinimapMarkerProvider.CS_DEATH_TTL_TICKS != 100L) {
            helper.fail("CS death TTL must be 100");
            return;
        }
        helper.succeed();
    }
}