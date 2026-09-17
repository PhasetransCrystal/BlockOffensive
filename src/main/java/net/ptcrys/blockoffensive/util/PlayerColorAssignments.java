package net.ptcrys.blockoffensive.util;

import java.util.*;

/** Stable, unique colors within one roster. No client-side allocation. */
public final class PlayerColorAssignments {

    private final Map<UUID, Integer> colors = new LinkedHashMap<>();
    private static final int[] PALETTE = { 0xFF5E96FF, 0xFFEEE44B, 0xFFB36BE2, 0xFF079C82, 0xFFFF9B38 };

    public boolean reconcile(Collection<UUID> roster) {
        boolean changed = colors.keySet().retainAll(new HashSet<>(roster));
        for (UUID id : roster.stream().sorted().toList()) {
            if (!colors.containsKey(id)) {
                assign(id);
                changed = true;
            }
        }
        return changed;
    }

    public int assign(UUID id) {
        return colors.computeIfAbsent(id, ignored -> {
            Set<Integer> used = new HashSet<>(colors.values());
            for (int color : PALETTE) if (!used.contains(color)) return color;
            // Extra slots remain unique for maps configured with more than five players per team.
            for (int slot = 0;; slot++) {
                int color = 0xFF000000 | ((0x80B0E0 + slot * 0x9E3779) & 0xFFFFFF);
                if (!used.contains(color)) return color;
            }
        });
    }

    public int get(UUID id) {
        return colors.getOrDefault(id, 0xFFFFFFFF);
    }

    public boolean remove(UUID id) {
        return colors.remove(id) != null;
    }

    public Map<UUID, Integer> snapshot() {
        return new LinkedHashMap<>(colors);
    }

    public void replace(Map<UUID, Integer> snapshot) {
        colors.clear();
        colors.putAll(snapshot);
    }

    public void clear() {
        colors.clear();
    }
}
