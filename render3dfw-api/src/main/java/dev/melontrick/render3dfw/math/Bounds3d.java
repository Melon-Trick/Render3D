package dev.melontrick.render3dfw.math;

import java.util.Objects;

public record Bounds3d(Vec3d minimum, Vec3d maximum) {
    public Bounds3d {
        Objects.requireNonNull(minimum, "minimum");
        Objects.requireNonNull(maximum, "maximum");
        if (minimum.x() > maximum.x() || minimum.y() > maximum.y() || minimum.z() > maximum.z()) {
            throw new IllegalArgumentException("minimum must not exceed maximum");
        }
    }

    public static Bounds3d around(Vec3d point) {
        return new Bounds3d(point, point);
    }

    public Vec3d center() {
        return new Vec3d(
                (minimum.x() + maximum.x()) * 0.5,
                (minimum.y() + maximum.y()) * 0.5,
                (minimum.z() + maximum.z()) * 0.5);
    }

    public Bounds3d union(Bounds3d other) {
        return new Bounds3d(
                new Vec3d(
                        Math.min(minimum.x(), other.minimum.x()),
                        Math.min(minimum.y(), other.minimum.y()),
                        Math.min(minimum.z(), other.minimum.z())),
                new Vec3d(
                        Math.max(maximum.x(), other.maximum.x()),
                        Math.max(maximum.y(), other.maximum.y()),
                        Math.max(maximum.z(), other.maximum.z())));
    }

    public Bounds3d translated(Vec3d offset) {
        return new Bounds3d(minimum.add(offset), maximum.add(offset));
    }
}
