package dev.melontrick.render3dfw.frame;

public record RenderFrameStats(
        int submittedCommands,
        int renderedCommands,
        int detailCulledCommands,
        int distanceCulledCommands,
        int frustumCulledCommands,
        int budgetCulledCommands,
        int renderedIndices,
        int batches,
        long compilationNanos) {}
