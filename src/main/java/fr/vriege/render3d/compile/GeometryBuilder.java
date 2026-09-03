package fr.vriege.render3d.compile;

import fr.vriege.render3d.math.Bounds3d;
import fr.vriege.render3d.math.Vec3d;
import java.util.Arrays;

/** Mutable scratch builder used only while a model is compiled into the cache. */
public final class GeometryBuilder {
    private double[] positions = new double[48];
    private int positionSize;
    private int[] lines = new int[32];
    private int lineSize;
    private int[] triangles = new int[48];
    private int triangleSize;

    public int vertex(Vec3d point) {
        ensurePositionCapacity(3);
        positions[positionSize++] = point.x();
        positions[positionSize++] = point.y();
        positions[positionSize++] = point.z();
        return positionSize / 3 - 1;
    }

    public void line(int first, int second) {
        checkVertex(first);
        checkVertex(second);
        ensureLineCapacity(2);
        lines[lineSize++] = first;
        lines[lineSize++] = second;
    }

    public void triangle(int first, int second, int third) {
        checkVertex(first);
        checkVertex(second);
        checkVertex(third);
        ensureTriangleCapacity(3);
        triangles[triangleSize++] = first;
        triangles[triangleSize++] = second;
        triangles[triangleSize++] = third;
    }

    public CompiledShape build(Bounds3d bounds) {
        return new CompiledShape(
                Arrays.copyOf(positions, positionSize),
                Arrays.copyOf(lines, lineSize),
                Arrays.copyOf(triangles, triangleSize),
                bounds);
    }

    private void checkVertex(int index) {
        if (index < 0 || index >= positionSize / 3) {
            throw new IndexOutOfBoundsException("vertex " + index + " has not been emitted");
        }
    }

    private void ensurePositionCapacity(int additional) {
        if (positionSize + additional > positions.length) {
            positions = Arrays.copyOf(positions, Math.max(positions.length * 2, positionSize + additional));
        }
    }

    private void ensureLineCapacity(int additional) {
        if (lineSize + additional > lines.length) {
            lines = Arrays.copyOf(lines, Math.max(lines.length * 2, lineSize + additional));
        }
    }

    private void ensureTriangleCapacity(int additional) {
        if (triangleSize + additional > triangles.length) {
            triangles = Arrays.copyOf(triangles, Math.max(triangles.length * 2, triangleSize + additional));
        }
    }
}
