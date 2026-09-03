# Render3DFW

Render3DFW is a modular Java 25 framework for high-volume 3D debugging overlays. Its public
contracts, geometry engine, scene processing, cache and batching remain independent from
Minecraft. Every supported Minecraft version lives in its own adapter module.

## Modules

- `render3dfw-api`: public render contracts, immutable primitive models and engine-independent
  math types. It has no runtime dependency outside the JDK.
- `render3dfw-engine`: shape compilation, automatic registration, geometry caching, culling, LOD,
  draw budgets, state batching and lock-free scene snapshots. It depends only on
  `render3dfw-api`.
- `render3dfw-fabric-26.1`: the minimal Minecraft 26.1 hooks and native GPU backend. Its output JAR
  embeds both agnostic modules for Fabric consumers.

The root project is an aggregator, following the same modular convention as AstralByte. A new
Minecraft version adds a sibling `render3dfw-fabric-<version>` module without changing the API or
engine solely for platform churn.

## Engine design

- immutable primitive models and custom `Shape3d` support;
- automatic shape compiler discovery through `ServiceLoader`;
- weighted LRU geometry cache, independently of instance color and placement;
- persistent per-scene BVHs, built on publication rather than rebuilt every frame;
- distance, detail and frustum culling with command and index budgets;
- automatic LOD for procedural geometry;
- allocation-bounded priority selection and structure-of-arrays render batches;
- state buckets keyed by program, depth, blend and cull modes;
- lock-free immutable scene snapshots for render-thread reads.

The Fabric backend reuses CPU staging memory and grows persistent GPU vertex/index buffers by
power-of-two capacity. A scene may therefore contain hundreds of thousands of commands without
forcing a complete per-command scan or fresh GPU buffer allocation every frame. If all commands
are simultaneously visible, the configured command/index budgets still bound submitted work.
Repeated opaque geometry, and repeated alpha geometry when it remains one depth-sorted group, uses
a compact 12-byte instance stream and an indexed instanced draw. `RenderSettings.massive()` raises
the safety budgets to 500,000 commands and 64 million indices for this workload.

For a very large scene, build `SceneSnapshot.of(commands)` on a worker thread and publish it with
`RenderScene.publish(snapshot)`. Rendering continues to read the previous immutable snapshot until
the new BVH is atomically available.

The API and engine classpaths contain no Minecraft, Fabric, LWJGL or rendering API dependency.
Minecraft classes occur exclusively under `render3dfw-fabric-26.1`.

## Minimal use from Fabric 26.1

```java
var scene = FabricRender3D.system().scene("navigation");
scene.update(commands -> commands.add(
    RenderCommand.of(
        new Box3d(new Vec3d(0, 64, 0), new Vec3d(1, 65, 1)),
        RenderStyle.both(
            ColorRgba.of(255, 120, 20, 255),
            ColorRgba.of(255, 120, 20, 45)
        )
    )
));
```

Custom model types are registered once with `Render3DSystem.shapes()`. Custom Minecraft programs
are registered against a `ProgramId` through `FabricRender3D.programs()`. This keeps shader and
Minecraft API churn inside the version adapter.

The built-in flat-color program supports all three depth modes (`TESTED_WRITE`,
`TESTED_READ_ONLY`, `ALWAYS_VISIBLE`), alpha or opaque blending, and back-face or no culling.
