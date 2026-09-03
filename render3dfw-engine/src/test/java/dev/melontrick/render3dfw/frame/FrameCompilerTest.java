package dev.melontrick.render3dfw.frame;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.melontrick.render3dfw.api.ColorRgba;
import dev.melontrick.render3dfw.api.DepthMode;
import dev.melontrick.render3dfw.api.DetailMask;
import dev.melontrick.render3dfw.api.RenderCommand;
import dev.melontrick.render3dfw.api.RenderState;
import dev.melontrick.render3dfw.api.RenderStyle;
import dev.melontrick.render3dfw.compile.GeometryCache;
import dev.melontrick.render3dfw.compile.ShapeRegistry;
import dev.melontrick.render3dfw.math.Vec3d;
import dev.melontrick.render3dfw.model.Box3d;
import java.util.List;
import org.junit.jupiter.api.Test;

final class FrameCompilerTest {
    @Test
    void cullsDetailsAndDistanceThenBucketsByGpuState() {
        FrameCompiler compiler = new FrameCompiler(new GeometryCache(ShapeRegistry.createDefault(), 1_024L * 1_024L));
        RenderStyle style = RenderStyle.lines(ColorRgba.WHITE);
        Box3d unit = new Box3d(Vec3d.ZERO, new Vec3d(1.0, 1.0, 1.0));
        RenderCommand visible = RenderCommand.of(unit, style).withDetails(DetailMask.of(1));
        RenderCommand overlay = RenderCommand.of(unit, style)
                .at(new Vec3d(2.0, 0.0, 0.0))
                .withState(RenderState.DEBUG.withDepth(DepthMode.ALWAYS_VISIBLE))
                .withDetails(DetailMask.of(1));
        RenderCommand hiddenDetail = RenderCommand.of(unit, style).withDetails(DetailMask.of(2));
        RenderCommand tooFar =
                RenderCommand.of(unit, style).at(new Vec3d(100.0, 0.0, 0.0)).within(10.0);
        RenderSettings settings = new RenderSettings(DetailMask.of(1), 10, 10_000, 200.0, 10.0, 50.0, 1_024L * 1_024L);

        RenderFrame frame =
                compiler.compile(List.of(visible, overlay, hiddenDetail, tooFar), CameraView.at(Vec3d.ZERO), settings);

        assertEquals(2, frame.batches().size());
        assertEquals(2, frame.stats().renderedCommands());
        assertEquals(1, frame.stats().detailCulledCommands());
        assertEquals(1, frame.stats().distanceCulledCommands());
    }

    @Test
    void commandBudgetKeepsHigherPriorityCommands() {
        FrameCompiler compiler = new FrameCompiler(new GeometryCache(ShapeRegistry.createDefault(), 1_024L * 1_024L));
        RenderStyle style = RenderStyle.lines(ColorRgba.WHITE);
        Box3d unit = new Box3d(Vec3d.ZERO, new Vec3d(1.0, 1.0, 1.0));
        RenderCommand low = RenderCommand.of(unit, style).prioritized(0);
        RenderCommand high =
                RenderCommand.of(unit, style).at(new Vec3d(5.0, 0.0, 0.0)).prioritized(100);
        RenderSettings settings = new RenderSettings(DetailMask.ALL, 1, 10_000, 200.0, 10.0, 50.0, 1_024L * 1_024L);

        RenderFrame frame = compiler.compile(List.of(low, high), CameraView.at(Vec3d.ZERO), settings);

        assertEquals(1, frame.stats().renderedCommands());
        assertEquals(1, frame.stats().budgetCulledCommands());
        assertEquals(
                new Vec3d(5.0, 0.0, 0.0),
                frame.batches().getFirst().instances().getFirst().translation());
    }

    @Test
    void keepsVariableStylesInAZeroCopyUniformShapeBatch() {
        FrameCompiler compiler = new FrameCompiler(new GeometryCache(ShapeRegistry.createDefault(), 1_024L * 1_024L));
        Box3d unit = new Box3d(Vec3d.ZERO, new Vec3d(1.0, 1.0, 1.0));
        RenderStyle white = RenderStyle.lines(ColorRgba.WHITE);
        RenderStyle orange = RenderStyle.lines(ColorRgba.of(255, 96, 32, 255));
        RenderCommand first = RenderCommand.of(unit, white).withState(RenderState.OPAQUE_DEBUG);
        RenderCommand second = RenderCommand.of(unit, orange)
                .withState(RenderState.OPAQUE_DEBUG)
                .at(new Vec3d(2.0, 0.0, 0.0));

        RenderFrame frame =
                compiler.compile(List.of(first, second), CameraView.at(Vec3d.ZERO), RenderSettings.defaults());
        RenderBatch batch = frame.batches().getFirst();

        assertEquals(0, frame.stats().spatiallyTestedCommands());
        assertTrue(batch.hasUniformGeometry());
        assertTrue(batch.hasUniformShapeMode());
        assertFalse(batch.hasUniformStyle());
        assertEquals(white, batch.style(0));
        assertEquals(orange, batch.style(1));
    }
}
