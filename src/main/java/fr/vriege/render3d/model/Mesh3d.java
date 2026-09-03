package fr.vriege.render3d.model;

import fr.vriege.render3d.math.Bounds3d;
import fr.vriege.render3d.math.Vec3d;
import java.util.List;
import java.util.Objects;

/** An indexed triangle mesh. Indices are interpreted in groups of three. */
public record Mesh3d(List<Vec3d> vertices, List<Integer> indices, Bounds3d bounds) implements Shape3d {
    public Mesh3d {
        Objects.requireNonNull(vertices, "vertices");
        Objects.requireNonNull(indices, "indices");
        Objects.requireNonNull(bounds, "bounds");
        vertices = List.copyOf(vertices);
        indices = List.copyOf(indices);
        if (vertices.size() < 3 || indices.isEmpty() || indices.size() % 3 != 0) {
            throw new IllegalArgumentException("mesh requires vertices and triangle indices");
        }
        for (int index : indices) {
            if (index < 0 || index >= vertices.size()) {
                throw new IndexOutOfBoundsException("mesh index " + index + " is outside the vertex array");
            }
        }
    }

    public static Mesh3d of(List<Vec3d> vertices, List<Integer> indices) {
        Objects.requireNonNull(vertices, "vertices");
        if (vertices.isEmpty()) {
            throw new IllegalArgumentException("vertices must not be empty");
        }
        Bounds3d bounds = Bounds3d.around(vertices.getFirst());
        for (int index = 1; index < vertices.size(); index++) {
            bounds = bounds.union(Bounds3d.around(vertices.get(index)));
        }
        return new Mesh3d(vertices, indices, bounds);
    }
}
