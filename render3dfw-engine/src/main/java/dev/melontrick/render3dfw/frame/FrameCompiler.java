package dev.melontrick.render3dfw.frame;

import dev.melontrick.render3dfw.api.BlendMode;
import dev.melontrick.render3dfw.api.RenderCommand;
import dev.melontrick.render3dfw.api.RenderState;
import dev.melontrick.render3dfw.compile.CompiledShape;
import dev.melontrick.render3dfw.compile.DetailLevel;
import dev.melontrick.render3dfw.compile.GeometryCache;
import dev.melontrick.render3dfw.scene.SceneSnapshot;
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

        CandidateHeap candidates = candidateHeaps.get();
        candidates.reset(settings.maxCommands());
        int submittedCommands = 0;
        int testedCommands = 0;
        int spatiallyPrunedCommands = 0;
        int detailsCulled = 0;
        int distanceCulled = 0;
        int frustumCulled = 0;
        int spatialNodesTested = 0;
        for (SceneSnapshot snapshot : snapshots) {
            SceneSnapshot.QueryStats query = snapshot.collectVisible(camera, settings, candidates::offer);
            submittedCommands += query.commands();
            testedCommands += query.testedCommands();
            spatiallyPrunedCommands += query.spatiallyPrunedCommands();
            detailsCulled += query.detailCulledCommands();
            distanceCulled += query.distanceCulledCommands();
            frustumCulled += query.frustumCulledCommands();
            spatialNodesTested += query.testedNodes();
        }
        long culledAt = System.nanoTime();

        int budgetCulled = candidates.offered() - candidates.size();
        long requestedIndices = 0L;
        try (GeometryCache.LookupSession lookup = cache.openLookupSession()) {
            for (int index = 0; index < candidates.size(); index++) {
                RenderCommand command = candidates.command(index);
                DetailLevel detail = selectDetail(candidates.distanceSquared(index), settings);
                CompiledShape geometry = lookup.get(command.shape(), detail);
                int indices = visibleIndexCount(geometry, command);
                candidates.compiled(index, geometry, indices);
                requestedIndices += indices;
            }
        }
        if (candidates.offered() > candidates.size() || requestedIndices > settings.maxIndices()) {
            candidates.sortBestFirst();
        }
        long geometryPreparedAt = System.nanoTime();
        Map<RenderState, RenderBatch.Builder> buckets = null;
        RenderState singleState = null;
        RenderBatch.Builder singleBuilder = null;
        int renderedCommands = 0;
        int renderedIndices = 0;
        for (int index = 0; index < candidates.size(); index++) {
            RenderCommand command = candidates.command(index);
            double distanceSquared = candidates.distanceSquared(index);
            CompiledShape geometry = candidates.geometry(index);
            int indices = candidates.indexCount(index);
            if (indices == 0 || indices > settings.maxIndices() - renderedIndices) {
                budgetCulled++;
                continue;
            }
            if (buckets == null) {
                if (singleState == null) {
                    singleState = command.state();
                    singleBuilder = new RenderBatch.Builder(candidates.size());
                } else if (!singleState.equals(command.state())) {
                    buckets = new LinkedHashMap<>();
                    buckets.put(singleState, singleBuilder);
                }
            }
            RenderBatch.Builder builder = buckets == null
                    ? singleBuilder
                    : buckets.computeIfAbsent(command.state(), ignored -> new RenderBatch.Builder());
            builder.add(geometry, command.translation(), command.style(), distanceSquared);
            renderedCommands++;
            renderedIndices += indices;
        }
        candidates.clearReferences();

        List<RenderBatch> batches;
        if (buckets == null) {
            batches = new ArrayList<>(singleBuilder == null ? 0 : 1);
            if (singleBuilder != null) {
                batches.add(buildBatch(singleState, singleBuilder));
            }
        } else {
            batches = new ArrayList<>(buckets.size());
            for (Map.Entry<RenderState, RenderBatch.Builder> entry : buckets.entrySet()) {
                batches.add(buildBatch(entry.getKey(), entry.getValue()));
            }
        }
        batches.sort(Comparator.comparing(batch -> batch.state().program()));
        long batchedAt = System.nanoTime();

        RenderFrameStats stats = new RenderFrameStats(
                submittedCommands,
                renderedCommands,
                detailsCulled,
                distanceCulled,
                frustumCulled,
                budgetCulled,
                testedCommands,
                spatiallyPrunedCommands,
                spatialNodesTested,
                renderedIndices,
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

        private void reset(int maximumCommands) {
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

        private void clearReferences() {
            Arrays.fill(commands, 0, size, null);
            Arrays.fill(geometries, 0, size, null);
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
