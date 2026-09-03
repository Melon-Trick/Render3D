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
        long compilationNanos) {}
