package fr.vriege.render3d.fabric.v26_1;

import fr.vriege.render3d.frame.RenderBatch;
import fr.vriege.render3d.math.Vec3d;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;

/**
 * Version-specific bridge for a Render3D program id. Implementations may use gizmos, a custom
 * RenderPipeline, or another Minecraft 26.1 rendering facility.
 */
@FunctionalInterface
public interface FabricProgram {
    void draw(LevelRenderContext context, Vec3d cameraPosition, RenderBatch batch);
}
