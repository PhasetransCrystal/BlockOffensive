package com.ptcrys.blockoffensive.minimap.acceptance;

import com.ptcrys.fpsmatch.core.minimap.storage.AuthorityJournalProvider;
import com.ptcrys.fpsmatch.core.minimap.storage.MinimapAuthorityJournal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Canonical owner records and NOFOLLOW object witnesses for the acceptance journal. */
final class BOMinimapAcceptanceAuthorityOwnerCodec {
    static final String ROOT_MARKER = ".bo-acceptance-root-owner";
    private static final String AUTHORITY_DIRECTORY = ".bo-acceptance-authority";
    private static final int RECORD_MAGIC = 0x424F414F;
    private static final int MARKER_MAGIC = 0x424F524D;
    private static final int VERSION = 1;
    private static final int NONCE_BYTES = 32;
    private static final int MAX_BYTES = MinimapAuthorityJournal.MAX_IDENTITY_BYTES;
    private static final SecureRandom RANDOM = new SecureRandom();

    private BOMinimapAcceptanceAuthorityOwnerCodec() {
    }

    enum Kind {
        RESERVED,
        SEALED,
        RECEIPT_SELECTED,
        COMMIT_ATTEMPTED,
        CLEANUP_READY
    }

    record Layout(
            Path canonicalRoot,
            Path fixtureRoot,
            Path journalRoot,
            String fixtureRelative,
            UUID actorId,
            UUID runId
    ) {
        static Layout require(
                Path canonicalRoot,
                Path fixtureRoot,
                UUID actorId,
                UUID runId
        ) throws IOException {
            UUID actor = Objects.requireNonNull(actorId, "actorId");
            UUID run = Objects.requireNonNull(runId, "runId");
            Path canonical = Objects.requireNonNull(canonicalRoot, "canonicalRoot")
                    .toAbsolutePath().normalize();
            Path fixture = Objects.requireNonNull(fixtureRoot, "fixtureRoot")
                    .toAbsolutePath().normalize();
            requirePlainChain(canonical, true);
            if (fixture.equals(canonical) || !fixture.startsWith(canonical)
                    || fixture.getParent() == null) {
                throw new IOException("Fixture root must be a strict canonical-root descendant");
            }
            Path authority = canonical.resolve(AUTHORITY_DIRECTORY).normalize();
            if (fixture.startsWith(authority) || authority.startsWith(fixture)) {
                throw new IOException("Fixture root overlaps the external owner ledger");
            }
            Path journal = authority.resolve(actor.toString()).resolve(run.toString()).normalize();
            String relative = canonical.relativize(fixture).toString().replace('\\', '/');
            return new Layout(canonical, fixture, journal, relative, actor, run);
        }

        void prepareOpen() throws IOException {
            if (Files.exists(fixtureRoot, LinkOption.NOFOLLOW_LINKS)) {
                throw new FileAlreadyExistsException(fixtureRoot.toString());
            }
            createPlainDescendants(canonicalRoot, fixtureRoot.getParent());
            createPlainDescendants(canonicalRoot, journalRoot.getParent());
        }

        private static void createPlainDescendants(Path anchor, Path target) throws IOException {
            if (!target.startsWith(anchor)) {
                throw new IOException("Owner directory escaped the canonical root");
            }
            Path cursor = anchor;
            for (Path part : anchor.relativize(target)) {
                cursor = cursor.resolve(part);
                try {
                    Files.createDirectory(cursor);
                    forceDirectory(cursor.getParent());
                } catch (FileAlreadyExistsException exists) {
                    if (!isPlainDirectory(cursor)) {
                        throw new IOException("Owner path is not a plain directory: " + cursor,
                                exists);
                    }
                }
            }
            requirePlainChain(target, true);
        }

        private static void requirePlainChain(Path path, boolean mustExist) throws IOException {
            Path cursor = path.getRoot();
            if (cursor == null) {
                throw new IOException("Owner path is not absolute");
            }
            for (Path part : path) {
                cursor = cursor.resolve(part);
                if (!Files.exists(cursor, LinkOption.NOFOLLOW_LINKS)) {
                    if (mustExist) {
                        throw new NoSuchFileException(cursor.toString());
                    }
                    return;
                }
                if (Files.isSymbolicLink(cursor)) {
                    throw new IOException("Owner path contains a symbolic link: " + cursor);
                }
            }
            if (!isPlainDirectory(path)) {
                throw new IOException("Owner path is not a plain directory: " + path);
            }
        }
    }

    record Witness(
            String normalizedPath,
            String realPath,
            long creationSeconds,
            int creationNanos,
            boolean directory,
            boolean regularFile,
            String fileKey,
            String fileStoreName,
            String fileStoreType,
            long fileSize,
            byte[] contentDigest
    ) {
        Witness {
            contentDigest = contentDigest.clone();
        }

        static Witness capture(Path path) throws IOException {
            Path normalized = path.toAbsolutePath().normalize();
            BasicFileAttributes attributes = Files.readAttributes(
                    normalized, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (Files.isSymbolicLink(normalized) || attributes.isOther()) {
                throw new IOException("Owner witness cannot bind a link/reparse object: " + path);
            }
            FileStore store = Files.getFileStore(normalized);
            Instant creation = attributes.creationTime().toInstant();
            Object key = attributes.fileKey();
            byte[] digest = attributes.isRegularFile()
                    ? sha256(Files.readAllBytes(normalized)) : new byte[0];
            return new Witness(
                    normalized.toString(),
                    normalized.toRealPath(LinkOption.NOFOLLOW_LINKS).normalize().toString(),
                    creation.getEpochSecond(), creation.getNano(),
                    attributes.isDirectory(), attributes.isRegularFile(),
                    key == null ? "" : key.toString(), store.name(), store.type(),
                    attributes.isRegularFile() ? attributes.size() : 0L, digest);
        }

        boolean matches(Path path) throws IOException {
            return same(capture(path));
        }

        boolean same(Witness other) {
            return other != null
                    && normalizedPath.equals(other.normalizedPath)
                    && realPath.equals(other.realPath)
                    && creationSeconds == other.creationSeconds
                    && creationNanos == other.creationNanos
                    && directory == other.directory
                    && regularFile == other.regularFile
                    && fileKey.equals(other.fileKey)
                    && fileStoreName.equals(other.fileStoreName)
                    && fileStoreType.equals(other.fileStoreType)
                    && fileSize == other.fileSize
                    && Arrays.equals(contentDigest, other.contentDigest);
        }

        void write(DataOutputStream output) throws IOException {
            writeString(output, normalizedPath);
            writeString(output, realPath);
            output.writeLong(creationSeconds);
            output.writeInt(creationNanos);
            output.writeBoolean(directory);
            output.writeBoolean(regularFile);
            writeString(output, fileKey);
            writeString(output, fileStoreName);
            writeString(output, fileStoreType);
            output.writeLong(fileSize);
            writeBytes(output, contentDigest);
        }

        static Witness read(DataInputStream input) throws IOException {
            return new Witness(
                    readString(input), readString(input), input.readLong(), input.readInt(),
                    input.readBoolean(), input.readBoolean(), readString(input),
                    readString(input), readString(input), input.readLong(), readBytes(input));
        }

        @Override
        public byte[] contentDigest() {
            return contentDigest.clone();
        }
    }

    record OwnerRecord(
            Kind kind,
            int ownerGeneration,
            UUID actorId,
            UUID runId,
            String journalInstance,
            String fixtureRelative,
            byte[] rootNonce,
            Witness canonicalWitness,
            Witness parentWitness,
            Witness rootWitness,
            Witness markerWitness,
            byte[] receipt,
            boolean commitAttempted
    ) {
        OwnerRecord {
            rootNonce = rootNonce.clone();
            receipt = receipt.clone();
        }

        static OwnerRecord reserved(Layout layout) throws IOException {
            byte[] nonce = new byte[NONCE_BYTES];
            RANDOM.nextBytes(nonce);
            return new OwnerRecord(
                    Kind.RESERVED, 1, layout.actorId(), layout.runId(),
                    "bo-owner-" + layout.runId(), layout.fixtureRelative(), nonce,
                    Witness.capture(layout.canonicalRoot()),
                    Witness.capture(layout.fixtureRoot().getParent()),
                    null, null, new byte[0], false);
        }

        OwnerRecord seal(Layout layout, byte[] canonicalReceipt) throws IOException {
            return successor(
                    Kind.SEALED,
                    Witness.capture(layout.fixtureRoot()),
                    Witness.capture(layout.fixtureRoot().resolve(ROOT_MARKER)),
                    requireReceipt(canonicalReceipt), false);
        }

        OwnerRecord selectReceipt(byte[] canonicalReceipt) {
            return successor(
                    Kind.RECEIPT_SELECTED, rootWitness, markerWitness,
                    requireReceipt(canonicalReceipt), commitAttempted);
        }

        OwnerRecord withCommitAttempted() {
            return successor(
                    Kind.COMMIT_ATTEMPTED, rootWitness, markerWitness, receipt, true);
        }

        OwnerRecord cleanupReady() {
            return successor(
                    Kind.CLEANUP_READY, rootWitness, markerWitness, receipt,
                    commitAttempted);
        }

        private OwnerRecord successor(
                Kind nextKind,
                Witness nextRoot,
                Witness nextMarker,
                byte[] nextReceipt,
                boolean nextCommitAttempted
        ) {
            return new OwnerRecord(
                    nextKind, Math.addExact(ownerGeneration, 1), actorId, runId,
                    journalInstance, fixtureRelative, rootNonce,
                    canonicalWitness, parentWitness, nextRoot, nextMarker, nextReceipt,
                    nextCommitAttempted);
        }

        boolean validSuccessorOf(OwnerRecord previous) {
            if (ownerGeneration != previous.ownerGeneration + 1
                    || !actorId.equals(previous.actorId) || !runId.equals(previous.runId)
                    || !journalInstance.equals(previous.journalInstance)
                    || !fixtureRelative.equals(previous.fixtureRelative)
                    || !Arrays.equals(rootNonce, previous.rootNonce)
                    || !canonicalWitness.same(previous.canonicalWitness)
                    || !parentWitness.same(previous.parentWitness)) {
                return false;
            }
            return switch (kind) {
                case RESERVED -> false;
                case SEALED -> previous.kind == Kind.RESERVED
                        && rootWitness != null && markerWitness != null
                        && receipt.length > 0 && !commitAttempted;
                case RECEIPT_SELECTED -> (previous.kind == Kind.SEALED
                        || previous.kind == Kind.RECEIPT_SELECTED
                        || previous.kind == Kind.COMMIT_ATTEMPTED)
                        && sameRootIdentity(previous)
                        && commitAttempted == previous.commitAttempted;
                case COMMIT_ATTEMPTED -> (previous.kind == Kind.SEALED
                        || previous.kind == Kind.RECEIPT_SELECTED)
                        && !previous.commitAttempted && commitAttempted
                        && sameSealedIdentity(previous);
                case CLEANUP_READY -> (previous.kind == Kind.SEALED
                        || previous.kind == Kind.RECEIPT_SELECTED
                        || previous.kind == Kind.COMMIT_ATTEMPTED)
                        && commitAttempted == previous.commitAttempted
                        && sameSealedIdentity(previous);
            };
        }

        private boolean sameSealedIdentity(OwnerRecord previous) {
            return sameRootIdentity(previous)
                    && Arrays.equals(receipt, previous.receipt);
        }

        private boolean sameRootIdentity(OwnerRecord previous) {
            return rootWitness.same(previous.rootWitness)
                    && markerWitness.same(previous.markerWitness);
        }

        byte[] encode() throws IOException {
            byte[] encoded = BOMinimapAcceptanceAuthorityOwnerCodec.encode(output -> {
                output.writeInt(RECORD_MAGIC);
                output.writeInt(VERSION);
                output.writeByte(kind.ordinal());
                output.writeInt(ownerGeneration);
                writeUuid(output, actorId);
                writeUuid(output, runId);
                writeString(output, journalInstance);
                writeString(output, fixtureRelative);
                writeBytes(output, rootNonce);
                writeWitness(output, canonicalWitness);
                writeWitness(output, parentWitness);
                writeWitness(output, rootWitness);
                writeWitness(output, markerWitness);
                writeBytes(output, receipt);
                output.writeBoolean(commitAttempted);
            });
            if (encoded.length > MAX_BYTES) {
                throw new IOException("Acceptance owner record exceeds journal identity bounds");
            }
            return encoded;
        }

        static OwnerRecord decode(byte[] bytes) throws IOException {
            if (bytes.length == 0 || bytes.length > MAX_BYTES) {
                throw new IOException("Acceptance owner record length is invalid");
            }
            try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
                if (input.readInt() != RECORD_MAGIC || input.readInt() != VERSION) {
                    throw new IOException("Acceptance owner record header is invalid");
                }
                int kindCode = input.readUnsignedByte();
                if (kindCode >= Kind.values().length) {
                    throw new IOException("Acceptance owner record phase is invalid");
                }
                OwnerRecord record = new OwnerRecord(
                        Kind.values()[kindCode], input.readInt(), readUuid(input),
                        readUuid(input), readString(input), readString(input), readBytes(input),
                        readWitness(input), readWitness(input), readWitness(input),
                        readWitness(input), readBytes(input), input.readBoolean());
                if (input.read() != -1 || record.ownerGeneration <= 0
                        || record.rootNonce.length != NONCE_BYTES
                        || record.canonicalWitness == null || record.parentWitness == null) {
                    throw new IOException("Acceptance owner record is not canonical");
                }
                return record;
            } catch (EOFException truncated) {
                throw new IOException("Acceptance owner record is truncated", truncated);
            }
        }

        @Override
        public byte[] rootNonce() {
            return rootNonce.clone();
        }

        @Override
        public byte[] receipt() {
            return receipt.clone();
        }
    }

    static void createFixtureRoot(Layout layout, OwnerRecord reserved) throws IOException {
        Files.createDirectory(layout.fixtureRoot());
        forceDirectory(layout.fixtureRoot().getParent());
        byte[] marker = markerBytes(reserved);
        writeCreateNew(layout.fixtureRoot().resolve(ROOT_MARKER), marker);
        forceDirectory(layout.fixtureRoot());
    }

    static boolean verifyExternal(Layout layout, OwnerRecord record) throws IOException {
        if (!record.actorId().equals(layout.actorId()) || !record.runId().equals(layout.runId())
                || !record.fixtureRelative().equals(layout.fixtureRelative())
                || !record.canonicalWitness().matches(layout.canonicalRoot())
                || !record.parentWitness().matches(layout.fixtureRoot().getParent())) {
            return false;
        }
        if (record.kind() == Kind.RESERVED) {
            return !Files.exists(layout.fixtureRoot(), LinkOption.NOFOLLOW_LINKS);
        }
        if (!Files.exists(layout.fixtureRoot(), LinkOption.NOFOLLOW_LINKS)) {
            return record.kind() == Kind.CLEANUP_READY;
        }
        Path marker = layout.fixtureRoot().resolve(ROOT_MARKER);
        return record.rootWitness() != null && record.markerWitness() != null
                && record.rootWitness().matches(layout.fixtureRoot())
                && record.markerWitness().matches(marker)
                && Arrays.equals(Files.readAllBytes(marker), markerBytes(record));
    }

    static boolean verifyLiveReservedRoot(Layout layout, OwnerRecord reserved)
            throws IOException {
        if (!reserved.canonicalWitness().matches(layout.canonicalRoot())
                || !reserved.parentWitness().matches(layout.fixtureRoot().getParent())
                || !isPlainDirectory(layout.fixtureRoot())) {
            return false;
        }
        Path marker = layout.fixtureRoot().resolve(ROOT_MARKER);
        return isPlainFile(marker)
                && Arrays.equals(Files.readAllBytes(marker), markerBytes(reserved));
    }

    static AuthorityJournalProvider defaultProvider() throws IOException {
        return MinimapAuthorityJournal.provider();
    }

    private static byte[] markerBytes(OwnerRecord record) throws IOException {
        return encode(output -> {
            output.writeInt(MARKER_MAGIC);
            output.writeInt(VERSION);
            writeUuid(output, record.actorId());
            writeUuid(output, record.runId());
            writeString(output, record.fixtureRelative());
            writeBytes(output, record.rootNonce());
        });
    }

    private static byte[] requireReceipt(byte[] receipt) {
        byte[] copy = Objects.requireNonNull(receipt, "canonicalReceipt").clone();
        if (copy.length == 0 || copy.length > MAX_BYTES) {
            throw new IllegalArgumentException("Canonical receipt length is invalid");
        }
        return copy;
    }

    private static void writeCreateNew(Path file, byte[] bytes) throws IOException {
        try (FileChannel channel = FileChannel.open(
                file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE,
                LinkOption.NOFOLLOW_LINKS)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
    }

    private static void forceDirectory(Path directory) throws IOException {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
            return;
        } catch (IOException nioFailure) {
            if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
                throw nioFailure;
            }
            try {
                Class<?> type = Class.forName(
                        "com.ptcrys.fpsmatch.core.minimap.storage."
                                + "WindowsDirectorySynchronizer");
                Method sync = type.getDeclaredMethod("sync", Path.class);
                sync.setAccessible(true);
                sync.invoke(null, directory);
            } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException
                     | RuntimeException reflectionFailure) {
                nioFailure.addSuppressed(reflectionFailure);
                throw nioFailure;
            } catch (java.lang.reflect.InvocationTargetException invoked) {
                if (invoked.getCause() instanceof IOException io) {
                    throw io;
                }
                nioFailure.addSuppressed(invoked.getCause());
                throw nioFailure;
            }
        }
    }

    private static boolean isPlainDirectory(Path path) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(
                path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        return attributes.isDirectory() && !attributes.isOther() && !Files.isSymbolicLink(path);
    }

    private static boolean isPlainFile(Path path) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(
                path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        return attributes.isRegularFile() && !attributes.isOther()
                && !Files.isSymbolicLink(path);
    }

    static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static byte[] encode(Encoder encoder) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            encoder.write(output);
        }
        return bytes.toByteArray();
    }

    private static void writeUuid(DataOutputStream output, UUID value) throws IOException {
        output.writeLong(value.getMostSignificantBits());
        output.writeLong(value.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream input) throws IOException {
        return new UUID(input.readLong(), input.readLong());
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        writeBytes(output, value.getBytes(StandardCharsets.UTF_8));
    }

    private static String readString(DataInputStream input) throws IOException {
        return new String(readBytes(input), StandardCharsets.UTF_8);
    }

    private static void writeBytes(DataOutputStream output, byte[] value) throws IOException {
        output.writeInt(value.length);
        output.write(value);
    }

    private static byte[] readBytes(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > MAX_BYTES) {
            throw new IOException("Canonical owner field length is invalid");
        }
        byte[] value = input.readNBytes(length);
        if (value.length != length) {
            throw new EOFException("Canonical owner field is truncated");
        }
        return value;
    }

    private static void writeWitness(DataOutputStream output, Witness witness)
            throws IOException {
        output.writeBoolean(witness != null);
        if (witness != null) {
            witness.write(output);
        }
    }

    private static Witness readWitness(DataInputStream input) throws IOException {
        return input.readBoolean() ? Witness.read(input) : null;
    }

    @FunctionalInterface
    private interface Encoder {
        void write(DataOutputStream output) throws IOException;
    }
}
