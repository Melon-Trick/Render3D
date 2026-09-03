package fr.vriege.render3d.model;

import fr.vriege.render3d.math.Bounds3d;
import fr.vriege.render3d.math.Vec3d;
import java.util.Objects;

public record Sphere3d(Vec3d center, double radius) implements Shape3d {
    public Sphere3d {
        Objects.requireNonNull(center, "center");
        if (!Double.isFinite(radius) || radius <= 0.0) {
            throw new IllegalArgumentException("radius must be finite and positive");
        }
    }

    @Override
    public Bounds3d bounds() {
        Vec3d extent = new Vec3d(radius, radius, radius);
        return new Bounds3d(center.subtract(extent), center.add(extent));
    }
}
