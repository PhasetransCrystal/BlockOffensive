package net.ptcrys.blockoffensive.compat;

import net.minecraftforge.fml.ModList;

public class BOImpl {

    public static boolean isGD656KillIconLoaded() {
        return ModList.get().isLoaded("gd656killicon");
    }

    public static boolean isCounterStrikeGrenadesLoaded() {
        return ModList.get().isLoaded("csgrenades");
    }

    public static boolean isHitIndicationLoaded() {
        return ModList.get().isLoaded("hitindication");
    }

    public static boolean isPhysicsModLoaded() {
        return ModList.get().isLoaded("physicsmod");
    }
}
