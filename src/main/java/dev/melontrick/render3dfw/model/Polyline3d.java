package dev.melontrick.render3dfw.model;

import dev.melontrick.render3dfw.math.Bounds3d;
import dev.melontrick.render3dfw.math.Vec3d;
import java.util.List;
import java.util.Objects;

public record Polyline3d(List<Vec3d> points, boolean closed) implements Shape3d {
    public Polyline3d {
        Objects.requireNonNull(points, "points");
        points = List.copyOf(points);
        if (points.size() < 2) {
            throw new IllegalArgumentException("a polyline requires at least two points");
        }
    }

    @Override
    public Bounds3d bounds() {
        Bounds3d result = Bounds3d.around(points.getFirst());
        for (int index = 1; index < points.size(); index++) {
            result = result.union(Bounds3d.around(points.get(index)));
        }
        return result;
    }
}
