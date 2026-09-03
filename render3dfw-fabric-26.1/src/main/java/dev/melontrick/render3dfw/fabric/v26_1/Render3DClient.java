package dev.melontrick.render3dfw.fabric.v26_1;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

public final class Render3DClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        FabricRender3D.install();
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> FabricRender3D.close());
    }
}
