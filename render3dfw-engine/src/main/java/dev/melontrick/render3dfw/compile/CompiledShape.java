package dev.melontrick.render3dfw.compile;

import dev.melontrick.render3dfw.math.Bounds3d;
import java.util.Objects;

public final class CompiledShape {
    private final double[] positions;
    private final int[] lineIndices;
    private final int[] triangleIndices;
    private final Bounds3d bounds;

    CompiledShape(double[] positions, int[] lineIndices, int[] triangleIndices, Bounds3d bounds) {
        this.positions = positions.clone();
        this.lineIndices = lineIndices.clone();
        this.triangleIndices = triangleIndices.clone();
        this.bounds = Objects.requireNonNull(bounds, "bounds");
    }

    public int vertexCount() {
        return positions.length / 3;
    }

    public int lineIndexCount() {
        return lineIndices.length;
    }

    public int triangleIndexCount() {
        return triangleIndices.length;
    }

    public double x(int vertex) {
        return positions[checkedVertex(vertex) * 3];
    }

    public double y(int vertex) {
        return positions[checkedVertex(vertex) * 3 + 1];
    }

    public double z(int vertex) {
        return positions[checkedVertex(vertex) * 3 + 2];
    }

    public int lineIndex(int index) {
        return lineIndices[index];
    }

    public int triangleIndex(int index) {
        return triangleIndices[index];
    }

    public Bounds3d bounds() {
        return bounds;
    }

    public long estimatedBytes() {
        return (long) positions.length * Double.BYTES
                + (long) (lineIndices.length + triangleIndices.length) * Integer.BYTES;
    }

    private int checkedVertex(int vertex) {
        if (vertex < 0 || vertex >= vertexCount()) {
            throw new IndexOutOfBoundsException(vertex);
        }
        return vertex;
    }
}
