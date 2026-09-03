package dev.melontrick.render3dfw.fabric.v26_1;

import dev.melontrick.render3dfw.Render3DSystem;
import dev.melontrick.render3dfw.frame.CameraView;
import dev.melontrick.render3dfw.frame.RenderFrame;
import dev.melontrick.render3dfw.frame.VisibilityTest;
import dev.melontrick.render3dfw.math.Vec3d;
import java.util.Objects;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class FabricRenderBackend {
    private static final double POSITION_QUANTUM = 0.125;
    private static final double ROTATION_QUANTUM = 0.5;

    private final Render3DSystem system;
    private final FabricProgramRegistry programs;
    private volatile RenderFrame extractedFrame = RenderFrame.empty();
    private boolean installed;

    public FabricRenderBackend(Render3DSystem system, FabricProgramRegistry programs) {
        this.system = Objects.requireNonNull(system, "system");
        this.programs = Objects.requireNonNull(programs, "programs");
    }

    public void install() {
        if (installed) {
            throw new IllegalStateException("Fabric Render3D backend is already installed");
        }
        installed = true;
        LevelRenderEvents.END_EXTRACTION.register(context -> {
            Vec3 camera = context.camera().position();
            Frustum frustum = context.levelState().cameraRenderState.cullFrustum;
            Vec3d cameraPosition = new Vec3d(camera.x(), camera.y(), camera.z());
            long reuseKey = CameraView.quantizedKey(
                    cameraPosition,
                    context.camera().yaw(),
                    context.camera().xRot(),
                    POSITION_QUANTUM,
                    ROTATION_QUANTUM);
            CameraView view = CameraView.reusable(cameraPosition, visibility(frustum), reuseKey);
            extractedFrame = system.compile(view);
        });
        LevelRenderEvents.END_MAIN.register(context -> {
            RenderFrame frame = extractedFrame;
            if (frame.batches().isEmpty()) {
                return;
            }
            Vec3 camera = context.levelState().cameraRenderState.pos;
            Vec3d cameraPosition = new Vec3d(camera.x(), camera.y(), camera.z());
            frame.batches().forEach(batch -> programs.draw(context, cameraPosition, batch));
        });
    }

    private static VisibilityTest visibility(Frustum frustum) {
        if (frustum == null) {
            return VisibilityTest.ALL;
        }
        return new VisibilityTest() {
            @Override
            public boolean isVisible(
                    double minimumX,
                    double minimumY,
                    double minimumZ,
                    double maximumX,
                    double maximumY,
                    double maximumZ) {
                return frustum.isVisible(new AABB(minimumX, minimumY, minimumZ, maximumX, maximumY, maximumZ));
            }

            @Override
            public Classification classify(
                    double minimumX,
                    double minimumY,
                    double minimumZ,
                    double maximumX,
                    double maximumY,
                    double maximumZ) {
                int minX = floorToInt(minimumX);
                int minY = floorToInt(minimumY);
                int minZ = floorToInt(minimumZ);
                int maxX = inclusiveMaximum(minX, maximumX);
                int maxY = inclusiveMaximum(minY, maximumY);
                int maxZ = inclusiveMaximum(minZ, maximumZ);
                int classification = frustum.cubeInFrustum(new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ));
                return switch (classification) {
                    case -2 -> Classification.INSIDE;
                    case -1 -> Classification.INTERSECTING;
                    default -> Classification.OUTSIDE;
                };
            }
        };
    }

    private static int floorToInt(double value) {
        return (int) Math.clamp(Math.floor(value), Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    private static int ceilToInt(double value) {
        return (int) Math.clamp(Math.ceil(value), Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    private static int inclusiveMaximum(int minimum, double maximum) {
        int ceiling = ceilToInt(maximum);
        return ceiling <= minimum ? minimum : ceiling - 1;
    }
}
