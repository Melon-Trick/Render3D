package fr.vriege.render3d.compile;

import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.vriege.render3d.math.Vec3d;
import fr.vriege.render3d.model.Sphere3d;
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
