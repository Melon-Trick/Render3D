package dev.vriege.render3dfw.model;

import dev.vriege.render3dfw.math.Bounds3d;
import dev.vriege.render3dfw.math.Vec3d;
import java.util.Objects;

public record Box3d(Vec3d minimum, Vec3d maximum) implements Shape3d {
    public Box3d {
        Objects.requireNonNull(minimum, "minimum");
        Objects.requireNonNull(maximum, "maximum");
        new Bounds3d(minimum, maximum);
    }

    @Override
    public Bounds3d bounds() {
        return new Bounds3d(minimum, maximum);
    }
}
