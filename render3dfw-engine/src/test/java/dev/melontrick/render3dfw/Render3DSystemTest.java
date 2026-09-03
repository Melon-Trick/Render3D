package dev.melontrick.render3dfw;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.melontrick.render3dfw.api.ColorRgba;
import dev.melontrick.render3dfw.api.RenderCommand;
import dev.melontrick.render3dfw.api.RenderStyle;
import dev.melontrick.render3dfw.frame.CameraView;
import dev.melontrick.render3dfw.frame.RenderSettings;
import dev.melontrick.render3dfw.math.Vec3d;
import dev.melontrick.render3dfw.model.Line3d;
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

    @Test
    void reusesAFrameUntilItsViewOrSceneChanges() {
        Render3DSystem system = Render3DSystem.create();
        RenderCommand command =
                RenderCommand.of(new Line3d(Vec3d.ZERO, new Vec3d(1.0, 0.0, 0.0)), RenderStyle.lines(ColorRgba.WHITE));
        system.scene("debug").replace(List.of(command));
        CameraView firstView = CameraView.reusable(Vec3d.ZERO, (a, b, c, d, e, f) -> true, 7L);

        assertFalse(system.compile(firstView).stats().reusedFrame());
        assertTrue(system.compile(firstView).stats().reusedFrame());
        assertEquals(new Render3DSystem.FrameReuseStats(1L, 1L), system.frameReuseStats());

        CameraView movedView = CameraView.reusable(new Vec3d(1.0, 0.0, 0.0), (a, b, c, d, e, f) -> true, 8L);
        var movedFrame = system.compile(movedView);
        assertFalse(movedFrame.stats().reusedFrame());
        assertTrue(movedFrame.stats().compilationNanos() > 0L);

        system.scene("debug").replace(List.of(command.at(new Vec3d(0.0, 1.0, 0.0))));
        assertFalse(system.compile(movedView).stats().reusedFrame());
        assertEquals(new Render3DSystem.FrameReuseStats(1L, 3L), system.frameReuseStats());
    }

    @Test
    void quantizedViewKeysRemainStableInsideACell() {
        long first = CameraView.quantizedKey(new Vec3d(10.01, 20.01, 30.01), 40.01, 10.01, 0.125, 0.5);
        long second = CameraView.quantizedKey(new Vec3d(10.02, 20.02, 30.02), 40.02, 10.02, 0.125, 0.5);
        long moved = CameraView.quantizedKey(new Vec3d(10.2, 20.01, 30.01), 40.01, 10.01, 0.125, 0.5);

        assertEquals(first, second);
        assertNotEquals(first, moved);
    }
}
