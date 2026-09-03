package fr.vriege.render3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import fr.vriege.render3d.api.ColorRgba;
import fr.vriege.render3d.api.RenderCommand;
import fr.vriege.render3d.api.RenderStyle;
import fr.vriege.render3d.frame.CameraView;
import fr.vriege.render3d.frame.RenderSettings;
import fr.vriege.render3d.math.Vec3d;
import fr.vriege.render3d.model.Line3d;
import java.util.List;
import org.junit.jupiter.api.Test;

final class Render3DSystemTest {
    @Test
    void scenesPublishImmutableSnapshotsAndShareTheGeometryCache() {
        Render3DSystem system = Render3DSystem.create();
        RenderCommand command =
                RenderCommand.of(new Line3d(Vec3d.ZERO, new Vec3d(1.0, 0.0, 0.0)), RenderStyle.lines(ColorRgba.WHITE));
        system.scene("one").replace(List.of(command));
        system.scene("two").replace(List.of(command.at(new Vec3d(0.0, 1.0, 0.0))));

        assertEquals(2, system.compile(CameraView.at(Vec3d.ZERO)).stats().renderedCommands());
        assertEquals(1, system.cacheStats().misses());
        assertEquals(1, system.cacheStats().hits());

        long previousCapacity = system.settings().geometryCacheBytes();
        RenderSettings current = system.settings();
        system.settings(new RenderSettings(
                current.enabledDetails(),
                current.maxCommands(),
                current.maxIndices(),
                current.maxDistance(),
                current.highDetailDistance(),
                current.mediumDetailDistance(),
                previousCapacity / 2));
        assertNotEquals(previousCapacity, system.settings().geometryCacheBytes());
        assertEquals(0, system.cacheStats().entries());
    }
}
