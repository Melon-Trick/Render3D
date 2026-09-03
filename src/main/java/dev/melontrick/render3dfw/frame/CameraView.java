package dev.melontrick.render3dfw.frame;

import dev.melontrick.render3dfw.math.Vec3d;
import java.util.Objects;

public record CameraView(Vec3d position, VisibilityTest visibility) {
    public CameraView {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(visibility, "visibility");
    }

    public static CameraView at(Vec3d position) {
        return new CameraView(position, VisibilityTest.ALL);
    }
}
