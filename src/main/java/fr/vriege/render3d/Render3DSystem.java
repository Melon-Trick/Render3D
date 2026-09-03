package fr.vriege.render3d;

import fr.vriege.render3d.api.RenderCommand;
import fr.vriege.render3d.compile.GeometryCache;
import fr.vriege.render3d.compile.ShapeRegistry;
import fr.vriege.render3d.frame.CameraView;
import fr.vriege.render3d.frame.FrameCompiler;
import fr.vriege.render3d.frame.RenderFrame;
import fr.vriege.render3d.frame.RenderSettings;
import fr.vriege.render3d.scene.RenderScene;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

/** Engine-independent Render3D runtime. Platform modules only feed cameras and consume frames. */
public final class Render3DSystem {
    private final ShapeRegistry shapes;
    private final ConcurrentMap<String, RenderScene> scenes = new ConcurrentHashMap<>();
    private final AtomicReference<RenderSettings> settings;
    private final AtomicReference<CompilerResources> resources;

    private Render3DSystem(ShapeRegistry shapes, RenderSettings initialSettings) {
        this.shapes = Objects.requireNonNull(shapes, "shapes");
        settings = new AtomicReference<>(Objects.requireNonNull(initialSettings, "initialSettings"));
        resources = new AtomicReference<>(compilerResources(initialSettings.geometryCacheBytes()));
    }

    public static Render3DSystem create() {
        return new Render3DSystem(ShapeRegistry.createDefault(), RenderSettings.defaults());
    }

    public static Render3DSystem create(ShapeRegistry shapes, RenderSettings settings) {
        return new Render3DSystem(shapes, settings);
    }

    public ShapeRegistry shapes() {
        return shapes;
    }

    public RenderScene scene(String id) {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("scene id must not be blank");
        }
        return scenes.computeIfAbsent(id, ignored -> new RenderScene());
    }

    public void removeScene(String id) {
        scenes.remove(Objects.requireNonNull(id, "id"));
    }

    public RenderSettings settings() {
        return settings.get();
    }

    public void settings(RenderSettings newSettings) {
        RenderSettings previous = settings.getAndSet(Objects.requireNonNull(newSettings, "newSettings"));
        if (previous.geometryCacheBytes() != newSettings.geometryCacheBytes()) {
            resources.set(compilerResources(newSettings.geometryCacheBytes()));
        }
    }

    public RenderFrame compile(CameraView camera) {
        List<RenderCommand> commands = new ArrayList<>();
        for (RenderScene scene : scenes.values()) {
            commands.addAll(scene.snapshot());
        }
        if (commands.isEmpty()) {
            return RenderFrame.empty();
        }
        return resources.get().compiler().compile(commands, camera, settings.get());
    }

    public GeometryCache.CacheStats cacheStats() {
        return resources.get().cache().stats();
    }

    public void clearCache() {
        resources.get().cache().clear();
    }

    private CompilerResources compilerResources(long cacheBytes) {
        GeometryCache cache = new GeometryCache(shapes, cacheBytes);
        return new CompilerResources(cache, new FrameCompiler(cache));
    }

    private record CompilerResources(GeometryCache cache, FrameCompiler compiler) {}
}
