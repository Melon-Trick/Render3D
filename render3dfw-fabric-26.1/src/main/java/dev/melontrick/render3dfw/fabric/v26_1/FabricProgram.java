package dev.melontrick.render3dfw.fabric.v26_1;

import dev.melontrick.render3dfw.frame.RenderBatch;
import dev.melontrick.render3dfw.math.Vec3d;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;

/**
 * Version-specific bridge for a Render3D program id. Implementations may use gizmos, a custom
 * RenderPipeline, or another Minecraft 26.1 rendering facility.
 */
@FunctionalInterface
public interface FabricProgram {
    void draw(LevelRenderContext context, Vec3d cameraPosition, RenderBatch batch);
}
