package fr.vriege.render3d.frame;

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
