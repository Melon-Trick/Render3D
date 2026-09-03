package dev.melontrick.render3dfw.fabric.v26_1;

import dev.melontrick.render3dfw.Render3DSystem;

public final class FabricRender3D {
    private static final Render3DSystem SYSTEM = Render3DSystem.create();
    private static final FabricProgramRegistry PROGRAMS = FabricProgramRegistry.createDefault();
    private static final FabricRenderBackend BACKEND = new FabricRenderBackend(SYSTEM, PROGRAMS);

    private FabricRender3D() {}

    public static Render3DSystem system() {
        return SYSTEM;
    }

    public static FabricProgramRegistry programs() {
        return PROGRAMS;
    }

    static void install() {
        BACKEND.install();
    }

    static void close() {
        PROGRAMS.close();
    }
}
