package fr.vriege.render3d.api;

/** A packed ARGB color. */
public record ColorRgba(int argb) {
    public static final ColorRgba WHITE = new ColorRgba(0xFFFFFFFF);
    public static final ColorRgba TRANSPARENT = new ColorRgba(0x00000000);

    public static ColorRgba of(int red, int green, int blue, int alpha) {
        requireByte(red, "red");
        requireByte(green, "green");
        requireByte(blue, "blue");
        requireByte(alpha, "alpha");
        return new ColorRgba(alpha << 24 | red << 16 | green << 8 | blue);
    }

    public int alpha() {
        return argb >>> 24;
    }

    public int red() {
        return argb >>> 16 & 0xFF;
    }

    public int green() {
        return argb >>> 8 & 0xFF;
    }

    public int blue() {
        return argb & 0xFF;
    }

    private static void requireByte(int value, String name) {
        if (value < 0 || value > 255) {
            throw new IllegalArgumentException(name + " must be between 0 and 255");
        }
    }
}
