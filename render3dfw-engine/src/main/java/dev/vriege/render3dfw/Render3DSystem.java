package dev.vriege.render3dfw;

import dev.vriege.render3dfw.compile.GeometryCache;
import dev.vriege.render3dfw.compile.ShapeRegistry;
import dev.vriege.render3dfw.frame.CameraView;
import dev.vriege.render3dfw.frame.FrameCompiler;
import dev.vriege.render3dfw.frame.RenderFrame;
import dev.vriege.render3dfw.frame.RenderSettings;
import dev.vriege.render3dfw.scene.RenderScene;
import dev.vriege.render3dfw.scene.SceneSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class Render3DSystem {
    private final ShapeRegistry shapes;
    private final ConcurrentMap<String, RenderScene> scenes = new ConcurrentHashMap<>();
    private final AtomicReference<RenderSettings> settings;
    private final AtomicReference<CompilerResources> resources;
    private final AtomicReference<FrameMemo> frameMemo = new AtomicReference<>();
    private final AtomicLong frameReuseHits = new AtomicLong();
    private final AtomicLong frameReuseMisses = new AtomicLong();
    private final AtomicLong sceneRegistryRevision = new AtomicLong();

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
        RenderScene existing = scenes.get(id);
        if (existing != null) {
            return existing;
        }
        RenderScene created = new RenderScene();
        RenderScene raced = scenes.putIfAbsent(id, created);
        if (raced != null) {
            return raced;
        }
        sceneRegistryRevision.incrementAndGet();
        return created;
    }

    public void removeScene(String id) {
        if (scenes.remove(Objects.requireNonNull(id, "id")) != null) {
            sceneRegistryRevision.incrementAndGet();
        }
    }

    public RenderSettings settings() {
        return settings.get();
    }

    public void settings(RenderSettings newSettings) {
        RenderSettings previous = settings.getAndSet(Objects.requireNonNull(newSettings, "newSettings"));
        if (previous.geometryCacheBytes() != newSettings.geometryCacheBytes()) {
            resources.set(compilerResources(newSettings.geometryCacheBytes()));
        }
        frameMemo.set(null);
    }

    public RenderFrame compile(CameraView camera) {
        Objects.requireNonNull(camera, "camera");
        RenderSettings currentSettings = settings.get();
        CompilerResources currentResources = resources.get();
        long currentSceneRegistryRevision = sceneRegistryRevision.get();
        FrameMemo memo = frameMemo.get();
        if (camera.reusable()
                && memo != null
                && memo.matches(
                        scenes.size(),
                        currentSceneRegistryRevision,
                        camera.reuseKey(),
                        currentSettings,
                        currentResources)) {
            frameReuseHits.incrementAndGet();
            return memo.reusedFrame();
        }
        if (camera.reusable()) {
            frameReuseMisses.incrementAndGet();
        }

        List<RenderScene> sceneReferences = new ArrayList<>(scenes.size());
        List<SceneSnapshot> allSnapshots = new ArrayList<>(scenes.size());
        List<SceneSnapshot> visibleSnapshots = new ArrayList<>(scenes.size());
        for (Map.Entry<String, RenderScene> entry : scenes.entrySet()) {
            RenderScene scene = entry.getValue();
            SceneSnapshot snapshot = scene.indexedSnapshot();
            sceneReferences.add(scene);
            allSnapshots.add(snapshot);
            if (snapshot.size() > 0) {
                visibleSnapshots.add(snapshot);
            }
        }
        RenderFrame frame = visibleSnapshots.isEmpty()
                ? RenderFrame.empty()
                : currentResources.compiler().compileSnapshots(visibleSnapshots, camera, currentSettings);
        if (camera.reusable()) {
            frameMemo.set(new FrameMemo(
                    sceneReferences.toArray(RenderScene[]::new),
                    allSnapshots.toArray(SceneSnapshot[]::new),
                    currentSceneRegistryRevision,
                    camera.reuseKey(),
                    currentSettings,
                    currentResources,
                    frame.asReused()));
        }
        return frame;
    }

    public GeometryCache.CacheStats cacheStats() {
        return resources.get().cache().stats();
    }

    public void clearCache() {
        resources.get().cache().clear();
        frameMemo.set(null);
    }

    public void clearFrameCache() {
        frameMemo.set(null);
    }

    public FrameReuseStats frameReuseStats() {
        return new FrameReuseStats(frameReuseHits.get(), frameReuseMisses.get());
    }

    private CompilerResources compilerResources(long cacheBytes) {
        GeometryCache cache = new GeometryCache(shapes, cacheBytes);
        return new CompilerResources(cache, new FrameCompiler(cache));
    }

    private record CompilerResources(GeometryCache cache, FrameCompiler compiler) {}

    public record FrameReuseStats(long hits, long misses) {}

    private record FrameMemo(
            RenderScene[] scenes,
            SceneSnapshot[] snapshots,
            long sceneRegistryRevision,
            long reuseKey,
            RenderSettings settings,
            CompilerResources resources,
            RenderFrame reusedFrame) {
        private boolean matches(
                int currentSceneCount,
                long currentSceneRegistryRevision,
                long currentReuseKey,
                RenderSettings currentSettings,
                CompilerResources currentResources) {
            if (reuseKey != currentReuseKey
                    || sceneRegistryRevision != currentSceneRegistryRevision
                    || settings != currentSettings
                    || resources != currentResources
                    || currentSceneCount != scenes.length) {
                return false;
            }
            for (int index = 0; index < scenes.length; index++) {
                RenderScene scene = scenes[index];
                if (scene.indexedSnapshot() != snapshots[index]) {
                    return false;
                }
            }
            return true;
        }
    }
}
