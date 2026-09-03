package fr.vriege.render3d.model;

import fr.vriege.render3d.math.Bounds3d;
import fr.vriege.render3d.math.Vec3d;
import java.util.Objects;

public record Triangle3d(Vec3d first, Vec3d second, Vec3d third) implements Shape3d {
    public Triangle3d {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        Objects.requireNonNull(third, "third");
    }

    @Override
    public Bounds3d bounds() {
        return new Bounds3d(
                new Vec3d(
                        Math.min(first.x(), Math.min(second.x(), third.x())),
                        Math.min(first.y(), Math.min(second.y(), third.y())),
                        Math.min(first.z(), Math.min(second.z(), third.z()))),
                new Vec3d(
                        Math.max(first.x(), Math.max(second.x(), third.x())),
                        Math.max(first.y(), Math.max(second.y(), third.y())),
                        Math.max(first.z(), Math.max(second.z(), third.z()))));
    }
}
