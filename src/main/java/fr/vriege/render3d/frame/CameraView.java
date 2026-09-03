package fr.vriege.render3d.frame;

import fr.vriege.render3d.math.Vec3d;
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
