package dev.melontrick.render3dfw.compile;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.melontrick.render3dfw.math.Vec3d;
import dev.melontrick.render3dfw.model.Sphere3d;
import org.junit.jupiter.api.Test;

final class ProceduralLodTest {
    @Test
    void sphereGeometryScalesWithSelectedDetailLevel() {
        ShapeRegistry registry = ShapeRegistry.createDefault();
        Sphere3d sphere = new Sphere3d(Vec3d.ZERO, 1.0);

        CompiledShape low = registry.compile(sphere, DetailLevel.LOW);
        CompiledShape medium = registry.compile(sphere, DetailLevel.MEDIUM);
        CompiledShape high = registry.compile(sphere, DetailLevel.HIGH);

        assertTrue(low.vertexCount() < medium.vertexCount());
        assertTrue(medium.vertexCount() < high.vertexCount());
        assertTrue(low.triangleIndexCount() < high.triangleIndexCount());
    }
}
