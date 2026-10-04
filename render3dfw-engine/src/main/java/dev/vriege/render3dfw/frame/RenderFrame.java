package dev.vriege.render3dfw.frame;

import java.util.List;
import java.util.Objects;

public record RenderFrame(List<RenderBatch> batches, RenderFrameStats stats) {
    public RenderFrame {
        Objects.requireNonNull(batches, "batches");
        Objects.requireNonNull(stats, "stats");
        batches = List.copyOf(batches);
    }

    public static RenderFrame empty() {
        return new RenderFrame(List.of(), new RenderFrameStats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0L, 0L, 0L, 0L, false));
    }

    public RenderFrame asReused() {
        return stats.reusedFrame() ? this : new RenderFrame(batches, stats.asReused());
    }
}
