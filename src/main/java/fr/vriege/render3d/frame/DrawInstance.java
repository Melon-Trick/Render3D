package fr.vriege.render3d.frame;

import fr.vriege.render3d.api.RenderStyle;
import fr.vriege.render3d.compile.CompiledShape;
import fr.vriege.render3d.math.Vec3d;
import java.util.Objects;

public record DrawInstance(CompiledShape geometry, Vec3d translation, RenderStyle style, double distanceSquared) {
    public DrawInstance {
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(translation, "translation");
        Objects.requireNonNull(style, "style");
        if (distanceSquared < 0.0 || Double.isNaN(distanceSquared)) {
            throw new IllegalArgumentException("distanceSquared must be non-negative");
        }
    }
}
