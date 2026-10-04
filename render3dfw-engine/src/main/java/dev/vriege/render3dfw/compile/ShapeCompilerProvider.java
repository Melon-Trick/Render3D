package dev.vriege.render3dfw.compile;

@FunctionalInterface
public interface ShapeCompilerProvider {
    void registerCompilers(ShapeRegistry registry);
}
