package dev.melontrick.render3dfw.frame;

import dev.melontrick.render3dfw.api.RenderState;
import java.util.List;
import java.util.Objects;

/** A state-homogeneous bucket; backends can emit it without state changes between instances. */
public record RenderBatch(RenderState state, List<DrawInstance> instances) {
    public RenderBatch {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(instances, "instances");
        instances = List.copyOf(instances);
        if (instances.isEmpty()) {
            throw new IllegalArgumentException("a render batch must not be empty");
        }
    }
}
