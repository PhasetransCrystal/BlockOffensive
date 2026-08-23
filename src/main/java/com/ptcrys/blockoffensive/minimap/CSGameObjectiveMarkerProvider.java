package com.ptcrys.blockoffensive.minimap;

import com.ptcrys.fpsmatch.core.minimap.marker.MarkerCandidate;
import com.ptcrys.fpsmatch.core.minimap.marker.MinimapMarkerProvider;
import com.ptcrys.fpsmatch.core.minimap.marker.MinimapViewerContext;
import com.ptcrys.fpsmatch.core.minimap.marker.ViewerRole;
import com.ptcrys.fpsmatch.core.minimap.model.NamespacedId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Emits C4 objective marker candidates. Per-recipient filtering for carried/dropped CT intel
 * is applied here against pure snapshot + viewer context (design 14.3).
 * Planted/defusing/defused terminal removal is encoded via phase; publicObjective only for team-public states.
 */
public final class CSGameObjectiveMarkerProvider implements MinimapMarkerProvider {
    public static final NamespacedId MARKER_ID = NamespacedId.parse("blockoffensive:objective/c4");
    public static final NamespacedId TYPE_ID = NamespacedId.parse("blockoffensive:type/c4");
    public static final NamespacedId STYLE_CARRIED = NamespacedId.parse("blockoffensive:style/c4_carried");
    public static final NamespacedId STYLE_DROPPED = NamespacedId.parse("blockoffensive:style/c4_dropped");
    public static final NamespacedId STYLE_PLANTED = NamespacedId.parse("blockoffensive:style/c4_planted");
    public static final NamespacedId STYLE_DEFUSING = NamespacedId.parse("blockoffensive:style/c4_defusing");

    public static final String T_TEAM = "t";
    public static final String CT_TEAM = "ct";

    private final Supplier<C4ObjectiveSnapshot> snapshotSupplier;
    private final LongSupplier nowTick;
    private final Supplier<Optional<Boolean>> carrierActiveIntelForCt;
    private final String tTeamId;
    private final String ctTeamId;

    public CSGameObjectiveMarkerProvider(
            Supplier<C4ObjectiveSnapshot> snapshotSupplier,
            LongSupplier nowTick,
            Supplier<Optional<Boolean>> carrierActiveIntelForCt
    ) {
        this(snapshotSupplier, nowTick, carrierActiveIntelForCt, T_TEAM, CT_TEAM);
    }

    public CSGameObjectiveMarkerProvider(
            Supplier<C4ObjectiveSnapshot> snapshotSupplier,
            LongSupplier nowTick,
            Supplier<Optional<Boolean>> carrierActiveIntelForCt,
            String tTeamId,
            String ctTeamId
    ) {
        this.snapshotSupplier = Objects.requireNonNull(snapshotSupplier, "snapshotSupplier");
        this.nowTick = Objects.requireNonNull(nowTick, "nowTick");
        this.carrierActiveIntelForCt = Objects.requireNonNull(carrierActiveIntelForCt, "carrierActiveIntelForCt");
        this.tTeamId = Objects.requireNonNull(tTeamId, "tTeamId");
        this.ctTeamId = Objects.requireNonNull(ctTeamId, "ctTeamId");
        if (tTeamId.isBlank() || ctTeamId.isBlank()) {
            throw new IllegalArgumentException("team ids cannot be blank");
        }
    }

    @Override
    public List<MarkerCandidate> collect(MinimapViewerContext context) {
        Objects.requireNonNull(context, "context");
        C4ObjectiveSnapshot snap = snapshotSupplier.get();
        if (snap == null || !snap.isActiveObjective()) {
            return List.of();
        }
        long tick = nowTick.getAsLong();
        if (!isVisibleTo(context, snap, tick)) {
            return List.of();
        }
        MarkerCandidate candidate = toCandidate(snap, context);
        return List.of(candidate);
    }

    private boolean isVisibleTo(MinimapViewerContext context, C4ObjectiveSnapshot snap, long tick) {
        if (context.role() == ViewerRole.SPECTATOR_TEAM) {
            return context.observerOmniscient() || snap.phase() == C4ObjectivePhase.PLANTED
                    || snap.phase() == C4ObjectivePhase.DEFUSING;
        }
        boolean isT = context.teamId().equals(tTeamId);
        boolean isCt = context.teamId().equals(ctTeamId);
        return switch (snap.phase()) {
            case CARRIED -> {
                if (isT) {
                    yield true;
                }
                if (isCt) {
                    // only when carrier is in active enemy intel for CT
                    yield carrierActiveIntelForCt.get().orElse(false);
                }
                yield false;
            }
            case DROPPED -> {
                if (isT) {
                    yield true;
                }
                if (isCt) {
                    yield isDroppedKnownToCt(snap, tick);
                }
                yield false;
            }
            case PLANTED, DEFUSING -> true; // always public once planted
            default -> false;
        };
    }

    private static boolean isDroppedKnownToCt(C4ObjectiveSnapshot snap, long tick) {
        if (!snap.droppedKnownToCt()) {
            return false;
        }
        Optional<Long> until = snap.droppedCtLastKnownUntil();
        return until.isEmpty() || tick <= until.get();
    }

    private MarkerCandidate toCandidate(C4ObjectiveSnapshot snap, MinimapViewerContext context) {
        NamespacedId style = switch (snap.phase()) {
            case CARRIED -> STYLE_CARRIED;
            case DROPPED -> STYLE_DROPPED;
            case PLANTED -> STYLE_PLANTED;
            case DEFUSING -> STYLE_DEFUSING;
            default -> STYLE_PLANTED;
        };
        // teamId: for filtering, use T for bomb ownership on carry/drop; planted is public
        String teamId = switch (snap.phase()) {
            case CARRIED, DROPPED -> tTeamId;
            case PLANTED, DEFUSING -> ctTeamId; // not used for publicObjective filtering
            default -> tTeamId;
        };
        boolean publicObjective = snap.phase() == C4ObjectivePhase.PLANTED
                || snap.phase() == C4ObjectivePhase.DEFUSING
                || (snap.phase() == C4ObjectivePhase.DROPPED && context.teamId().equals(tTeamId))
                || (snap.phase() == C4ObjectivePhase.CARRIED && context.teamId().equals(tTeamId));
        // Defusing progress only for CT / observer; T never sees progress in candidate style payload.
        // Progress is encoded only by style switch for CT; T gets PLANTED style even if DEFUSING.
        if (snap.phase() == C4ObjectivePhase.DEFUSING) {
            boolean showProgress = context.teamId().equals(ctTeamId)
                    || context.role() == ViewerRole.SPECTATOR_TEAM;
            if (!showProgress) {
                style = STYLE_PLANTED;
            }
        }
        return new MarkerCandidate(
                MARKER_ID,
                TYPE_ID,
                style,
                snap.x(), snap.y(), snap.z(), snap.yaw(),
                snap.updatedTick(),
                Optional.empty(),
                snap.floorSlug(),
                teamId,
                false,
                publicObjective
        );
    }

    public static Optional<Boolean> activeIntelFlag(CSMinimapIntelLedger ledger, String ctTeamId, UUID carrierId, long nowTick) {
        Objects.requireNonNull(ledger, "ledger");
        Objects.requireNonNull(ctTeamId, "ctTeamId");
        Objects.requireNonNull(carrierId, "carrierId");
        return ledger.get(ctTeamId, carrierId).map(state -> {
            // ensure ledger advanced externally; treat ACTIVE mode only
            return state.isActive() && nowTick <= state.activeUntilTick();
        });
    }
}