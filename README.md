# Render3D

Render3D is a Java 25 framework for high-volume 3D debugging overlays. Its model, scene,
culling, LOD, cache and batching code are engine-independent. Minecraft integration lives in a
strictly separate adapter for each supported Fabric/Minecraft version.

## Modules

- `render3d-core` (the root project): pure Java, with no Minecraft, Fabric, LWJGL or rendering API
  dependency.
- `fabric-26.1`: the minimal Minecraft 26.1 hook and native program implementations. Its default
  flat-color program uses consolidated line/triangle streams,
  camera-relative vertices, cached pipeline variants and at most two draws per state bucket.

The next Minecraft version should add another sibling adapter (`fabric-<version>`) rather than
changing core contracts.

## Core design

- immutable primitive models and custom `Shape3d` support;
- automatic shape compiler discovery through `ServiceLoader`;
- weighted LRU geometry cache, independently of instance color and placement;
- distance/detail/frustum culling and command/index budgets;
- automatic LOD for procedural geometry;
- state buckets keyed by program, depth, blend and cull modes;
- lock-free immutable scene snapshots for render-thread reads.

The core runtime classpath is empty: the produced core JAR resolves only to the JDK's `java.base`
module. Minecraft classes occur exclusively under `fabric-26.1`.

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
