package dev.melontrick.render3dfw.fabric.v26_1;

import dev.melontrick.render3dfw.Render3DSystem;
import dev.melontrick.render3dfw.frame.CameraView;
import dev.melontrick.render3dfw.frame.RenderFrame;
import dev.melontrick.render3dfw.math.Vec3d;
import java.util.Objects;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The complete Minecraft hook: one extraction callback and no game types leaking into core. */
public final class FabricRenderBackend {
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
            CameraView view = new CameraView(
                    new Vec3d(camera.x(), camera.y(), camera.z()),
                    bounds -> frustum == null
                            || frustum.isVisible(new AABB(
                                    bounds.minimum().x(),
                                    bounds.minimum().y(),
                                    bounds.minimum().z(),
                                    bounds.maximum().x(),
                                    bounds.maximum().y(),
                                    bounds.maximum().z())));
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
}
