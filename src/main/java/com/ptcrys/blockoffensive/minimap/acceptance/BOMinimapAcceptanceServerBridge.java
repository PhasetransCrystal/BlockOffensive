package com.ptcrys.blockoffensive.minimap.acceptance;

import com.ptcrys.blockoffensive.net.acceptance.BOMinimapAcceptanceAck;
import com.ptcrys.blockoffensive.net.acceptance.BOMinimapAcceptanceEditorContext;
import net.minecraft.server.level.ServerPlayer;

/** Narrow server bridge for packet handlers; authority stays in the fixture. */
public final class BOMinimapAcceptanceServerBridge {
    private BOMinimapAcceptanceServerBridge() {
    }

    public static boolean acceptAck(ServerPlayer sender, BOMinimapAcceptanceAck acknowledgement) {
        if (sender == null || acknowledgement == null
                || !sender.getUUID().equals(acknowledgement.ownerId())) {
            return false;
        }
        return BOMinimapAcceptanceCommandBridge.acceptAck(sender, acknowledgement);
    }

    public static boolean acceptEditorContext(
            ServerPlayer sender, BOMinimapAcceptanceEditorContext editorContext
    ) {
        if (sender == null || editorContext == null
                || !sender.getUUID().equals(editorContext.ownerId())) {
            return false;
        }
        return BOMinimapAcceptanceCommandBridge.acceptEditorContext(sender, editorContext);
    }
}
