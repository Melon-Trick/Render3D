package fr.vriege.render3d.frame;

import fr.vriege.render3d.api.DetailMask;
import java.util.Objects;

/** Runtime limits can be replaced atomically without rebuilding scenes or cached geometry. */
public record RenderSettings(
        DetailMask enabledDetails,
        int maxCommands,
        int maxIndices,
        double maxDistance,
        double highDetailDistance,
        double mediumDetailDistance,
        long geometryCacheBytes) {
    public RenderSettings {
        Objects.requireNonNull(enabledDetails, "enabledDetails");
        if (maxCommands < 1 || maxIndices < 1 || geometryCacheBytes < 1L) {
            throw new IllegalArgumentException("render budgets must be positive");
        }
        if (Double.isNaN(maxDistance) || maxDistance <= 0.0) {
            throw new IllegalArgumentException("maxDistance must be positive");
        }
        if (!Double.isFinite(highDetailDistance)
                || !Double.isFinite(mediumDetailDistance)
                || highDetailDistance < 0.0
                || mediumDetailDistance < highDetailDistance) {
            throw new IllegalArgumentException("LOD distances are invalid");
        }
    }

    public static RenderSettings defaults() {
        return new RenderSettings(DetailMask.ALL, 100_000, 4_000_000, 512.0, 32.0, 128.0, 64L * 1024L * 1024L);
    }
}
