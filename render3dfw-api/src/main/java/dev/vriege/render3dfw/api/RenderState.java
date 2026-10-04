package dev.vriege.render3dfw.api;

import java.util.Objects;

public record RenderState(ProgramId program, DepthMode depth, BlendMode blend, CullMode cull) {
    public static final RenderState DEBUG =
            new RenderState(ProgramId.FLAT_COLOR, DepthMode.TESTED_READ_ONLY, BlendMode.ALPHA, CullMode.NONE);
    public static final RenderState OPAQUE_DEBUG =
            new RenderState(ProgramId.FLAT_COLOR, DepthMode.TESTED_READ_ONLY, BlendMode.OPAQUE, CullMode.NONE);

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
