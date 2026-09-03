package fr.vriege.render3d.api;

import java.util.Objects;

/** Immutable GPU state. Equal states are automatically rendered in the same bucket. */
public record RenderState(ProgramId program, DepthMode depth, BlendMode blend, CullMode cull) {
    public static final RenderState DEBUG =
            new RenderState(ProgramId.FLAT_COLOR, DepthMode.TESTED_READ_ONLY, BlendMode.ALPHA, CullMode.NONE);

    public RenderState {
        Objects.requireNonNull(program, "program");
        Objects.requireNonNull(depth, "depth");
        Objects.requireNonNull(blend, "blend");
        Objects.requireNonNull(cull, "cull");
    }

    public RenderState withProgram(ProgramId newProgram) {
        return new RenderState(newProgram, depth, blend, cull);
    }

    public RenderState withDepth(DepthMode newDepth) {
        return new RenderState(program, newDepth, blend, cull);
    }
}
