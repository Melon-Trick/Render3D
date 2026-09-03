package dev.melontrick.render3dfw.fabric.v26_1;

import net.fabricmc.api.ClientModInitializer;

public final class Render3DClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        FabricRender3D.install();
    }
}
