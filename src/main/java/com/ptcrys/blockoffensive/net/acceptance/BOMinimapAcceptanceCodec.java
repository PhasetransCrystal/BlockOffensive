package com.ptcrys.blockoffensive.net.acceptance;

import com.ptcrys.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.ptcrys.fpsmatch.core.minimap.model.MapKey;
import com.ptcrys.fpsmatch.core.minimap.model.NamespacedId;
import com.ptcrys.fpsmatch.core.minimap.wire.WireIdentity;
import com.ptcrys.fpsmatch.core.minimap.wire.WireReader;
import com.ptcrys.fpsmatch.core.minimap.wire.WireWriter;

import java.util.Arrays;

/** Bounded, loader-neutral codec shared by the BO packet wrappers and tests. */
public final class BOMinimapAcceptanceCodec {
    public static final int VERSION = 1;
    public static final int MAX_PACKET_BYTES = 2_048;

    private BOMinimapAcceptanceCodec() {
    }

    public static byte[] encodeScene(BOMinimapAcceptanceSceneSignal signal) {
        WireWriter writer = new WireWriter(MAX_PACKET_BYTES);
        writer.writeUnsignedByte(VERSION);
        writer.writeUnsignedByte(signal.action().code());
        writer.writeUnsignedByte(signal.scene().code());
        writer.writeUuid(signal.ownerId());
        writer.writeUuid(signal.fixtureRunId());
        writer.writeNonNegativeVarLong(signal.epoch());
        writeMapIdentity(writer, signal.mapKey(), signal.dimension(), signal.documentId());
        writer.writeNonNegativeVarLong(signal.revision());
        writer.writeHash(signal.sourceHash());
        writer.writeHash(signal.runtimeHash());
        return writer.toByteArray();
    }

    public static BOMinimapAcceptanceSceneSignal decodeScene(byte[] bytes) {
        WireReader reader = reader(bytes);
        requireVersion(reader);
        BOMinimapAcceptanceSceneSignal.Action action =
                BOMinimapAcceptanceSceneSignal.Action.fromCode(reader.readUnsignedByte());
        BOMinimapAcceptanceSceneSignal.Scene scene =
                BOMinimapAcceptanceSceneSignal.Scene.fromCode(reader.readUnsignedByte());
        var owner = reader.readUuid();
        var run = reader.readUuid();
        long epoch = positive(reader.readNonNegativeVarLong(), "epoch");
        MapIdentity identity = readMapIdentity(reader);
        long revision = positive(reader.readNonNegativeVarLong(), "revision");
        var source = reader.readHash();
        var runtime = reader.readHash();
        reader.requireFinished();
        return new BOMinimapAcceptanceSceneSignal(
                action, scene, owner, run, epoch, identity.mapKey(), identity.dimension(),
                identity.documentId(), revision, source, runtime
        );
    }

    public static byte[] encodeAck(BOMinimapAcceptanceAck ack) {
        WireWriter writer = new WireWriter(MAX_PACKET_BYTES);
        writer.writeUnsignedByte(VERSION);
        writer.writeUuid(ack.ownerId());
        writer.writeUuid(ack.fixtureRunId());
        writer.writeNonNegativeVarLong(ack.epoch());
        writer.writeUnsignedByte(ack.outcome().code());
        writer.writeUtf8(ack.detail(), MinimapHardLimits.MAX_ERROR_DETAIL_UTF8_BYTES);
        return writer.toByteArray();
    }

    public static BOMinimapAcceptanceAck decodeAck(byte[] bytes) {
        WireReader reader = reader(bytes);
        requireVersion(reader);
        var owner = reader.readUuid();
        var run = reader.readUuid();
        long epoch = positive(reader.readNonNegativeVarLong(), "epoch");
        var outcome = BOMinimapAcceptanceAck.Outcome.fromCode(reader.readUnsignedByte());
        String detail = reader.readUtf8(MinimapHardLimits.MAX_ERROR_DETAIL_UTF8_BYTES);
        reader.requireFinished();
        return new BOMinimapAcceptanceAck(owner, run, epoch, outcome, detail);
    }

    public static byte[] encodeEditorContext(BOMinimapAcceptanceEditorContext message) {
        WireWriter writer = new WireWriter(MAX_PACKET_BYTES);
        writer.writeUnsignedByte(VERSION);
        writer.writeUuid(message.ownerId());
        writer.writeUuid(message.fixtureRunId());
        writer.writeNonNegativeVarLong(message.epoch());
        writeEditorContext(writer, message.context());
        return writer.toByteArray();
    }

    public static BOMinimapAcceptanceEditorContext decodeEditorContext(byte[] bytes) {
        WireReader reader = reader(bytes);
        requireVersion(reader);
        var owner = reader.readUuid();
        var run = reader.readUuid();
        long epoch = positive(reader.readNonNegativeVarLong(), "epoch");
        WireIdentity.EditorContext context = readEditorContext(reader);
        reader.requireFinished();
        return new BOMinimapAcceptanceEditorContext(owner, run, epoch, context);
    }

    private static void writeMapIdentity(
            WireWriter writer, MapKey mapKey, NamespacedId dimension, NamespacedId documentId
    ) {
        writer.writeUtf8(mapKey.gameType(), MinimapHardLimits.MAX_GAME_TYPE_UTF8_BYTES);
        writer.writeUtf8(mapKey.mapName(), MinimapHardLimits.MAX_MAP_NAME_UTF8_BYTES);
        writeNamespacedId(writer, dimension);
        writeNamespacedId(writer, documentId);
    }

    private static MapIdentity readMapIdentity(WireReader reader) {
        MapKey mapKey = new MapKey(
                reader.readUtf8(MinimapHardLimits.MAX_GAME_TYPE_UTF8_BYTES),
                reader.readUtf8(MinimapHardLimits.MAX_MAP_NAME_UTF8_BYTES)
        );
        return new MapIdentity(mapKey, readNamespacedId(reader), readNamespacedId(reader));
    }

    private static void writeNamespacedId(WireWriter writer, NamespacedId id) {
        writer.writeUtf8(id.namespace(), MinimapHardLimits.MAX_NAMESPACE_UTF8_BYTES);
        writer.writeUtf8(id.path(), MinimapHardLimits.MAX_NAMESPACED_PATH_UTF8_BYTES);
    }

    private static NamespacedId readNamespacedId(WireReader reader) {
        return new NamespacedId(
                reader.readUtf8(MinimapHardLimits.MAX_NAMESPACE_UTF8_BYTES),
                reader.readUtf8(MinimapHardLimits.MAX_NAMESPACED_PATH_UTF8_BYTES)
        );
    }

    private static void writeEditorContext(WireWriter writer, WireIdentity.EditorContext context) {
        WireIdentity.ScopeLease lease = context.lease();
        writer.writeUnsignedByte(lease.scope().code());
        writer.writeNonNegativeVarLong(lease.scopeEpoch());
        writer.writeNonNegativeVarLong(lease.runtimeGeneration());
        WireIdentity.DocumentBinding binding = context.binding();
        writeMapIdentity(writer, binding.target().mapKey(), binding.target().dimension(), binding.documentId());
        writer.writeUuid(context.sessionId());
        writer.writeUuid(context.draftId());
        writer.writeNonNegativeVarLong(context.baseRevision());
        writer.writeHash(context.baseSourceHash());
        writer.writeHash(context.draftRootHash());
        writer.writeNonNegativeVarLong(context.ackCursor());
    }

    private static WireIdentity.EditorContext readEditorContext(WireReader reader) {
        WireIdentity.ScopeLease lease = new WireIdentity.ScopeLease(
                WireIdentity.Scope.fromCode(reader.readUnsignedByte()),
                reader.readNonNegativeVarLong(), reader.readNonNegativeVarLong()
        );
        MapIdentity identity = readMapIdentity(reader);
        WireIdentity.DocumentBinding binding = new WireIdentity.DocumentBinding(
                new WireIdentity.MapTarget(identity.mapKey(), identity.dimension()), identity.documentId()
        );
        return new WireIdentity.EditorContext(
                lease, binding, reader.readUuid(), reader.readUuid(),
                reader.readNonNegativeVarLong(), reader.readHash(), reader.readHash(),
                reader.readNonNegativeVarLong()
        );
    }

    private static WireReader reader(byte[] bytes) {
        if (bytes == null || bytes.length > MAX_PACKET_BYTES) {
            throw new IllegalArgumentException("acceptance packet exceeds its hard limit");
        }
        return new WireReader(Arrays.copyOf(bytes, bytes.length));
    }

    private static void requireVersion(WireReader reader) {
        int version = reader.readUnsignedByte();
        if (version != VERSION) {
            throw new IllegalArgumentException("unsupported acceptance packet version: " + version);
        }
    }

    private static long positive(long value, String label) {
        if (value <= 0) {
            throw new IllegalArgumentException(label + " must be positive");
        }
        return value;
    }

    private record MapIdentity(MapKey mapKey, NamespacedId dimension, NamespacedId documentId) {
    }
}
