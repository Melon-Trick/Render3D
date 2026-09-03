package dev.melontrick.render3dfw.model;

import dev.melontrick.render3dfw.math.Bounds3d;

/** Marker contract for immutable, cacheable geometry models. */
public interface Shape3d {
    Bounds3d bounds();
}
