package dev.melontrick.render3dfw.frame;

public record RenderFrameStats(
        int submittedCommands,
        int renderedCommands,
        int detailCulledCommands,
        int distanceCulledCommands,
        int frustumCulledCommands,
        int budgetCulledCommands,
        int spatiallyTestedCommands,
        int spatiallyPrunedCommands,
        int spatialNodesTested,
        int renderedIndices,
        int batches,
        long cullingNanos,
        long geometryNanos,
        long batchingNanos,
        long compilationNanos,
        boolean reusedFrame) {
    public RenderFrameStats asReused() {
        return new RenderFrameStats(
                submittedCommands,
                renderedCommands,
                detailCulledCommands,
                distanceCulledCommands,
                frustumCulledCommands,
                budgetCulledCommands,
                spatiallyTestedCommands,
                spatiallyPrunedCommands,
                spatialNodesTested,
                renderedIndices,
                batches,
                0L,
                0L,
                0L,
                0L,
                true);
    }
}
