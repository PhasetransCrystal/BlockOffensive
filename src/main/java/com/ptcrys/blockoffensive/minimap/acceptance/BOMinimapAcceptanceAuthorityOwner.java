package com.ptcrys.blockoffensive.minimap.acceptance;

import com.ptcrys.blockoffensive.minimap.acceptance.BOMinimapAcceptanceAuthorityOwnerCodec.Kind;
import com.ptcrys.blockoffensive.minimap.acceptance.BOMinimapAcceptanceAuthorityOwnerCodec.Layout;
import com.ptcrys.blockoffensive.minimap.acceptance.BOMinimapAcceptanceAuthorityOwnerCodec.OwnerRecord;
import com.ptcrys.fpsmatch.core.minimap.storage.AuthorityJournalProvider;
import com.ptcrys.fpsmatch.core.minimap.storage.MinimapAuthorityJournal;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable external authority for one BO minimap acceptance run.
 *
 * <p>Closing this object closes only the provider handle. Fixture deletion is a later,
 * separately anchored cleanup action and must never be inferred from a path name.</p>
 */
final class BOMinimapAcceptanceAuthorityOwner implements AutoCloseable {
    enum Phase {
        RESERVED,
        SEALED,
        CLEANUP_READY
    }

    enum Availability {
        AVAILABLE,
        UNAVAILABLE
    }

    private final Layout layout;
    private final AuthorityJournalProvider provider;
    private final MinimapAuthorityJournal model;
    private AuthorityJournalProvider.JournalHandle handle;
    private OwnerRecord record;
    private Availability availability;
    private String unavailableDetail;
    private boolean closed;

    private BOMinimapAcceptanceAuthorityOwner(
            Layout layout,
            AuthorityJournalProvider provider,
            MinimapAuthorityJournal model,
            AuthorityJournalProvider.JournalHandle handle,
            OwnerRecord record,
            Availability availability,
            String unavailableDetail
    ) {
        this.layout = layout;
        this.provider = provider;
        this.model = model;
        this.handle = handle;
        this.record = record;
        this.availability = availability;
        this.unavailableDetail = unavailableDetail == null ? "" : unavailableDetail;
    }

    static BOMinimapAcceptanceAuthorityOwner open(
            Path canonicalRoot,
            Path fixtureRoot,
            UUID actorId,
            UUID runId
    ) throws IOException {
        return open(
                canonicalRoot, fixtureRoot, actorId, runId,
                BOMinimapAcceptanceAuthorityOwnerCodec.defaultProvider());
    }

    static BOMinimapAcceptanceAuthorityOwner open(
            Path canonicalRoot,
            Path fixtureRoot,
            UUID actorId,
            UUID runId,
            AuthorityJournalProvider provider
    ) throws IOException {
        Layout layout = Layout.require(canonicalRoot, fixtureRoot, actorId, runId);
        layout.prepareOpen();
        AuthorityJournalProvider selected = Objects.requireNonNull(provider, "provider");
        AuthorityJournalProvider.Availability providerAvailability =
                selected.availability(layout.journalRoot());
        if (!providerAvailability.supported()) {
            throw new IOException("Acceptance authority provider is unsupported: "
                    + providerAvailability.detail());
        }

        MinimapAuthorityJournal model = MinimapAuthorityJournal.defaults();
        AuthorityJournalProvider.JournalHandle handle = null;
        Throwable failure = null;
        try {
            handle = selected.open(layout.journalRoot(), model.config());
            if (handle.inspect().detection() != AuthorityJournalProvider.Detection.ABSENT) {
                throw new IOException("Acceptance owner ledger already exists");
            }
            OwnerRecord reserved = OwnerRecord.reserved(layout);
            MinimapAuthorityJournal.Snapshot activated = model.activate(
                    handle, reserved.journalInstance(), "reserve-" + runId,
                    reserved.encode());
            Derived derived = derive(activated, layout);
            if (!derived.available() || derived.record().kind() != Kind.RESERVED) {
                throw new IOException("Acceptance owner reservation did not become durable");
            }
            BOMinimapAcceptanceAuthorityOwnerCodec.createFixtureRoot(layout, reserved);
            return new BOMinimapAcceptanceAuthorityOwner(
                    layout, selected, model, handle, reserved,
                    Availability.AVAILABLE, "");
        } catch (IOException | RuntimeException | Error caught) {
            failure = caught;
            throw caught;
        } finally {
            if (failure != null && handle != null) {
                try {
                    handle.close();
                } catch (IOException closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
            }
        }
    }

    static BOMinimapAcceptanceAuthorityOwner reopen(
            Path canonicalRoot,
            Path fixtureRoot,
            UUID actorId,
            UUID runId
    ) throws IOException {
        return reopen(
                canonicalRoot, fixtureRoot, actorId, runId,
                BOMinimapAcceptanceAuthorityOwnerCodec.defaultProvider());
    }

    static BOMinimapAcceptanceAuthorityOwner reopen(
            Path canonicalRoot,
            Path fixtureRoot,
            UUID actorId,
            UUID runId,
            AuthorityJournalProvider provider
    ) throws IOException {
        Layout layout;
        try {
            layout = Layout.require(canonicalRoot, fixtureRoot, actorId, runId);
        } catch (IOException | RuntimeException invalid) {
            return unavailable(canonicalRoot, fixtureRoot, actorId, runId, provider,
                    "Acceptance owner paths are invalid");
        }
        AuthorityJournalProvider selected = Objects.requireNonNull(provider, "provider");
        if (!selected.availability(layout.journalRoot()).supported()) {
            return unavailable(layout, selected, "Authority provider is unsupported");
        }

        MinimapAuthorityJournal model = MinimapAuthorityJournal.defaults();
        AuthorityJournalProvider.JournalHandle handle = null;
        try {
            handle = selected.open(layout.journalRoot(), model.config());
            Derived derived = derive(model.load(handle), layout);
            if (!derived.available()) {
                closeQuietly(handle);
                return unavailable(layout, selected, derived.detail());
            }
            return new BOMinimapAcceptanceAuthorityOwner(
                    layout, selected, model, handle, derived.record(),
                    Availability.AVAILABLE, "");
        } catch (IOException | RuntimeException failure) {
            closeQuietly(handle);
            return unavailable(
                    layout, selected,
                    "Unable to verify acceptance owner: " + failure.getMessage());
        }
    }

    synchronized Phase phase() {
        if (record == null) {
            return Phase.RESERVED;
        }
        return switch (record.kind()) {
            case RESERVED -> Phase.RESERVED;
            case SEALED, RECEIPT_SELECTED, COMMIT_ATTEMPTED -> Phase.SEALED;
            case CLEANUP_READY -> Phase.CLEANUP_READY;
        };
    }

    synchronized Availability availability() {
        return availability;
    }

    synchronized boolean commitAttempted() {
        return availability == Availability.AVAILABLE && record != null
                && record.commitAttempted();
    }

    synchronized void sealReceipt(byte[] canonicalReceipt) throws IOException {
        ensureAvailable();
        if (record.kind() != Kind.RESERVED) {
            if (phase() == Phase.SEALED
                    && Arrays.equals(record.receipt(), canonicalReceipt)) {
                return;
            }
            throw new IllegalStateException("Acceptance owner is not RESERVED");
        }
        if (!BOMinimapAcceptanceAuthorityOwnerCodec.verifyLiveReservedRoot(
                layout, record)) {
            markUnavailable("Fixture root no longer matches the reservation");
            throw new IOException(unavailableDetail);
        }
        appendTransition("seal", record.seal(layout, canonicalReceipt));
    }

    /** Selects the next immutable non-wire receipt generation before acknowledging it. */
    synchronized boolean recordReceipt(byte[] canonicalReceipt) throws IOException {
        if (!usable() || phase() != Phase.SEALED || !verifyCurrentRecord()) {
            return false;
        }
        if (Arrays.equals(record.receipt(), canonicalReceipt)) {
            return true;
        }
        OwnerRecord selected;
        try {
            selected = record.selectReceipt(canonicalReceipt);
        } catch (IllegalArgumentException invalid) {
            markUnavailable(invalid.getMessage());
            return false;
        }
        try {
            appendTransition("receipt-selected", selected);
            return true;
        } catch (IOException | RuntimeException failure) {
            if (adoptExact(selected)) {
                return true;
            }
            markUnavailable("Receipt generation was not durably selected");
            return false;
        }
    }

    synchronized boolean recordCommitAttempted(byte[] canonicalReceipt) throws IOException {
        if (!usable()) {
            return false;
        }
        if (record.commitAttempted()) {
            return Arrays.equals(record.receipt(), canonicalReceipt)
                    && verifyCurrentRecord();
        }
        if ((record.kind() != Kind.SEALED && record.kind() != Kind.RECEIPT_SELECTED)
                || !Arrays.equals(record.receipt(), canonicalReceipt)
                || !verifyCurrentRecord()) {
            markUnavailable("Commit receipt or owner witness does not match");
            return false;
        }
        OwnerRecord attempted = record.withCommitAttempted();
        try {
            appendTransition("commit-attempted", attempted);
            return true;
        } catch (IOException | RuntimeException failure) {
            if (adoptExact(attempted)) {
                return true;
            }
            markUnavailable("Commit-attempt receipt was not durably selected");
            return false;
        }
    }

    synchronized boolean markCleanupReady() throws IOException {
        if (!usable()) {
            return false;
        }
        if (record.kind() == Kind.CLEANUP_READY) {
            return verifyCurrentRecord();
        }
        if ((record.kind() != Kind.SEALED && record.kind() != Kind.RECEIPT_SELECTED
                && record.kind() != Kind.COMMIT_ATTEMPTED)
                || !verifyCurrentRecord()) {
            markUnavailable("Cleanup-ready owner witness does not match");
            return false;
        }
        OwnerRecord ready = record.cleanupReady();
        try {
            appendTransition("cleanup-ready", ready);
            return true;
        } catch (IOException | RuntimeException failure) {
            if (adoptExact(ready)) {
                return true;
            }
            markUnavailable("Cleanup-ready receipt was not durably selected");
            return false;
        }
    }

    Path ledgerRootForTest() {
        return layout.journalRoot();
    }

    synchronized String unavailableDetailForTest() {
        return unavailableDetail;
    }

    synchronized byte[] selectedReceiptForTest() {
        return record == null ? new byte[0] : record.receipt();
    }

    @Override
    public synchronized void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        AuthorityJournalProvider.JournalHandle current = handle;
        handle = null;
        if (current != null) {
            current.close();
        }
    }

    private void appendTransition(String operationName, OwnerRecord desired)
            throws IOException {
        ensureAvailable();
        if (!verifyCurrentRecord()) {
            markUnavailable("Owner witness changed before " + operationName);
            throw new IOException(unavailableDetail);
        }
        AuthorityJournalProvider.Inspection inspection = handle.inspect();
        MinimapAuthorityJournal.Snapshot snapshot = model.parse(inspection);
        Derived before = derive(snapshot, layout, record.kind() == Kind.RESERVED);
        if (!before.available() || !sameRecord(before.record(), record)) {
            markUnavailable("Owner journal head changed before " + operationName);
            throw new IOException(unavailableDetail);
        }
        String operationId = operationName + "-" + UUID.randomUUID();
        MinimapAuthorityJournal.ReservationPlan plan = model.preflight(
                snapshot, inspection, operationId);
        if (plan.decision() != MinimapAuthorityJournal.PreflightDecision.RESERVE_OPERATION) {
            throw new IOException("Acceptance owner unexpectedly requires a checkpoint");
        }
        AuthorityJournalProvider.CapacityReceipt capacity =
                handle.reserve(plan.providerRequest());
        byte[] identity = desired.encode();
        MinimapAuthorityJournal.Entry intent = model.next(
                snapshot, MinimapAuthorityJournal.Operation.OWNER_LEDGER,
                MinimapAuthorityJournal.Phase.INTENT, operationId, identity,
                new byte[0], new byte[0], MinimapAuthorityJournal.Hashes.none(), 0L);
        append(capacity, 0, intent, desired);

        snapshot = model.load(handle);
        MinimapAuthorityJournal.Entry complete = model.next(
                snapshot, MinimapAuthorityJournal.Operation.OWNER_LEDGER,
                MinimapAuthorityJournal.Phase.COMPLETE, operationId, identity,
                new byte[0], new byte[0], MinimapAuthorityJournal.Hashes.none(), 0L);
        append(capacity, 1, complete, desired);

        MinimapAuthorityJournal.Snapshot completed = model.load(handle);
        Derived after = derive(completed, layout);
        if (!after.available() || !sameRecord(after.record(), desired)) {
            markUnavailable("Owner transition did not produce the exact durable head");
            throw new IOException(unavailableDetail);
        }
        record = desired;
        AuthorityJournalProvider.MutationResult released = handle.release(capacity);
        if (released.status() == AuthorityJournalProvider.MutationStatus.UNAVAILABLE) {
            // The immutable COMPLETE entry is already authoritative. The provider retains
            // the reciprocal capacity receipt for exact retry; no path cleanup is allowed.
            Derived retained = derive(model.load(handle), layout);
            if (!retained.available() || !sameRecord(retained.record(), desired)) {
                markUnavailable("Owner reservation release did not converge");
                throw new IOException(unavailableDetail);
            }
        }
    }

    private void append(
            AuthorityJournalProvider.CapacityReceipt capacity,
            int ordinal,
            MinimapAuthorityJournal.Entry entry,
            OwnerRecord desired
    ) throws IOException {
        AuthorityJournalProvider.MutationResult result = handle.append(
                new AuthorityJournalProvider.AppendRequest(
                        capacity, ordinal, model.encode(entry),
                        desired.encode()));
        if (result.status() == AuthorityJournalProvider.MutationStatus.UNAVAILABLE) {
            throw new IOException("Acceptance owner append is unavailable: " + result.detail());
        }
    }

    private boolean adoptExact(OwnerRecord expected) {
        if (handle == null) {
            return false;
        }
        try {
            Derived derived = derive(model.load(handle), layout);
            if (!derived.available() || !sameRecord(derived.record(), expected)) {
                return false;
            }
            record = expected;
            return true;
        } catch (IOException | RuntimeException ignored) {
            return false;
        }
    }

    private boolean verifyCurrentRecord() {
        try {
            return record.kind() == Kind.RESERVED
                    ? BOMinimapAcceptanceAuthorityOwnerCodec.verifyLiveReservedRoot(
                    layout, record)
                    : BOMinimapAcceptanceAuthorityOwnerCodec.verifyExternal(layout, record);
        } catch (IOException | RuntimeException failure) {
            return false;
        }
    }

    private boolean usable() {
        return !closed && availability == Availability.AVAILABLE
                && handle != null && record != null;
    }

    private void ensureAvailable() {
        if (!usable()) {
            throw new IllegalStateException("Acceptance owner is unavailable: "
                    + unavailableDetail);
        }
    }

    private void markUnavailable(String detail) {
        availability = Availability.UNAVAILABLE;
        unavailableDetail = detail == null ? "" : detail;
    }

    private static Derived derive(
            MinimapAuthorityJournal.Snapshot snapshot,
            Layout layout
    ) throws IOException {
        return derive(snapshot, layout, false);
    }

    private static Derived derive(
            MinimapAuthorityJournal.Snapshot snapshot,
            Layout layout,
            boolean allowLiveReserved
    ) throws IOException {
        List<MinimapAuthorityJournal.Entry> chain = snapshot.validatedChain();
        if (!snapshot.active() || chain.isEmpty() || snapshot.pending().isPresent()) {
            return Derived.unavailable("Owner journal is absent or has an unresolved operation");
        }
        MinimapAuthorityJournal.Entry activation = chain.get(0);
        if (activation.operation() != MinimapAuthorityJournal.Operation.ACTIVATION
                || activation.phase()
                != MinimapAuthorityJournal.Phase.ACTIVATION_COMPLETE) {
            return Derived.unavailable("Owner journal activation is invalid");
        }
        OwnerRecord current = OwnerRecord.decode(activation.attemptIdentity());
        if (current.kind() != Kind.RESERVED
                || !current.actorId().equals(layout.actorId())
                || !current.runId().equals(layout.runId())
                || !current.fixtureRelative().equals(layout.fixtureRelative())) {
            return Derived.unavailable("Owner reservation correlation changed");
        }
        byte[] pendingIdentity = null;
        String pendingOperation = null;
        for (int index = 1; index < chain.size(); index++) {
            MinimapAuthorityJournal.Entry entry = chain.get(index);
            if (entry.operation() == MinimapAuthorityJournal.Operation.CHECKPOINT) {
                continue;
            }
            if (entry.operation() != MinimapAuthorityJournal.Operation.OWNER_LEDGER) {
                return Derived.unavailable("Owner journal contains a foreign operation");
            }
            if (entry.phase() == MinimapAuthorityJournal.Phase.INTENT) {
                if (pendingIdentity != null) {
                    return Derived.unavailable("Owner journal contains overlapping intents");
                }
                pendingIdentity = entry.attemptIdentity();
                pendingOperation = entry.operationId();
            } else if (entry.phase() == MinimapAuthorityJournal.Phase.COMPLETE) {
                if (pendingIdentity == null || !entry.operationId().equals(pendingOperation)
                        || !Arrays.equals(pendingIdentity, entry.attemptIdentity())) {
                    return Derived.unavailable("Owner journal completion is uncorrelated");
                }
                OwnerRecord candidate = OwnerRecord.decode(entry.attemptIdentity());
                if (!candidate.validSuccessorOf(current)) {
                    return Derived.unavailable("Owner journal transition is invalid");
                }
                current = candidate;
                pendingIdentity = null;
                pendingOperation = null;
            } else {
                return Derived.unavailable("Owner journal phase is invalid");
            }
        }
        boolean external = current.kind() == Kind.RESERVED && allowLiveReserved
                ? BOMinimapAcceptanceAuthorityOwnerCodec.verifyLiveReservedRoot(layout, current)
                : BOMinimapAcceptanceAuthorityOwnerCodec.verifyExternal(layout, current);
        if (pendingIdentity != null || !external) {
            return Derived.unavailable("Owner root or selected receipt was replaced");
        }
        return Derived.available(current);
    }

    private static boolean sameRecord(OwnerRecord left, OwnerRecord right) {
        try {
            return Arrays.equals(left.encode(), right.encode());
        } catch (IOException impossible) {
            return false;
        }
    }

    private static BOMinimapAcceptanceAuthorityOwner unavailable(
            Layout layout,
            AuthorityJournalProvider provider,
            String detail
    ) {
        return new BOMinimapAcceptanceAuthorityOwner(
                layout, provider, MinimapAuthorityJournal.defaults(), null, null,
                Availability.UNAVAILABLE, detail);
    }

    private static BOMinimapAcceptanceAuthorityOwner unavailable(
            Path canonicalRoot,
            Path fixtureRoot,
            UUID actorId,
            UUID runId,
            AuthorityJournalProvider provider,
            String detail
    ) throws IOException {
        Layout layout;
        try {
            layout = Layout.require(canonicalRoot, fixtureRoot, actorId, runId);
        } catch (IOException | RuntimeException invalid) {
            Path canonical = canonicalRoot.toAbsolutePath().normalize();
            Path fixture = fixtureRoot.toAbsolutePath().normalize();
            layout = new Layout(
                    canonical, fixture,
                    canonical.resolve(".bo-acceptance-authority")
                            .resolve(actorId.toString()).resolve(runId.toString()),
                    canonical.relativize(fixture).toString().replace('\\', '/'),
                    actorId, runId);
        }
        return unavailable(layout, provider, detail);
    }

    private static void closeQuietly(AuthorityJournalProvider.JournalHandle handle) {
        if (handle == null) {
            return;
        }
        try {
            handle.close();
        } catch (IOException ignored) {
        }
    }

    private record Derived(boolean available, OwnerRecord record, String detail) {
        static Derived available(OwnerRecord record) {
            return new Derived(true, record, "");
        }

        static Derived unavailable(String detail) {
            return new Derived(false, null, detail);
        }
    }
}
