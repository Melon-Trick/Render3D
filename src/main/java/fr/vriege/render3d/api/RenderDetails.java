package fr.vriege.render3d.api;

public final class RenderDetails {
    public static final DetailMask BASIC = DetailMask.of(0);
    public static final DetailMask GEOMETRY = DetailMask.of(1);
    public static final DetailMask PATHS = DetailMask.of(2);
    public static final DetailMask COLLISIONS = DetailMask.of(3);
    public static final DetailMask NORMALS = DetailMask.of(4);
    public static final DetailMask INTERNALS = DetailMask.of(5);
    public static final DetailMask EXPENSIVE = DetailMask.of(6);

    private RenderDetails() {}
}
