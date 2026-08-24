package com.ptcrys.blockoffensive.minimap.acceptance;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Owns non-production acceptance handles until their cleanup has completed. */
public final class AcceptanceFixtureRegistry<H extends AcceptanceFixtureRegistry.Handle> {
    private final Map<UUID, H> handles = new LinkedHashMap<>();

    public synchronized H create(UUID owner, Supplier<? extends H> factory) {
        return createLocked(owner, handle -> false, factory);
    }

    /** Atomically rejects another live fixture that matches the requested scope. */
    public synchronized H createIfNoMatch(
            UUID owner,
            Predicate<? super H> conflictPredicate,
            Supplier<? extends H> factory
    ) {
        Objects.requireNonNull(conflictPredicate, "conflictPredicate");
        return createLocked(owner, conflictPredicate, factory);
    }

    private H createLocked(
            UUID owner,
            Predicate<? super H> conflictPredicate,
            Supplier<? extends H> factory
    ) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(factory, "factory");
        if (handles.containsKey(owner)) {
            throw new IllegalStateException("An acceptance fixture is already active");
        }
        for (H handle : handles.values()) {
            if (conflictPredicate.test(handle)) {
                throw new IllegalStateException(
                        "An acceptance fixture is already active for the requested scope");
            }
        }
        H handle = Objects.requireNonNull(factory.get(), "factory returned null");
        handles.put(owner, handle);
        try {
            handle.initialize();
            return handle;
        } catch (RuntimeException failure) {
            // Keep the handle registered so a later cleanup attempt can finish partial setup.
            throw failure;
        }
    }

    public synchronized Optional<H> find(UUID owner) {
        Objects.requireNonNull(owner, "owner");
        return Optional.ofNullable(handles.get(owner));
    }

    public synchronized boolean cleanup(UUID owner) {
        Objects.requireNonNull(owner, "owner");
        H handle = handles.get(owner);
        if (handle == null) {
            return false;
        }
        handle.close();
        handles.remove(owner);
        return true;
    }

    public synchronized int cleanupMatching(Predicate<? super H> predicate) {
        Objects.requireNonNull(predicate, "predicate");
        RuntimeException aggregate = null;
        int cleaned = 0;
        for (Map.Entry<UUID, H> entry : new ArrayList<>(handles.entrySet())) {
            if (!predicate.test(entry.getValue())) {
                continue;
            }
            try {
                entry.getValue().close();
                handles.remove(entry.getKey());
                cleaned++;
            } catch (RuntimeException failure) {
                if (aggregate == null) {
                    aggregate = new IllegalStateException(
                            "One or more acceptance fixtures could not be cleaned"
                    );
                }
                aggregate.addSuppressed(failure);
            }
        }
        if (aggregate != null) {
            throw aggregate;
        }
        return cleaned;
    }

    public synchronized int cleanupAll() {
        return cleanupMatching(handle -> true);
    }

    public synchronized List<H> snapshot() {
        return List.copyOf(handles.values());
    }

    public interface Handle extends AutoCloseable {
        void initialize();

        @Override
        void close();
    }
}
