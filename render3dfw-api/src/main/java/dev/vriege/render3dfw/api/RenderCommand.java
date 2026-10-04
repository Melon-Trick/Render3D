package dev.vriege.render3dfw.api;

import dev.vriege.render3dfw.math.Vec3d;
import dev.vriege.render3dfw.model.Shape3d;
import java.util.Objects;

public record RenderCommand(
        Shape3d shape,
        Vec3d translation,
        RenderStyle style,
        RenderState state,
        DetailMask details,
        double maxDistance,
        int priority) {
    public RenderCommand {
        Objects.requireNonNull(shape, "shape");
        Objects.requireNonNull(translation, "translation");
        Objects.requireNonNull(style, "style");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(details, "details");
        if (Double.isNaN(maxDistance) || maxDistance <= 0.0) {
            throw new IllegalArgumentException("maxDistance must be positive");
        }
    }

    public static RenderCommand of(Shape3d shape, RenderStyle style) {
        return new RenderCommand(
                shape, Vec3d.ZERO, style, RenderState.DEBUG, DetailMask.ALWAYS, Double.POSITIVE_INFINITY, 0);
    }

    public RenderCommand at(Vec3d offset) {
        return new RenderCommand(shape, offset, style, state, details, maxDistance, priority);
    }

    public RenderCommand withState(RenderState newState) {
        return new RenderCommand(shape, translation, style, newState, details, maxDistance, priority);
    }

    public RenderCommand withDetails(DetailMask newDetails) {
        return new RenderCommand(shape, translation, style, state, newDetails, maxDistance, priority);
    }

    public RenderCommand within(double distance) {
        return new RenderCommand(shape, translation, style, state, details, distance, priority);
    }

    public RenderCommand prioritized(int newPriority) {
        return new RenderCommand(shape, translation, style, state, details, maxDistance, newPriority);
    }
}
