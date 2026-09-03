package fr.vriege.render3d.compile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import fr.vriege.render3d.math.Bounds3d;
import fr.vriege.render3d.math.Vec3d;
import fr.vriege.render3d.model.Shape3d;
import org.junit.jupiter.api.Test;

final class ShapeRegistryTest {
    @Test
    void customShapesRegisterOnceAndAreCachedStructurally() {
        ShapeRegistry registry = ShapeRegistry.createDefault();
        registry.register(Cross.class, (shape, detail, output) -> {
            int left = output.vertex(shape.center().add(new Vec3d(-1.0, 0.0, 0.0)));
            int right = output.vertex(shape.center().add(new Vec3d(1.0, 0.0, 0.0)));
            int bottom = output.vertex(shape.center().add(new Vec3d(0.0, -1.0, 0.0)));
            int top = output.vertex(shape.center().add(new Vec3d(0.0, 1.0, 0.0)));
            output.line(left, right);
            output.line(bottom, top);
        });
        GeometryCache cache = new GeometryCache(registry, 1_024L * 1_024L);

        CompiledShape first = cache.get(new Cross(Vec3d.ZERO), DetailLevel.HIGH);
        CompiledShape second = cache.get(new Cross(Vec3d.ZERO), DetailLevel.HIGH);

        assertSame(first, second);
        assertEquals(4, first.vertexCount());
        assertEquals(4, first.lineIndexCount());
        assertEquals(1, cache.stats().hits());
        assertEquals(1, cache.stats().misses());
    }

    private record Cross(Vec3d center) implements Shape3d {
        @Override
        public Bounds3d bounds() {
            return new Bounds3d(center.add(new Vec3d(-1.0, -1.0, 0.0)), center.add(new Vec3d(1.0, 1.0, 0.0)));
        }
    }
}
