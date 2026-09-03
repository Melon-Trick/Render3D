package dev.melontrick.render3dfw.fabric.v26_1;

/** Service-loader extension point for automatically installed Fabric programs. */
@FunctionalInterface
public interface FabricProgramProvider {
    void registerPrograms(FabricProgramRegistry registry);
}
