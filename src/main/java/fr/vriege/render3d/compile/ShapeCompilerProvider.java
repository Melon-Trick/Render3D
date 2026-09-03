package fr.vriege.render3d.compile;

/** Service-loader extension point for automatically discovered shape compilers. */
@FunctionalInterface
public interface ShapeCompilerProvider {
    void registerCompilers(ShapeRegistry registry);
}
