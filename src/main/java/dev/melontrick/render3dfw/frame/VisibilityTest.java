package dev.melontrick.render3dfw.frame;

import dev.melontrick.render3dfw.math.Bounds3d;

@FunctionalInterface
public interface VisibilityTest {
    VisibilityTest ALL = bounds -> true;

    boolean isVisible(Bounds3d bounds);
}
