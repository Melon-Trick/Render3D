package fr.vriege.render3d.compile;

import fr.vriege.render3d.model.Shape3d;

@FunctionalInterface
public interface ShapeCompiler<S extends Shape3d> {
    void compile(S shape, DetailLevel detail, GeometryBuilder output);
}
