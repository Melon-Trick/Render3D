package dev.melontrick.render3dfw.compile;

import dev.melontrick.render3dfw.math.Vec3d;
import dev.melontrick.render3dfw.model.Box3d;
import dev.melontrick.render3dfw.model.Line3d;
import dev.melontrick.render3dfw.model.Mesh3d;
import dev.melontrick.render3dfw.model.Polyline3d;
import dev.melontrick.render3dfw.model.Sphere3d;
import dev.melontrick.render3dfw.model.Triangle3d;
import java.util.HashSet;
import java.util.Set;

final class BuiltinShapeCompilers {
    private BuiltinShapeCompilers() {}

    static void registerInto(ShapeRegistry registry) {
        registry.register(Line3d.class, BuiltinShapeCompilers::line);
        registry.register(Triangle3d.class, BuiltinShapeCompilers::triangle);
        registry.register(Box3d.class, BuiltinShapeCompilers::box);
        registry.register(Polyline3d.class, BuiltinShapeCompilers::polyline);
        registry.register(Mesh3d.class, BuiltinShapeCompilers::mesh);
        registry.register(Sphere3d.class, BuiltinShapeCompilers::sphere);
    }

    private static void line(Line3d shape, DetailLevel ignored, GeometryBuilder output) {
        output.line(output.vertex(shape.start()), output.vertex(shape.end()));
    }

    private static void triangle(Triangle3d shape, DetailLevel ignored, GeometryBuilder output) {
        int first = output.vertex(shape.first());
        int second = output.vertex(shape.second());
        int third = output.vertex(shape.third());
        output.line(first, second);
        output.line(second, third);
        output.line(third, first);
        output.triangle(first, second, third);
    }

    private static void box(Box3d shape, DetailLevel ignored, GeometryBuilder output) {
        Vec3d min = shape.minimum();
        Vec3d max = shape.maximum();
        int p000 = output.vertex(new Vec3d(min.x(), min.y(), min.z()));
        int p001 = output.vertex(new Vec3d(min.x(), min.y(), max.z()));
        int p010 = output.vertex(new Vec3d(min.x(), max.y(), min.z()));
        int p011 = output.vertex(new Vec3d(min.x(), max.y(), max.z()));
        int p100 = output.vertex(new Vec3d(max.x(), min.y(), min.z()));
        int p101 = output.vertex(new Vec3d(max.x(), min.y(), max.z()));
        int p110 = output.vertex(new Vec3d(max.x(), max.y(), min.z()));
        int p111 = output.vertex(new Vec3d(max.x(), max.y(), max.z()));

        edge(output, p000, p001);
        edge(output, p001, p101);
        edge(output, p101, p100);
        edge(output, p100, p000);
        edge(output, p010, p011);
        edge(output, p011, p111);
        edge(output, p111, p110);
        edge(output, p110, p010);
        edge(output, p000, p010);
        edge(output, p001, p011);
        edge(output, p100, p110);
        edge(output, p101, p111);

        quad(output, p000, p001, p101, p100);
        quad(output, p010, p110, p111, p011);
        quad(output, p000, p100, p110, p010);
        quad(output, p001, p011, p111, p101);
        quad(output, p000, p010, p011, p001);
        quad(output, p100, p101, p111, p110);
    }

    private static void polyline(Polyline3d shape, DetailLevel ignored, GeometryBuilder output) {
        int[] vertices = new int[shape.points().size()];
        for (int index = 0; index < vertices.length; index++) {
            vertices[index] = output.vertex(shape.points().get(index));
            if (index > 0) {
                output.line(vertices[index - 1], vertices[index]);
            }
        }
        if (shape.closed()) {
            output.line(vertices[vertices.length - 1], vertices[0]);
        }
    }

    private static void mesh(Mesh3d shape, DetailLevel ignored, GeometryBuilder output) {
        int[] vertices = new int[shape.vertices().size()];
        for (int index = 0; index < vertices.length; index++) {
            vertices[index] = output.vertex(shape.vertices().get(index));
        }
        Set<Long> edges = new HashSet<>();
        for (int index = 0; index < shape.indices().size(); index += 3) {
            int first = vertices[shape.indices().get(index)];
            int second = vertices[shape.indices().get(index + 1)];
            int third = vertices[shape.indices().get(index + 2)];
            output.triangle(first, second, third);
            uniqueEdge(output, edges, first, second);
            uniqueEdge(output, edges, second, third);
            uniqueEdge(output, edges, third, first);
        }
    }

    private static void sphere(Sphere3d shape, DetailLevel detail, GeometryBuilder output) {
        int slices =
                switch (detail) {
                    case LOW -> 8;
                    case MEDIUM -> 16;
                    case HIGH -> 32;
                };
        int stacks = slices / 2;
        int[][] vertices = new int[stacks + 1][slices];
        for (int stack = 0; stack <= stacks; stack++) {
            double latitude = Math.PI * stack / stacks - Math.PI * 0.5;
            double ringRadius = Math.cos(latitude) * shape.radius();
            double y = shape.center().y() + Math.sin(latitude) * shape.radius();
            for (int slice = 0; slice < slices; slice++) {
                double longitude = Math.PI * 2.0 * slice / slices;
                vertices[stack][slice] = output.vertex(new Vec3d(
                        shape.center().x() + Math.cos(longitude) * ringRadius,
                        y,
                        shape.center().z() + Math.sin(longitude) * ringRadius));
            }
        }
        for (int stack = 0; stack <= stacks; stack++) {
            for (int slice = 0; slice < slices; slice++) {
                int nextSlice = (slice + 1) % slices;
                output.line(vertices[stack][slice], vertices[stack][nextSlice]);
                if (stack < stacks) {
                    output.line(vertices[stack][slice], vertices[stack + 1][slice]);
                    output.triangle(vertices[stack][slice], vertices[stack + 1][slice], vertices[stack + 1][nextSlice]);
                    output.triangle(vertices[stack][slice], vertices[stack + 1][nextSlice], vertices[stack][nextSlice]);
                }
            }
        }
    }

    private static void edge(GeometryBuilder output, int first, int second) {
        output.line(first, second);
    }

    private static void quad(GeometryBuilder output, int first, int second, int third, int fourth) {
        output.triangle(first, second, third);
        output.triangle(first, third, fourth);
    }

    private static void uniqueEdge(GeometryBuilder output, Set<Long> edges, int first, int second) {
        int minimum = Math.min(first, second);
        int maximum = Math.max(first, second);
        long key = (long) minimum << 32 | maximum & 0xFFFFFFFFL;
        if (edges.add(key)) {
            output.line(first, second);
        }
    }
}
