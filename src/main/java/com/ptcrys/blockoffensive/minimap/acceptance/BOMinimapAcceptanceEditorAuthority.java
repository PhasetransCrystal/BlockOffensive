package com.ptcrys.blockoffensive.minimap.acceptance;

import com.ptcrys.fpsmatch.common.capability.map.MinimapCapability;
import com.ptcrys.fpsmatch.core.minimap.model.MapKey;
import com.ptcrys.fpsmatch.core.minimap.model.NamespacedId;
import com.ptcrys.fpsmatch.core.minimap.wire.WireIdentity;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

final class BOMinimapAcceptanceEditorAuthority {
    private BOMinimapAcceptanceEditorAuthority() {
    }

    static boolean matches(
            MapKey mapKey,
            NamespacedId dimension,
            NamespacedId documentId,
            MinimapCapability.Binding publishedBinding,
            Supplier<Optional<MinimapCapability.Binding>> currentBinding,
            Predicate<WireIdentity.EditorContext> runtimeAuthority,
            WireIdentity.EditorContext candidate
    ) {
        if (publishedBinding == null) {
            return false;
        }
        return new BOMinimapAcceptanceEditorContextVerifier(
                mapKey,
                dimension,
                documentId,
                identity(publishedBinding),
                () -> currentBinding.get().map(
                        BOMinimapAcceptanceEditorAuthority::identity
                ),
                runtimeAuthority
        ).matches(candidate);
    }

    private static BOMinimapAcceptanceEditorContextVerifier.BindingIdentity identity(
            MinimapCapability.Binding binding
    ) {
        return new BOMinimapAcceptanceEditorContextVerifier.BindingIdentity(
                binding.dimension(), binding.documentId(), binding.revision(),
                binding.sourceHash(), binding.runtimeHash()
        );
    }
}
