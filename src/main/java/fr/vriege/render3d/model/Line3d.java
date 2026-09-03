package fr.vriege.render3d.model;

import fr.vriege.render3d.math.Bounds3d;
import fr.vriege.render3d.math.Vec3d;
import java.util.Objects;

public record Line3d(Vec3d start, Vec3d end) implements Shape3d {
    public Line3d {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
    }

    @Override
    public Bounds3d bounds() {
        return new Bounds3d(
                new Vec3d(Math.min(start.x(), end.x()), Math.min(start.y(), end.y()), Math.min(start.z(), end.z())),
                new Vec3d(Math.max(start.x(), end.x()), Math.max(start.y(), end.y()), Math.max(start.z(), end.z())));
    }
}
