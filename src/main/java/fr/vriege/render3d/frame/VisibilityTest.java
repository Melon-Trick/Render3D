package fr.vriege.render3d.frame;

import fr.vriege.render3d.math.Bounds3d;

@FunctionalInterface
public interface VisibilityTest {
    VisibilityTest ALL = bounds -> true;

    boolean isVisible(Bounds3d bounds);
}
