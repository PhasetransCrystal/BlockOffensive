package net.ptcrys.blockoffensive.client.shop;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ShopActionProgressCheck {
    public static void main(String[] args) {
        ShopActionProgress<String> state = new ShopActionProgress<>();
        Map<String, Integer> counts = new HashMap<>(Map.of("pistol", 0, "grenade", 2));
        List<String> slots = List.of("pistol", "grenade");
        state.start(1, "pistol", 0, 1, 0);
        state.requestRefundAll(); // DEL while buy reply/snapshot is in flight
        check(state.nextRefund(slots, s -> counts.get(s) > 0) == null, "wait for the purchase snapshot");
        check(state.retries(40).get(0).id() == 1, "retry same ID, never duplicate a purchase");
        counts.put("pistol", 1); // slot sync received, action-result callback absent
        state.reconcile(counts::get);
        check(!state.isBusy("pistol"), "authoritative slot sync releases buy without reopening");
        check(state.nextRefund(slots, s -> counts.get(s) > 0).equals("pistol"), "DEL includes newly bought item");
        state.start(2, "pistol", 1, -1, 41);
        check(state.nextRefund(slots, s -> counts.get(s) > 0) == null, "serialize refunds");
        counts.put("pistol", 0); state.reconcile(counts::get);
        check(state.nextRefund(slots, s -> counts.get(s) > 0).equals("grenade"), "next slot after refund");
        state.start(3, "grenade", 2, -1, 42);
        counts.put("grenade", 1); state.reconcile(counts::get);
        check(state.nextRefund(slots, s -> counts.get(s) > 0).equals("grenade"), "repeat refund from latest quantity");
        state.start(4, "grenade", 1, -1, 43); state.rejected(4);
        check(state.nextRefund(slots, s -> counts.get(s) > 0) == null, "failed slot cannot loop forever");
        state.requestRefundAll();
        check(state.nextRefund(slots, s -> counts.get(s) > 0).equals("grenade"), "new DEL works without reopening");
        state.start(5, "grenade", 1, -1, 50);
        check(state.expire(169).isEmpty(), "allow response grace period");
        check(state.expire(170).equals(List.of(5L)), "missing reply cannot block queue permanently");
        state.clear();
        check(state.nextRefund(slots, s -> true) == null, "close cancels refund intent");
        System.out.println("Shop action progress checks passed (12 scenarios)");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
