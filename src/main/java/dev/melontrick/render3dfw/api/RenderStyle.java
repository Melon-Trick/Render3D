package dev.melontrick.render3dfw.api;

import java.util.Objects;

/** Per-instance appearance, deliberately kept out of cached model geometry. */
public record RenderStyle(ColorRgba lineColor, ColorRgba fillColor, float lineWidth, ShapeMode shapeMode) {
    public RenderStyle {
        Objects.requireNonNull(lineColor, "lineColor");
        Objects.requireNonNull(fillColor, "fillColor");
        Objects.requireNonNull(shapeMode, "shapeMode");
        if (!Float.isFinite(lineWidth) || lineWidth <= 0.0F) {
            throw new IllegalArgumentException("lineWidth must be finite and positive");
        }
    }

    public static RenderStyle lines(ColorRgba color) {
        return new RenderStyle(color, ColorRgba.TRANSPARENT, 1.0F, ShapeMode.LINES);
    }

    public static RenderStyle filled(ColorRgba color) {
        return new RenderStyle(ColorRgba.TRANSPARENT, color, 1.0F, ShapeMode.FILL);
    }

    public static RenderStyle both(ColorRgba line, ColorRgba fill) {
        return new RenderStyle(line, fill, 1.0F, ShapeMode.BOTH);
    }
}
