package dev.melontrick.render3dfw.api;

public record DetailMask(long bits) {
    public static final DetailMask ALWAYS = new DetailMask(0L);
    public static final DetailMask ALL = new DetailMask(-1L);

    public static DetailMask of(int bit) {
        if (bit < 0 || bit >= Long.SIZE) {
            throw new IllegalArgumentException("detail bit must be between 0 and 63");
        }
        return new DetailMask(1L << bit);
    }

    public DetailMask or(DetailMask other) {
        return new DetailMask(bits | other.bits);
    }

    public boolean visibleWithin(DetailMask enabled) {
        return bits == 0L || (bits & enabled.bits) != 0L;
    }
}
