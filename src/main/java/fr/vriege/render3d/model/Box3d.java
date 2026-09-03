package fr.vriege.render3d.model;

import fr.vriege.render3d.math.Bounds3d;
import fr.vriege.render3d.math.Vec3d;
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
