package dev.melontrick.render3dfw.compile;

@FunctionalInterface
public interface ShapeCompilerProvider {
    void registerCompilers(ShapeRegistry registry);
}
