package dev.melontrick.render3dfw.fabric.v26_1;

import dev.melontrick.render3dfw.frame.RenderBatch;
import dev.melontrick.render3dfw.math.Vec3d;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;

@FunctionalInterface
public interface FabricProgram extends AutoCloseable {
    void draw(LevelRenderContext context, Vec3d cameraPosition, RenderBatch batch);

    @Override
    default void close() {}
}
