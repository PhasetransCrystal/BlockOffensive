package com.ptcrys.blockoffensive.client.shop;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/** Tracks authoritative slot progress separately from optional UI result/feedback delivery. */
public final class ShopActionProgress<K> {
    public record Pending<K>(long id, K slot, int before, int delta, int sentTick) {}
    private final Map<Long, Pending<K>> pending = new LinkedHashMap<>();
    private boolean refundAll;
    private final java.util.Set<K> failedRefunds = new java.util.HashSet<>();

    public void start(long id, K slot, int before, int delta, int tick) {
        pending.put(id, new Pending<>(id, slot, before, delta, tick));
    }

    public boolean isBusy(K slot) { return pending.values().stream().anyMatch(p -> p.slot().equals(slot)); }

    public void reconcile(ToIntFunction<K> count) {
        pending.values().removeIf(p -> p.delta() > 0 ? count.applyAsInt(p.slot()) > p.before()
                : count.applyAsInt(p.slot()) < p.before());
    }

    public void rejected(long id) {
        Pending<K> failed = pending.remove(id);
        if (failed != null && failed.delta() < 0) failedRefunds.add(failed.slot());
    }

    public List<Pending<K>> retries(int tick) {
        return pending.values().stream().filter(p -> tick - p.sentTick() > 0
                && tick - p.sentTick() < 120 && (tick - p.sentTick()) % 40 == 0).toList();
    }

    public List<Long> expire(int tick) {
        List<Long> expired = new ArrayList<>();
        pending.values().removeIf(p -> {
            if (tick - p.sentTick() < 120) return false;
            expired.add(p.id());
            if (p.delta() < 0) failedRefunds.add(p.slot());
            return true;
        });
        return expired;
    }

    public void requestRefundAll() { refundAll = true; failedRefunds.clear(); }
    public void cancelRefundAll() { refundAll = false; failedRefunds.clear(); }

    public K nextRefund(Iterable<K> slots, Predicate<K> canReturn) {
        if (!refundAll || !pending.isEmpty()) return null;
        for (K slot : slots) if (!failedRefunds.contains(slot) && canReturn.test(slot)) return slot;
        cancelRefundAll();
        return null;
    }

    public void clear() { pending.clear(); cancelRefundAll(); }
}
