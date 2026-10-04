package dev.vriege.render3dfw.api;

public enum ShapeMode {
    LINES(true, false),
    FILL(false, true),
    BOTH(true, true);

    private final boolean lines;
    private final boolean fill;

    ShapeMode(boolean lines, boolean fill) {
        this.lines = lines;
        this.fill = fill;
    }

    public boolean lines() {
        return lines;
    }

    public boolean fill() {
        return fill;
    }
}
