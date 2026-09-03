package dev.melontrick.render3dfw.api;

/** Depth behavior forms part of the batch key and therefore never changes inside a draw. */
public enum DepthMode {
    TESTED_WRITE,
    TESTED_READ_ONLY,
    ALWAYS_VISIBLE
}
