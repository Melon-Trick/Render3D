package dev.vriege.render3dfw.frame;

import dev.vriege.render3dfw.api.BlendMode;
import dev.vriege.render3dfw.api.RenderCommand;
import dev.vriege.render3dfw.api.RenderState;
import dev.vriege.render3dfw.compile.CompiledShape;
import dev.vriege.render3dfw.compile.DetailLevel;
import dev.vriege.render3dfw.compile.GeometryCache;
import dev.vriege.render3dfw.math.Vec3d;
import dev.vriege.render3dfw.model.Shape3d;
import dev.vriege.render3dfw.scene.SceneSnapshot;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class FrameCompiler {
    private final GeometryCache cache;
    private final ThreadLocal<CandidateHeap> candidateHeaps = ThreadLocal.withInitial(CandidateHeap::new);

    public FrameCompiler(GeometryCache cache) {
        this.cache = Objects.requireNonNull(cache, "cache");
    }

    public RenderFrame compile(Collection<RenderCommand> commands, CameraView camera, RenderSettings settings) {
        Objects.requireNonNull(commands, "commands");
        return compileSnapshots(List.of(SceneSnapshot.of(commands)), camera, settings);
    }

    public RenderFrame compileSnapshots(
            Collection<SceneSnapshot> snapshots, CameraView camera, RenderSettings settings) {
        Objects.requireNonNull(snapshots, "snapshots");
        Objects.requireNonNull(camera, "camera");
        Objects.requireNonNull(settings, "settings");
        long startedAt = System.nanoTime();
        RenderFrame uniformFrame = compileUniformSnapshots(snapshots, camera, settings, startedAt);
        if (uniformFrame != null) {
            return uniformFrame;
        }

        CandidateHeap candidates = candidateHeaps.get();
        candidates.reset(settings.maxCommands());
        SceneSnapshot commandOnlySnapshot = null;
        if (snapshots.size() == 1) {
            SceneSnapshot snapshot = snapshots.iterator().next();
            RenderCommand command = snapshot.uniformCommand();
            if (command != null
                    && command.state().blend() == BlendMode.OPAQUE
                    && snapshot.uniformShapeMode() != null
                    && cache.isDetailInvariant(command.shape())
                    && snapshot.size() <= settings.maxCommands()) {
                commandOnlySnapshot = snapshot;
            }
        }
        int submittedCommands = 0;
        int testedCommands = 0;
        int spatiallyPrunedCommands = 0;
        int detailsCulled = 0;
        int distanceCulled = 0;
        int frustumCulled = 0;
        int spatialNodesTested = 0;
        for (SceneSnapshot snapshot : snapshots) {
            SceneSnapshot.QueryStats query = snapshot == commandOnlySnapshot
                    ? snapshot.collectVisibleCommands(camera, settings, candidates::offerWithoutDistance)
                    : snapshot.collectVisible(camera, settings, candidates::offer);
            submittedCommands += query.commands();
            testedCommands += query.testedCommands();
            spatiallyPrunedCommands += query.spatiallyPrunedCommands();
            detailsCulled += query.detailCulledCommands();
            distanceCulled += query.distanceCulledCommands();
            frustumCulled += query.frustumCulledCommands();
            spatialNodesTested += query.testedNodes();
        }
        long culledAt = System.nanoTime();

        boolean sorted = candidates.offered() > candidates.size();
        if (sorted) {
            candidates.sortBestFirst();
        }
        int budgetCulled = candidates.offered() - candidates.size();
        CompiledShape sharedGeometry = null;
        int sharedIndexCount = 0;
        SceneSnapshot sharedSnapshot = null;
        RenderCommand sharedCommand = null;
        if (snapshots.size() == 1) {
            SceneSnapshot snapshot = snapshots.iterator().next();
            RenderCommand uniformCommand = snapshot.uniformCommand();
            if (uniformCommand != null && cache.isDetailInvariant(uniformCommand.shape())) {
                if (snapshot.uniformShapeMode() != null) {
                    sharedSnapshot = snapshot;
                    sharedCommand = uniformCommand;
                    sharedGeometry = cache.get(uniformCommand.shape(), DetailLevel.LOW);
                    sharedIndexCount = visibleIndexCount(sharedGeometry, uniformCommand);
                }
            }
        }
        long requestedIndices = sharedGeometry == null ? 0L : (long) sharedIndexCount * candidates.size();
        if (sharedGeometry != null
                && candidates.size() > 0
                && sharedIndexCount > 0
                && sharedCommand.state().blend() == BlendMode.OPAQUE
                && requestedIndices <= settings.maxIndices()) {
            long geometryPreparedAt = System.nanoTime();
            RenderBatch batch = RenderBatch.uniformCommands(
                    sharedGeometry,
                    sharedCommand,
                    sharedSnapshot.uniformStyle(),
                    sharedSnapshot.uniformShapeMode(),
                    candidates.copyCommands(),
                    camera.position());
            candidates.clearStaleReferences();
            long batchedAt = System.nanoTime();
            RenderFrameStats stats = new RenderFrameStats(
                    submittedCommands,
                    candidates.size(),
                    detailsCulled,
                    distanceCulled,
                    frustumCulled,
                    budgetCulled,
                    testedCommands,
                    spatiallyPrunedCommands,
                    spatialNodesTested,
                    (int) requestedIndices,
                    1,
                    culledAt - startedAt,
                    geometryPreparedAt - culledAt,
                    batchedAt - geometryPreparedAt,
                    batchedAt - startedAt,
                    false);
            return new RenderFrame(List.of(batch), stats);
        }
        BatchAccumulator accumulator = new BatchAccumulator(candidates.size(), camera.position());
        Shape3d previousShape = null;
        DetailLevel previousDetail = null;
        CompiledShape previousGeometry = null;
        boolean previousDetailInvariant = false;
        try (GeometryCache.LookupSession lookup = sharedGeometry == null ? cache.openLookupSession() : null) {
            for (int index = 0; index < candidates.size(); index++) {
                RenderCommand command = candidates.command(index);
                CompiledShape geometry = sharedGeometry;
                int indices = sharedIndexCount;
                if (geometry == null) {
                    DetailLevel detail = selectDetail(candidates.distanceSquared(index), settings);
                    Shape3d shape = command.shape();
                    if (shape == previousShape && (previousDetailInvariant || detail == previousDetail)) {
                        geometry = previousGeometry;
                        lookup.recordHit();
                    } else {
                        previousDetailInvariant = cache.isDetailInvariant(shape);
                        geometry = lookup.get(shape, detail);
                        previousShape = shape;
                        previousDetail = detail;
                        previousGeometry = geometry;
                    }
                    indices = visibleIndexCount(geometry, command);
                    candidates.compiled(index, geometry, indices);
                    requestedIndices += indices;
                }
                if (indices == 0 || indices > settings.maxIndices() - accumulator.renderedIndices()) {
                    budgetCulled++;
                    continue;
                }
                accumulator.add(command, geometry, indices, candidates.distanceSquared(index));
            }
        }
        long geometryPreparedAt = System.nanoTime();
        if (!sorted && requestedIndices > settings.maxIndices()) {
            candidates.sortBestFirst();
            accumulator = new BatchAccumulator(candidates.size(), camera.position());
            budgetCulled = candidates.offered() - candidates.size();
            for (int index = 0; index < candidates.size(); index++) {
                int indices = sharedGeometry == null ? candidates.indexCount(index) : sharedIndexCount;
                if (indices == 0 || indices > settings.maxIndices() - accumulator.renderedIndices()) {
                    budgetCulled++;
                    continue;
                }
                accumulator.add(
                        candidates.command(index),
                        sharedGeometry == null ? candidates.geometry(index) : sharedGeometry,
                        indices,
                        candidates.distanceSquared(index));
            }
        }
        candidates.clearStaleReferences();
        List<RenderBatch> batches = accumulator.build();
        long batchedAt = System.nanoTime();

        RenderFrameStats stats = new RenderFrameStats(
                submittedCommands,
                accumulator.renderedCommands(),
                detailsCulled,
                distanceCulled,
                frustumCulled,
                budgetCulled,
                testedCommands,
                spatiallyPrunedCommands,
                spatialNodesTested,
                accumulator.renderedIndices(),
                batches.size(),
                culledAt - startedAt,
                geometryPreparedAt - culledAt,
                batchedAt - geometryPreparedAt,
                batchedAt - startedAt,
                false);
        return new RenderFrame(batches, stats);
    }

    private static int visibleIndexCount(CompiledShape geometry, RenderCommand command) {
        int result = 0;
        if (command.style().shapeMode().lines()) {
            result += geometry.lineIndexCount();
        }
        if (command.style().shapeMode().fill()) {
            result += geometry.triangleIndexCount();
        }
        return result;
    }

    private RenderFrame compileUniformSnapshots(
            Collection<SceneSnapshot> snapshots, CameraView camera, RenderSettings settings, long startedAt) {
        int submittedCommands = 0;
        for (SceneSnapshot snapshot : snapshots) {
            RenderCommand command = snapshot.uniformCommand();
            if (command == null
                    || command.state().blend() != BlendMode.OPAQUE
                    || snapshot.uniformShapeMode() == null
                    || !cache.isDetailInvariant(command.shape())
                    || !snapshot.isFullyVisible(camera, settings)
                    || snapshot.size() > settings.maxCommands() - submittedCommands) {
                return null;
            }
            submittedCommands += snapshot.size();
        }
        long culledAt = System.nanoTime();

        int renderedIndices = 0;
        List<RenderBatch> batches = new ArrayList<>(snapshots.size());
        for (SceneSnapshot snapshot : snapshots) {
            RenderCommand command = snapshot.uniformCommand();
            CompiledShape geometry = cache.get(command.shape(), DetailLevel.LOW);
            int instanceIndices = visibleIndexCount(geometry, command);
            long snapshotIndices = (long) instanceIndices * snapshot.size();
            if (instanceIndices == 0 || snapshotIndices > settings.maxIndices() - renderedIndices) {
                return null;
            }
            renderedIndices += (int) snapshotIndices;
            batches.add(RenderBatch.uniformSnapshot(
                    geometry,
                    command,
                    snapshot.uniformStyle(),
                    snapshot.uniformShapeMode(),
                    snapshot.commands(),
                    camera.position()));
        }
        long geometryPreparedAt = System.nanoTime();
        batches.sort(Comparator.comparing(batch -> batch.state().program()));
        List<RenderBatch> immutableBatches = batches.size() == 1 ? List.of(batches.getFirst()) : List.copyOf(batches);
        long batchedAt = System.nanoTime();
        RenderFrameStats stats = new RenderFrameStats(
                submittedCommands,
                submittedCommands,
                0,
                0,
                0,
                0,
                0,
                0,
                snapshots.size(),
                renderedIndices,
                batches.size(),
                culledAt - startedAt,
                geometryPreparedAt - culledAt,
                batchedAt - geometryPreparedAt,
                batchedAt - startedAt,
                false);
        return new RenderFrame(immutableBatches, stats);
    }

    private static RenderBatch buildBatch(RenderState state, RenderBatch.Builder builder) {
        if (state.blend() == BlendMode.ALPHA) {
            builder.sortBackToFront();
        }
        return builder.build(state);
    }

    private static DetailLevel selectDetail(double distanceSquared, RenderSettings settings) {
        if (distanceSquared <= settings.highDetailDistance() * settings.highDetailDistance()) {
            return DetailLevel.HIGH;
        }
        if (distanceSquared <= settings.mediumDetailDistance() * settings.mediumDetailDistance()) {
            return DetailLevel.MEDIUM;
        }
        return DetailLevel.LOW;
    }

    private static final class BatchAccumulator {
        private final int initialCapacity;
        private final Vec3d distanceOrigin;
        private Map<RenderState, RenderBatch.Builder> buckets;
        private RenderState singleState;
        private RenderBatch.Builder singleBuilder;
        private int renderedCommands;
        private int renderedIndices;

        private BatchAccumulator(int initialCapacity, Vec3d distanceOrigin) {
            this.initialCapacity = initialCapacity;
            this.distanceOrigin = distanceOrigin;
        }

        private void add(RenderCommand command, CompiledShape geometry, int indices, double distanceSquared) {
            if (buckets == null) {
                if (singleState == null) {
                    singleState = command.state();
                    singleBuilder = new RenderBatch.Builder(
                            initialCapacity, distanceOrigin, command.state().blend() == BlendMode.ALPHA);
                } else if (!singleState.equals(command.state())) {
                    buckets = new LinkedHashMap<>();
                    buckets.put(singleState, singleBuilder);
                }
            }
            RenderBatch.Builder builder = buckets == null
                    ? singleBuilder
                    : buckets.computeIfAbsent(
                            command.state(),
                            state -> new RenderBatch.Builder(16, distanceOrigin, state.blend() == BlendMode.ALPHA));
            builder.add(command, geometry, distanceSquared);
            renderedCommands++;
            renderedIndices += indices;
        }

        private int renderedCommands() {
            return renderedCommands;
        }

        private int renderedIndices() {
            return renderedIndices;
        }

        private List<RenderBatch> build() {
            List<RenderBatch> result;
            if (buckets == null) {
                result = new ArrayList<>(singleBuilder == null ? 0 : 1);
                if (singleBuilder != null) {
                    result.add(buildBatch(singleState, singleBuilder));
                }
            } else {
                result = new ArrayList<>(buckets.size());
                for (Map.Entry<RenderState, RenderBatch.Builder> entry : buckets.entrySet()) {
                    result.add(buildBatch(entry.getKey(), entry.getValue()));
                }
            }
            result.sort(Comparator.comparing(batch -> batch.state().program()));
            return result;
        }
    }

    private static final class CandidateHeap {
        private RenderCommand[] commands = new RenderCommand[0];
        private double[] distancesSquared = new double[0];
        private long[] sequences = new long[0];
        private CompiledShape[] geometries = new CompiledShape[0];
        private int[] indexCounts = new int[0];
        private int limit;
        private int size;
        private int offered;
        private long nextSequence;
        private boolean heapified;
        private int previousSize;

        private void reset(int maximumCommands) {
            previousSize = size;
            limit = maximumCommands;
            size = 0;
            offered = 0;
            nextSequence = 0L;
            heapified = false;
            ensureCapacity(maximumCommands);
        }

        private void offer(RenderCommand command, double distanceSquared) {
            offered++;
            long sequence = nextSequence++;
            if (size < limit) {
                set(size, command, distanceSquared, sequence);
                size++;
                return;
            }
            heapify();
            if (isBetter(command, distanceSquared, sequence, 0)) {
                set(0, command, distanceSquared, sequence);
                siftDown(0, size);
            }
        }

        private void offerWithoutDistance(RenderCommand command) {
            if (size >= limit) {
                offer(command, 0.0);
                return;
            }
            offered++;
            commands[size] = command;
            size++;
            nextSequence++;
        }

        private void sortBestFirst() {
            heapify();
            for (int end = size - 1; end > 0; end--) {
                swap(0, end);
                siftDown(0, end);
            }
            heapified = false;
        }

        private void compiled(int index, CompiledShape geometry, int indexCount) {
            geometries[index] = geometry;
            indexCounts[index] = indexCount;
        }

        private CompiledShape geometry(int index) {
            return geometries[index];
        }

        private int indexCount(int index) {
            return indexCounts[index];
        }

        private RenderCommand command(int index) {
            return commands[index];
        }

        private double distanceSquared(int index) {
            return distancesSquared[index];
        }

        private int size() {
            return size;
        }

        private int offered() {
            return offered;
        }

        private RenderCommand[] copyCommands() {
            return Arrays.copyOf(commands, size);
        }

        private void clearStaleReferences() {
            if (size < previousSize) {
                Arrays.fill(commands, size, previousSize, null);
                Arrays.fill(geometries, size, previousSize, null);
            }
        }

        private void ensureCapacity(int required) {
            if (commands.length >= required) {
                return;
            }
            commands = Arrays.copyOf(commands, required);
            distancesSquared = Arrays.copyOf(distancesSquared, required);
            sequences = Arrays.copyOf(sequences, required);
            geometries = Arrays.copyOf(geometries, required);
            indexCounts = Arrays.copyOf(indexCounts, required);
        }

        private void heapify() {
            if (heapified) {
                return;
            }
            for (int index = size / 2 - 1; index >= 0; index--) {
                siftDown(index, size);
            }
            heapified = true;
        }

        private void siftDown(int index, int heapSize) {
            int parent = index;
            while (true) {
                int left = parent * 2 + 1;
                if (left >= heapSize) {
                    return;
                }
                int right = left + 1;
                int worst = right < heapSize && isWorse(right, left) ? right : left;
                if (!isWorse(worst, parent)) {
                    return;
                }
                swap(parent, worst);
                parent = worst;
            }
        }

        private boolean isWorse(int first, int second) {
            int priority = Integer.compare(commands[first].priority(), commands[second].priority());
            if (priority != 0) {
                return priority < 0;
            }
            int distance = Double.compare(distancesSquared[first], distancesSquared[second]);
            if (distance != 0) {
                return distance > 0;
            }
            return sequences[first] > sequences[second];
        }

        private boolean isBetter(RenderCommand command, double distanceSquared, long sequence, int other) {
            int priority = Integer.compare(command.priority(), commands[other].priority());
            if (priority != 0) {
                return priority > 0;
            }
            int distance = Double.compare(distanceSquared, distancesSquared[other]);
            if (distance != 0) {
                return distance < 0;
            }
            return sequence < sequences[other];
        }

        private void set(int index, RenderCommand command, double distanceSquared, long sequence) {
            commands[index] = command;
            distancesSquared[index] = distanceSquared;
            sequences[index] = sequence;
            geometries[index] = null;
            indexCounts[index] = 0;
        }

        private void swap(int first, int second) {
            RenderCommand command = commands[first];
            commands[first] = commands[second];
            commands[second] = command;
            double distance = distancesSquared[first];
            distancesSquared[first] = distancesSquared[second];
            distancesSquared[second] = distance;
            long sequence = sequences[first];
            sequences[first] = sequences[second];
            sequences[second] = sequence;
            CompiledShape geometry = geometries[first];
            geometries[first] = geometries[second];
            geometries[second] = geometry;
            int indexCount = indexCounts[first];
            indexCounts[first] = indexCounts[second];
            indexCounts[second] = indexCount;
        }
    }
}
