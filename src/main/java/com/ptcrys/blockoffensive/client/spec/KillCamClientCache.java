package com.ptcrys.blockoffensive.client.spec;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public final class KillCamClientCache {
    private static Vec3 killerPos;
    private static Vec3 victimPos;

    private static UUID      killerUUID;
    private static String    killerName;
    private static ItemStack weapon = ItemStack.EMPTY;

    public static void cache(Vec3 kPos, Vec3 vPos, UUID kId, String kName, ItemStack weap) {
        killerPos  = kPos;
        victimPos  = vPos;
        killerUUID = kId;
        killerName = kName;
        weapon     = weap == null ? ItemStack.EMPTY : weap;
    }

    public static Vec3  consumeKiller() { Vec3 t = killerPos; killerPos = null; return t; }
    public static Vec3  consumeVictim() { Vec3 t = victimPos; victimPos = null; return t; }

    public static UUID   getKillerUUID()  { return killerUUID; }
    public static String getKillerName()  { return killerName; }

    public static ItemStack getWeapon()   { return weapon == null ? ItemStack.EMPTY : weapon; }

    private KillCamClientCache() {}
}