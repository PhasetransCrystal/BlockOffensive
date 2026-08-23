package com.ptcrys.blockoffensive.compat;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class ProxiedRagdollRegistry {

    private final Set<Integer> entityIds = ConcurrentHashMap.newKeySet();

    boolean begin(int entityId) {
        return entityIds.add(entityId);
    }

    void clear(int entityId) {
        entityIds.remove(entityId);
    }

    void clearAll() {
        entityIds.clear();
    }
}
