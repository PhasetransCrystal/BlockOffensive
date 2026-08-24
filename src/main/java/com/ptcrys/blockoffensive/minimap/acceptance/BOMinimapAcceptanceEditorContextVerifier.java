package com.ptcrys.blockoffensive.minimap.acceptance;

import com.ptcrys.fpsmatch.core.minimap.model.MapKey;
import com.ptcrys.fpsmatch.core.minimap.model.NamespacedId;
import com.ptcrys.fpsmatch.core.minimap.model.Sha256;
import com.ptcrys.fpsmatch.core.minimap.wire.WireIdentity;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Verifies fixture-owned identity before consulting the FPSMatch runtime authority. */
public final class BOMinimapAcceptanceEditorContextVerifier {
    private final MapKey mapKey;
    private final NamespacedId dimension;
    private final NamespacedId documentId;
    private final BindingIdentity publishedBinding;
    private final Supplier<Optional<BindingIdentity>> currentBinding;
    private final Predicate<WireIdentity.EditorContext> runtimeAuthority;

    public BOMinimapAcceptanceEditorContextVerifier(
            MapKey mapKey,
            NamespacedId dimension,
            NamespacedId documentId,
            BindingIdentity publishedBinding,
            Supplier<Optional<BindingIdentity>> currentBinding,
            Predicate<WireIdentity.EditorContext> runtimeAuthority
    ) {
        this.mapKey = Objects.requireNonNull(mapKey, "mapKey");
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.documentId = Objects.requireNonNull(documentId, "documentId");
        this.publishedBinding = Objects.requireNonNull(
                publishedBinding, "publishedBinding"
        );
        this.currentBinding = Objects.requireNonNull(currentBinding, "currentBinding");
        this.runtimeAuthority = Objects.requireNonNull(
                runtimeAuthority, "runtimeAuthority"
        );
        if (!dimension.equals(publishedBinding.dimension())
                || !documentId.equals(publishedBinding.documentId())) {
            throw new IllegalArgumentException(
                    "published binding does not match the fixture identity"
            );
        }
    }

    public boolean matches(WireIdentity.EditorContext candidate) {
        if (candidate == null
                || candidate.lease().scope() != WireIdentity.Scope.EDITOR
                || !mapKey.equals(candidate.binding().target().mapKey())
                || !dimension.equals(candidate.binding().target().dimension())
                || !documentId.equals(candidate.binding().documentId())
                || candidate.baseRevision() != publishedBinding.revision()
                || !candidate.baseSourceHash().equals(
                publishedBinding.sourceHash()
        )) {
            return false;
        }
        try {
            Optional<BindingIdentity> current = Objects.requireNonNull(
                    currentBinding.get(), "current binding supplier returned null"
            );
            return current.filter(publishedBinding::equals).isPresent()
                    && runtimeAuthority.test(candidate);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public record BindingIdentity(
            NamespacedId dimension,
            NamespacedId documentId,
            long revision,
            Sha256 sourceHash,
            Sha256 runtimeHash
    ) {
        public BindingIdentity {
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(documentId, "documentId");
            Objects.requireNonNull(sourceHash, "sourceHash");
            Objects.requireNonNull(runtimeHash, "runtimeHash");
            if (revision < 0) {
                throw new IllegalArgumentException("revision must be non-negative");
            }
        }
    }
}
