package fr.vriege.render3d.model;

import fr.vriege.render3d.math.Bounds3d;

/** Marker contract for immutable, cacheable geometry models. */
public interface Shape3d {
    Bounds3d bounds();
}
