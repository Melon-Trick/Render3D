package dev.melontrick.render3dfw.frame;

@FunctionalInterface
public interface VisibilityTest {
    VisibilityTest ALL = (minimumX, minimumY, minimumZ, maximumX, maximumY, maximumZ) -> true;

    boolean isVisible(
            double minimumX, double minimumY, double minimumZ, double maximumX, double maximumY, double maximumZ);
}
