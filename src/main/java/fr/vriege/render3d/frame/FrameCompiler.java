package fr.vriege.render3d.frame;

import fr.vriege.render3d.api.BlendMode;
import fr.vriege.render3d.api.RenderCommand;
import fr.vriege.render3d.api.RenderState;
import fr.vriege.render3d.compile.CompiledShape;
import fr.vriege.render3d.compile.DetailLevel;
import fr.vriege.render3d.compile.GeometryCache;
import fr.vriege.render3d.math.Bounds3d;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Converts model commands into culled, budgeted and state-sorted backend batches. */
public final class FrameCompiler {
    private final GeometryCache cache;

    public FrameCompiler(GeometryCache cache) {
        this.cache = Objects.requireNonNull(cache, "cache");
    }

    public RenderFrame compile(Collection<RenderCommand> commands, CameraView camera, RenderSettings settings) {
        Objects.requireNonNull(commands, "commands");
        Objects.requireNonNull(camera, "camera");
        Objects.requireNonNull(settings, "settings");
        long startedAt = System.nanoTime();

        List<Candidate> candidates = new ArrayList<>(commands.size());
        int detailsCulled = 0;
        int distanceCulled = 0;
        int frustumCulled = 0;
        for (RenderCommand command : commands) {
            if (!command.details().visibleWithin(settings.enabledDetails())) {
                detailsCulled++;
                continue;
            }
            Bounds3d worldBounds = command.shape().bounds().translated(command.translation());
            double distanceSquared = worldBounds.center().distanceSquared(camera.position());
            double maximumDistance = Math.min(command.maxDistance(), settings.maxDistance());
            if (distanceSquared > maximumDistance * maximumDistance) {
                distanceCulled++;
                continue;
            }
            if (!camera.visibility().isVisible(worldBounds)) {
                frustumCulled++;
                continue;
            }
            candidates.add(new Candidate(command, distanceSquared));
        }

        candidates.sort(Comparator.comparingInt(
                        (Candidate candidate) -> candidate.command().priority())
                .reversed()
                .thenComparingDouble(Candidate::distanceSquared));

        Map<RenderState, List<DrawInstance>> buckets = new LinkedHashMap<>();
        int renderedCommands = 0;
        int renderedIndices = 0;
        int budgetCulled = 0;
        for (Candidate candidate : candidates) {
            if (renderedCommands >= settings.maxCommands()) {
                budgetCulled++;
                continue;
            }
            DetailLevel detail = selectDetail(candidate.distanceSquared(), settings);
            CompiledShape geometry = cache.get(candidate.command().shape(), detail);
            int indices = visibleIndexCount(geometry, candidate.command());
            if (indices == 0 || indices > settings.maxIndices() - renderedIndices) {
                budgetCulled++;
                continue;
            }
            buckets.computeIfAbsent(candidate.command().state(), ignored -> new ArrayList<>())
                    .add(new DrawInstance(
                            geometry,
                            candidate.command().translation(),
                            candidate.command().style(),
                            candidate.distanceSquared()));
            renderedCommands++;
            renderedIndices += indices;
        }

        List<RenderBatch> batches = new ArrayList<>(buckets.size());
        for (Map.Entry<RenderState, List<DrawInstance>> entry : buckets.entrySet()) {
            if (entry.getKey().blend() == BlendMode.ALPHA) {
                entry.getValue()
                        .sort(Comparator.comparingDouble(DrawInstance::distanceSquared)
                                .reversed());
            }
            batches.add(new RenderBatch(entry.getKey(), entry.getValue()));
        }
        batches.sort(Comparator.comparing(batch -> batch.state().program()));

        RenderFrameStats stats = new RenderFrameStats(
                commands.size(),
                renderedCommands,
                detailsCulled,
                distanceCulled,
                frustumCulled,
                budgetCulled,
                renderedIndices,
                batches.size(),
                System.nanoTime() - startedAt);
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

    private static DetailLevel selectDetail(double distanceSquared, RenderSettings settings) {
        if (distanceSquared <= settings.highDetailDistance() * settings.highDetailDistance()) {
            return DetailLevel.HIGH;
        }
        if (distanceSquared <= settings.mediumDetailDistance() * settings.mediumDetailDistance()) {
            return DetailLevel.MEDIUM;
        }
        return DetailLevel.LOW;
    }

    private record Candidate(RenderCommand command, double distanceSquared) {}
}
