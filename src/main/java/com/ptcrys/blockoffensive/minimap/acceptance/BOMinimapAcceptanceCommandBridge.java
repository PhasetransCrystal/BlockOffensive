package com.ptcrys.blockoffensive.minimap.acceptance;

import com.ptcrys.blockoffensive.command.BOMinimapAcceptanceCommand;
import com.ptcrys.blockoffensive.net.acceptance.BOMinimapAcceptanceAck;
import com.ptcrys.blockoffensive.net.acceptance.BOMinimapAcceptanceEditorContext;
import net.minecraft.server.level.ServerPlayer;

/** Stateless packet-to-command boundary; the active fixture registry remains authoritative. */
public final class BOMinimapAcceptanceCommandBridge {
    private BOMinimapAcceptanceCommandBridge() {
    }

    public static boolean acceptAck(
            ServerPlayer player,
            BOMinimapAcceptanceAck acknowledgement
    ) {
        return BOMinimapAcceptanceCommand.acceptAck(player, acknowledgement);
    }

    public static boolean acceptEditorContext(
            ServerPlayer player,
            BOMinimapAcceptanceEditorContext context
    ) {
        return BOMinimapAcceptanceCommand.acceptEditorContext(player, context);
    }
}
