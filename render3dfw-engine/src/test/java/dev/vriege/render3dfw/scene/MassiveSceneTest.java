package dev.vriege.render3dfw.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vriege.render3dfw.api.ColorRgba;
import dev.vriege.render3dfw.api.DetailMask;
import dev.vriege.render3dfw.api.RenderCommand;
import dev.vriege.render3dfw.api.RenderStyle;
import dev.vriege.render3dfw.frame.CameraView;
import dev.vriege.render3dfw.frame.RenderSettings;
import dev.vriege.render3dfw.math.Vec3d;
import dev.vriege.render3dfw.model.Line3d;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class MassiveSceneTest {
    private static final int COMMAND_COUNT = 500_000;

    @Test
    void spatialIndexPrunesAHalfMillionCommandSceneBeforePerCommandTesting() {
        RenderCommand model =
                RenderCommand.of(new Line3d(Vec3d.ZERO, new Vec3d(1.0, 0.0, 0.0)), RenderStyle.lines(ColorRgba.WHITE));
        List<RenderCommand> commands = new ArrayList<>(COMMAND_COUNT);
        for (int index = 0; index < COMMAND_COUNT; index++) {
            commands.add(model.at(new Vec3d(index * 4.0, 0.0, 0.0)));
        }
        SceneSnapshot snapshot = SceneSnapshot.of(commands);
        RenderSettings settings =
                new RenderSettings(DetailMask.ALL, 10_000, 1_000_000, 64.0, 16.0, 48.0, 16L * 1024L * 1024L);
        AtomicInteger visible = new AtomicInteger();

        SceneSnapshot.QueryStats query = snapshot.collectVisible(
                CameraView.at(Vec3d.ZERO), settings, (command, distance) -> visible.incrementAndGet());

        assertEquals(COMMAND_COUNT, query.commands());
        assertTrue(visible.get() > 0);
        assertTrue(query.testedCommands() < 512);
        assertTrue(query.spatiallyPrunedCommands() > COMMAND_COUNT - 512);
    }
}
