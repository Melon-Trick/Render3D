package dev.melontrick.render3dfw.frame;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.melontrick.render3dfw.api.ColorRgba;
import dev.melontrick.render3dfw.api.RenderCommand;
import dev.melontrick.render3dfw.api.RenderState;
import dev.melontrick.render3dfw.api.RenderStyle;
import dev.melontrick.render3dfw.compile.GeometryCache;
import dev.melontrick.render3dfw.compile.ShapeRegistry;
import dev.melontrick.render3dfw.math.Vec3d;
import dev.melontrick.render3dfw.model.Line3d;
import dev.melontrick.render3dfw.scene.SceneSnapshot;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class MassiveFrameCompilationTest {
    private static final int COMMAND_COUNT = 500_000;

    @Test
    void compilesAHalfMillionVisibleRepeatedPrimitivesIntoOneBatch() {
        RenderCommand model = RenderCommand.of(
                        new Line3d(Vec3d.ZERO, new Vec3d(1.0, 0.0, 0.0)), RenderStyle.lines(ColorRgba.WHITE))
                .withState(RenderState.OPAQUE_DEBUG);
        List<RenderCommand> commands = new ArrayList<>(COMMAND_COUNT);
        for (int index = 0; index < COMMAND_COUNT; index++) {
            double x = (index % 100) * 2.0;
            double y = ((index / 100) % 100) * 2.0;
            double z = (index / 10_000) * 2.0;
            commands.add(model.at(new Vec3d(x, y, z)));
        }
        SceneSnapshot snapshot = SceneSnapshot.of(commands);
        GeometryCache cache = new GeometryCache(ShapeRegistry.createDefault(), 128L * 1024L * 1024L);
        FrameCompiler compiler = new FrameCompiler(cache);

        RenderFrame frame = compiler.compileSnapshots(
                List.of(snapshot), CameraView.at(new Vec3d(100.0, 100.0, 50.0)), RenderSettings.massive());

        assertEquals(COMMAND_COUNT, frame.stats().renderedCommands());
        assertEquals(COMMAND_COUNT * 2, frame.stats().renderedIndices());
        assertEquals(1, frame.batches().size());
        assertEquals(COMMAND_COUNT, frame.batches().getFirst().size());
        assertTrue(cache.stats().entries() <= 3);
        assertTrue(cache.stats().hits() >= COMMAND_COUNT - 3L);
    }
}
