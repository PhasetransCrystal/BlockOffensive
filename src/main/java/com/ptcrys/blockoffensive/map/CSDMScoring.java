package com.ptcrys.blockoffensive.map;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** Current CS2 deathmatch weapon scores, matched against normalized gun ids. */
final class CSDMScoring {
    private CSDMScoring() {
    }

    static int scoreForWeaponPath(String weaponPath) {
        String id = weaponPath.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        if (containsAny(id, "zeus", "p2000", "glock", "usp")) return 18;
        if (containsAny(id, "p250", "dualberetta", "elite", "tec9", "cz75")) return 16;
        if (containsAny(id, "r8revolver", "revolver", "deagle", "deserteagle")) return 14;
        if (containsAny(id, "ump", "nova", "bizon", "sawedoff", "mac10", "mp5sd", "mp7", "mp9", "mag7")) return 13;
        if (containsAny(id, "xm1014", "p90", "ssg08", "scout", "galil", "famas", "negev")) return 11;
        if (containsAny(id, "m249", "awp", "g3sg1", "scar20")) return 8;
        return 10;
    }

    private static boolean containsAny(String value, String... candidates) {
        return Stream.of(candidates).anyMatch(value::contains);
    }
}
