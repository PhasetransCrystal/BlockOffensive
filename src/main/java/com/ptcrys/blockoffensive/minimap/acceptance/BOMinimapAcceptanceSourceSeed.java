package com.ptcrys.blockoffensive.minimap.acceptance;

import com.ptcrys.blockoffensive.minimap.CSGameMinimapRegionProvider;
import com.ptcrys.fpsmatch.core.minimap.contract.MinimapFormatContract;
import com.ptcrys.fpsmatch.core.minimap.editor.document.EditorSourceDefaults;
import com.ptcrys.fpsmatch.core.minimap.format.CanonicalPngCodecV1;
import com.ptcrys.fpsmatch.core.minimap.format.CanonicalZipWriter;
import com.ptcrys.fpsmatch.core.minimap.format.CompiledMapPair;
import com.ptcrys.fpsmatch.core.minimap.format.RuntimeCompileRequest;
import com.ptcrys.fpsmatch.core.minimap.format.RuntimeMapCompiler;
import com.ptcrys.fpsmatch.core.minimap.format.SourceMap;
import com.ptcrys.fpsmatch.core.minimap.format.SourceMapReader;
import com.ptcrys.fpsmatch.core.minimap.format.SourceMapWriter;
import com.ptcrys.fpsmatch.core.minimap.model.BlendMode;
import com.ptcrys.fpsmatch.core.minimap.model.CanvasBounds;
import com.ptcrys.fpsmatch.core.minimap.model.CanvasPoint;
import com.ptcrys.fpsmatch.core.minimap.model.ConnectionsFile;
import com.ptcrys.fpsmatch.core.minimap.model.ContainerPath;
import com.ptcrys.fpsmatch.core.minimap.model.ControlPoint;
import com.ptcrys.fpsmatch.core.minimap.model.DisplayLabel;
import com.ptcrys.fpsmatch.core.minimap.model.FillStyle;
import com.ptcrys.fpsmatch.core.minimap.model.FloorCalibration;
import com.ptcrys.fpsmatch.core.minimap.model.LayerCommon;
import com.ptcrys.fpsmatch.core.minimap.model.MapKey;
import com.ptcrys.fpsmatch.core.minimap.model.MinimapDefinition;
import com.ptcrys.fpsmatch.core.minimap.model.MinimapRegion;
import com.ptcrys.fpsmatch.core.minimap.model.NamespacedId;
import com.ptcrys.fpsmatch.core.minimap.model.PolygonGeometry;
import com.ptcrys.fpsmatch.core.minimap.model.RasterPaintLayer;
import com.ptcrys.fpsmatch.core.minimap.model.RegionStyle;
import com.ptcrys.fpsmatch.core.minimap.model.RegionStyleOverride;
import com.ptcrys.fpsmatch.core.minimap.model.RegionsFile;
import com.ptcrys.fpsmatch.core.minimap.model.RgbaColor;
import com.ptcrys.fpsmatch.core.minimap.model.Sha256;
import com.ptcrys.fpsmatch.core.minimap.model.SourceDocument;
import com.ptcrys.fpsmatch.core.minimap.model.SourceFloor;
import com.ptcrys.fpsmatch.core.minimap.model.StrokeStyle;
import com.ptcrys.fpsmatch.core.minimap.model.StylesFile;
import com.ptcrys.fpsmatch.core.minimap.model.TextAppearance;
import com.ptcrys.fpsmatch.core.minimap.model.WorldBounds;
import com.ptcrys.fpsmatch.core.minimap.model.WorldPoint2D;
import com.ptcrys.fpsmatch.core.minimap.region.RuntimeRegionDescriptor;
import com.ptcrys.fpsmatch.core.minimap.region.WorldAxisAlignedBounds;
import com.ptcrys.fpsmatch.core.minimap.storage.CurrentPublication;
import com.ptcrys.fpsmatch.core.minimap.storage.MinimapRepository;
import com.ptcrys.fpsmatch.core.minimap.storage.PublishDescriptor;
import com.ptcrys.fpsmatch.core.minimap.storage.PublishOutcome;
import com.ptcrys.fpsmatch.core.minimap.storage.PublishTarget;
import com.ptcrys.fpsmatch.core.minimap.storage.PublishTransaction;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/** Builds and commits the typed authored base used only by the BO acceptance fixture. */
final class BOMinimapAcceptanceSourceSeed {
    private static final int CANVAS_EDGE = 512;
    private static final int TILE_EDGE = 128;
    private static final String FLOOR_ID = "ground";
    private static final com.ptcrys.fpsmatch.core.minimap.model.CompilerProfile PROFILE =
            new com.ptcrys.fpsmatch.core.minimap.model.CompilerProfile(
                    NamespacedId.parse("fpsmatch:editor"), MinimapFormatContract.CURRENT);
    private static final Set<String> SUPPORTED_SEMANTICS = Set.of(
            CSGameMinimapRegionProvider.SEMANTIC_MAP_BOUNDARY,
            CSGameMinimapRegionProvider.SEMANTIC_BOMB_SITE,
            CSGameMinimapRegionProvider.SEMANTIC_SPAWN,
            CSGameMinimapRegionProvider.SEMANTIC_SHOP
    );

    private BOMinimapAcceptanceSourceSeed() {
    }

    static Publication publish(
            MinimapRepository repository,
            MapKey mapKey,
            NamespacedId dimension,
            NamespacedId documentId,
            List<RuntimeRegionDescriptor> liveRegions
    ) {
        return publish(
                repository, mapKey, dimension, documentId, liveRegions,
                ignored -> { }, repositoryActions(repository));
    }

    static Publication publish(
            MinimapRepository repository,
            MapKey mapKey,
            NamespacedId dimension,
            NamespacedId documentId,
            List<RuntimeRegionDescriptor> liveRegions,
            Consumer<Attempt> attemptObserver,
            RepositoryActions repositoryActions
    ) {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(attemptObserver, "attemptObserver");
        Objects.requireNonNull(repositoryActions, "repositoryActions");
        PublishTransaction transaction = repository.reserve(
                mapKey, dimension, documentId, 0L);
        boolean commitAttempted = false;
        try {
            CompiledSeed seed = compile(
                    mapKey, dimension, documentId,
                    transaction.publishRevision(), liveRegions);
            transaction = repository.prepare(
                    transaction, seed.sourceBytes(), seed.runtimeBytes());
            attemptObserver.accept(new Attempt(
                    transaction.target(), transaction.descriptor()));
            commitAttempted = true;
            PublishOutcome outcome = repositoryActions.commit(transaction);
            if (!outcome.committed()) {
                throw new IllegalStateException(
                        "Acceptance seed commit did not complete: " + outcome.message());
            }
            CurrentPublication current = repository.currentPublication(mapKey)
                    .orElseThrow(() -> new IllegalStateException(
                            "Acceptance seed CURRENT is unavailable"));
            requireExactCurrent(current, mapKey, dimension, documentId, transaction);
            return new Publication(
                    current,
                    current.record().descriptor().sourceHash(),
                    current.record().descriptor().runtimeHash());
        } catch (RuntimeException failure) {
            if (!commitAttempted) {
                try {
                    repository.abort(transaction, "acceptance seed failed");
                } catch (RuntimeException abortFailure) {
                    failure.addSuppressed(abortFailure);
                }
            } else {
                try {
                    PublishOutcome recovered = repositoryActions.recover(mapKey);
                    if (recovered.status() != PublishOutcome.Status.UNAVAILABLE
                            && recovered.status()
                            != PublishOutcome.Status.COMMIT_STATUS_UNKNOWN) {
                        Optional<CurrentPublication> current =
                                repository.currentPublication(mapKey);
                        if (current.isPresent()) {
                            CurrentPublication exact = current.orElseThrow();
                            requireExactCurrent(
                                    exact, mapKey, dimension, documentId, transaction);
                            return new Publication(
                                    exact,
                                    exact.record().descriptor().sourceHash(),
                                    exact.record().descriptor().runtimeHash());
                        }
                    }
                } catch (RuntimeException recoveryFailure) {
                    failure.addSuppressed(recoveryFailure);
                }
            }
            throw failure;
        }
    }

    private static RepositoryActions repositoryActions(MinimapRepository repository) {
        return new RepositoryActions() {
            @Override
            public PublishOutcome commit(PublishTransaction transaction) {
                return repository.commit(transaction);
            }

            @Override
            public PublishOutcome recover(MapKey mapKey) {
                return repository.recover(mapKey);
            }
        };
    }

    static CompiledSeed compile(
            MapKey mapKey,
            NamespacedId dimension,
            NamespacedId documentId,
            long revision,
            List<RuntimeRegionDescriptor> liveRegions
    ) {
        MinimapDefinition definition = definition(
                mapKey, dimension, documentId, revision, liveRegions);
        byte[] sourceBytes = SourceMapWriter.write(definition);
        try (SourceMap source = SourceMapReader.read(sourceBytes)) {
            CompiledMapPair compiled = RuntimeMapCompiler.compile(
                    source,
                    RuntimeCompileRequest.forSource(
                            source.manifest(), revision, PROFILE, transparentTiles()));
            return new CompiledSeed(sourceBytes, compiled.runtimeBytes());
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to close acceptance seed source", failure);
        }
    }

    static MinimapDefinition definition(
            MapKey mapKey,
            NamespacedId dimension,
            NamespacedId documentId,
            long revision,
            List<RuntimeRegionDescriptor> liveRegions
    ) {
        Objects.requireNonNull(mapKey, "mapKey");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(documentId, "documentId");
        List<RuntimeRegionDescriptor> regions = List.copyOf(liveRegions);
        if (regions.isEmpty()) {
            throw new IllegalArgumentException("Acceptance seed requires live regions");
        }
        validateDescriptors(regions);
        RuntimeRegionDescriptor boundary = regions.stream()
                .filter(region -> region.semanticType().equals(
                        CSGameMinimapRegionProvider.SEMANTIC_MAP_BOUNDARY))
                .reduce((left, right) -> {
                    throw new IllegalArgumentException(
                            "Acceptance seed requires one map boundary");
                })
                .orElseThrow(() -> new IllegalArgumentException(
                        "Acceptance seed requires a map boundary"));
        WorldAxisAlignedBounds world = boundary.worldBounds();
        if (world.minX() == world.maxX() || world.minZ() == world.maxZ()) {
            throw new IllegalArgumentException("Acceptance map boundary has zero horizontal area");
        }
        requireInsideBoundary(regions, world);

        CanvasBounds canvas = new CanvasBounds(CANVAS_EDGE, CANVAS_EDGE);
        RasterPaintLayer paint = new RasterPaintLayer(new LayerCommon(
                "paint", DisplayLabel.literal("Paint"), true, false, 1.0,
                BlendMode.NORMAL, Optional.empty(), false));
        MinimapDefinition base = EditorSourceDefaults.createDefinition(
                mapKey, dimension, documentId, revision, canvas, TILE_EDGE,
                FLOOR_ID, List.of(paint));
        FloorCalibration calibration = calibration(world);
        SourceFloor oldFloor = base.document().floors().get(0);
        SourceFloor floor = new SourceFloor(
                oldFloor.selection(), oldFloor.label(), oldFloor.contentBounds(),
                oldFloor.background(), calibration, oldFloor.layers());
        SourceDocument document = new SourceDocument(
                new WorldBounds(world.minX(), world.minZ(), world.maxX(), world.maxZ()),
                canvas, base.document().defaultViewMode(), List.of(floor),
                base.document().layerOrder());

        var transform = calibration.fit().transform();
        Map<String, RuntimeRegionDescriptor> profiles = new LinkedHashMap<>();
        for (RuntimeRegionDescriptor region : regions) {
            profiles.putIfAbsent(region.semanticType(), region);
        }
        List<MinimapRegion> authoredRegions = new ArrayList<>();
        List<com.ptcrys.fpsmatch.core.minimap.model.MinimapStyle> styles =
                new ArrayList<>();
        // Authored gameplay references must be complete so a live merge keeps the
        // committed runtime identity; visual styles remain shared per semantic.
        for (RuntimeRegionDescriptor descriptor : regions) {
            String slug = semanticSlug(descriptor.semanticType());
            NamespacedId styleId = NamespacedId.parse(
                    "blockoffensive:acceptance_" + slug);
            ProjectedRegion projected = project(transform, descriptor.worldBounds());
            authoredRegions.add(new MinimapRegion(
                    descriptor.id(),
                    FLOOR_ID,
                    DisplayLabel.literal(descriptor.label()),
                    new PolygonGeometry(projected.corners()),
                    NamespacedId.parse(descriptor.semanticType()),
                    descriptor.tags().stream().map(NamespacedId::parse).toList(),
                    descriptor.gameplayReference().map(NamespacedId::parse),
                    styleId,
                    RegionStyleOverride.empty(),
                    projected.anchor(),
                    descriptor.priority(),
                    0.0,
                    64.0
            ));
        }
        for (Map.Entry<String, RuntimeRegionDescriptor> entry : profiles.entrySet()) {
            String slug = semanticSlug(entry.getKey());
            NamespacedId styleId = NamespacedId.parse(
                    "blockoffensive:acceptance_" + slug);
            styles.add(style(styleId, entry.getKey()));
        }
        return new MinimapDefinition(
                base.manifest(), document, new RegionsFile(authoredRegions),
                new ConnectionsFile(List.of()), new StylesFile(styles));
    }

    private static void validateDescriptors(List<RuntimeRegionDescriptor> regions) {
        Set<String> ids = new HashSet<>();
        Set<String> references = new HashSet<>();
        for (RuntimeRegionDescriptor region : regions) {
            Objects.requireNonNull(region, "live region");
            if (!FLOOR_ID.equals(region.floorId())) {
                throw new IllegalArgumentException(
                        "Acceptance seed regions must use the ground floor");
            }
            if (!SUPPORTED_SEMANTICS.contains(region.semanticType())) {
                throw new IllegalArgumentException(
                        "Unsupported acceptance region semantic: " + region.semanticType());
            }
            if (!ids.add(region.id())) {
                throw new IllegalArgumentException(
                        "Duplicate acceptance region id: " + region.id());
            }
            String reference = region.gameplayReference().orElseThrow(() ->
                    new IllegalArgumentException(
                            "Acceptance live region requires a gameplay reference: "
                                    + region.id()));
            if (!references.add(reference)) {
                throw new IllegalArgumentException(
                        "Duplicate acceptance gameplay reference: " + reference);
            }
        }
    }

    private static void requireInsideBoundary(
            List<RuntimeRegionDescriptor> regions,
            WorldAxisAlignedBounds boundary
    ) {
        for (RuntimeRegionDescriptor region : regions) {
            WorldAxisAlignedBounds bounds = region.worldBounds();
            if (bounds.minX() == bounds.maxX() || bounds.minZ() == bounds.maxZ()) {
                throw new IllegalArgumentException(
                        "Acceptance region has zero horizontal area: " + region.id());
            }
            if (bounds.minX() < boundary.minX() || bounds.maxX() > boundary.maxX()
                    || bounds.minZ() < boundary.minZ() || bounds.maxZ() > boundary.maxZ()) {
                throw new IllegalArgumentException(
                        "Acceptance region lies outside the map boundary: " + region.id());
            }
        }
    }

    private static FloorCalibration calibration(WorldAxisAlignedBounds world) {
        return new FloorCalibration(List.of(
                control(world.minX(), world.minZ(), 0, 0),
                control(world.maxX(), world.minZ(), CANVAS_EDGE, 0),
                control(world.minX(), world.maxZ(), 0, CANVAS_EDGE)
        ), false, 2.0);
    }

    private static ControlPoint control(double x, double z, double u, double v) {
        return new ControlPoint(new WorldPoint2D(x, z), new CanvasPoint(u, v));
    }

    private static ProjectedRegion project(
            com.ptcrys.fpsmatch.core.minimap.model.AffineTransform2D transform,
            WorldAxisAlignedBounds bounds
    ) {
        List<CanvasPoint> corners = List.of(
                clamped(transform.transform(new WorldPoint2D(bounds.minX(), bounds.minZ()))),
                clamped(transform.transform(new WorldPoint2D(bounds.maxX(), bounds.minZ()))),
                clamped(transform.transform(new WorldPoint2D(bounds.maxX(), bounds.maxZ()))),
                clamped(transform.transform(new WorldPoint2D(bounds.minX(), bounds.maxZ())))
        );
        double u = corners.stream().mapToDouble(CanvasPoint::u).average().orElseThrow();
        double v = corners.stream().mapToDouble(CanvasPoint::v).average().orElseThrow();
        return new ProjectedRegion(corners, new CanvasPoint(u, v));
    }

    private static CanvasPoint clamped(CanvasPoint point) {
        return new CanvasPoint(
                Math.max(0.0, Math.min(CANVAS_EDGE, point.u())),
                Math.max(0.0, Math.min(CANVAS_EDGE, point.v()))
        );
    }

    private static RegionStyle style(NamespacedId id, String semantic) {
        RgbaColor accent = switch (semantic) {
            case CSGameMinimapRegionProvider.SEMANTIC_MAP_BOUNDARY ->
                    new RgbaColor(174, 184, 194, 255);
            case CSGameMinimapRegionProvider.SEMANTIC_BOMB_SITE ->
                    new RgbaColor(241, 190, 68, 255);
            case CSGameMinimapRegionProvider.SEMANTIC_SPAWN ->
                    new RgbaColor(86, 181, 235, 255);
            case CSGameMinimapRegionProvider.SEMANTIC_SHOP ->
                    new RgbaColor(105, 196, 132, 255);
            default -> throw new IllegalArgumentException(
                    "Unsupported acceptance region semantic: " + semantic);
        };
        return new RegionStyle(
                id,
                new FillStyle(accent, 0.18),
                new StrokeStyle(accent, 1.5, 0.9),
                new TextAppearance(new RgbaColor(245, 248, 250, 255), 1.0));
    }

    private static String semanticSlug(String semantic) {
        int separator = semantic.lastIndexOf('/');
        String slug = separator < 0 ? semantic : semantic.substring(separator + 1);
        if (!slug.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException(
                    "Acceptance semantic has no stable slug: " + semantic);
        }
        return slug;
    }

    private static List<CanonicalZipWriter.EntrySource> transparentTiles() {
        byte[] tile = CanonicalPngCodecV1.encode(
                TILE_EDGE, TILE_EDGE, new byte[TILE_EDGE * TILE_EDGE * 4]);
        List<CanonicalZipWriter.EntrySource> entries = new ArrayList<>();
        for (int y = 0; y < CANVAS_EDGE / TILE_EDGE; y++) {
            for (int x = 0; x < CANVAS_EDGE / TILE_EDGE; x++) {
                entries.add(new CanonicalZipWriter.Entry(
                        ContainerPath.parse("floors/" + FLOOR_ID + "/tiles/0/"
                                + x + "_" + y + ".png"), tile));
            }
        }
        return List.copyOf(entries);
    }

    private static void requireExactCurrent(
            CurrentPublication current,
            MapKey mapKey,
            NamespacedId dimension,
            NamespacedId documentId,
            PublishTransaction transaction
    ) {
        var descriptor = current.record().descriptor();
        if (!current.target().mapKey().equals(mapKey)
                || !current.target().dimension().equals(dimension)
                || !current.target().documentId().equals(documentId)
                || current.pointer().expectedBaseRevision() != 0L
                || current.pointer().revision() != transaction.publishRevision()
                || descriptor.baseRevision() != transaction.baseRevision()
                || descriptor.publishRevision() != transaction.publishRevision()
                || !descriptor.publishToken().equals(transaction.publishToken())
                || !descriptor.sourceHash().equals(transaction.descriptor().sourceHash())
                || !descriptor.runtimeHash().equals(transaction.descriptor().runtimeHash())
                || !descriptor.runtimeContainerHash().equals(
                transaction.descriptor().runtimeContainerHash())) {
            throw new IllegalStateException(
                    "Acceptance seed CURRENT does not match its reservation");
        }
    }

    record CompiledSeed(byte[] sourceBytes, byte[] runtimeBytes) {
        CompiledSeed {
            sourceBytes = sourceBytes.clone();
            runtimeBytes = runtimeBytes.clone();
        }

        @Override
        public byte[] sourceBytes() {
            return sourceBytes.clone();
        }

        @Override
        public byte[] runtimeBytes() {
            return runtimeBytes.clone();
        }
    }

    record Publication(
            CurrentPublication current,
            Sha256 sourceHash,
            Sha256 runtimeHash
    ) {
        Publication {
            Objects.requireNonNull(current, "current");
            Objects.requireNonNull(sourceHash, "sourceHash");
            Objects.requireNonNull(runtimeHash, "runtimeHash");
        }

        long revision() {
            return current.pointer().revision();
        }
    }

    record Attempt(PublishTarget target, PublishDescriptor descriptor) {
        Attempt {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(descriptor, "descriptor");
        }

        boolean matches(CurrentPublication publication) {
            return publication.target().equals(target)
                    && publication.pointer().expectedBaseRevision()
                    == descriptor.baseRevision()
                    && publication.pointer().revision() == descriptor.publishRevision()
                    && publication.pointer().descriptorChecksum()
                    .equals(descriptor.descriptorChecksum())
                    && publication.record().descriptor().equals(descriptor);
        }

        boolean matchesPointer(
                com.ptcrys.fpsmatch.core.minimap.storage.CurrentPointer pointer
        ) {
            return pointer.expectedBaseRevision() == descriptor.baseRevision()
                    && pointer.revision() == descriptor.publishRevision()
                    && pointer.descriptorChecksum().equals(descriptor.descriptorChecksum());
        }
    }

    interface RepositoryActions {
        PublishOutcome commit(PublishTransaction transaction);

        PublishOutcome recover(MapKey mapKey);
    }

    private record ProjectedRegion(List<CanvasPoint> corners, CanvasPoint anchor) {
        private ProjectedRegion {
            corners = List.copyOf(corners);
        }
    }
}
