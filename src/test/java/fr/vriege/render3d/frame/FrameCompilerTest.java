package fr.vriege.render3d.frame;

import static org.junit.jupiter.api.Assertions.assertEquals;

import fr.vriege.render3d.api.ColorRgba;
import fr.vriege.render3d.api.DepthMode;
import fr.vriege.render3d.api.DetailMask;
import fr.vriege.render3d.api.RenderCommand;
import fr.vriege.render3d.api.RenderState;
import fr.vriege.render3d.api.RenderStyle;
import fr.vriege.render3d.compile.GeometryCache;
import fr.vriege.render3d.compile.ShapeRegistry;
import fr.vriege.render3d.math.Vec3d;
import fr.vriege.render3d.model.Box3d;
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
}
