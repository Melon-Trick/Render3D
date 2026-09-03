package dev.melontrick.render3dfw.compile;

import dev.melontrick.render3dfw.model.Shape3d;

@FunctionalInterface
public interface ShapeCompiler<S extends Shape3d> {
    void compile(S shape, DetailLevel detail, GeometryBuilder output);
}
