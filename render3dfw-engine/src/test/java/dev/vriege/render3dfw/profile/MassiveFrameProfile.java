package dev.vriege.render3dfw.profile;

import dev.vriege.render3dfw.Render3DSystem;
import dev.vriege.render3dfw.api.ColorRgba;
import dev.vriege.render3dfw.api.RenderCommand;
import dev.vriege.render3dfw.api.RenderState;
import dev.vriege.render3dfw.api.RenderStyle;
import dev.vriege.render3dfw.compile.GeometryCache;
import dev.vriege.render3dfw.compile.ShapeRegistry;
import dev.vriege.render3dfw.frame.CameraView;
import dev.vriege.render3dfw.frame.FrameCompiler;
import dev.vriege.render3dfw.frame.RenderFrame;
import dev.vriege.render3dfw.frame.RenderSettings;
import dev.vriege.render3dfw.math.Vec3d;
import dev.vriege.render3dfw.model.Line3d;
import dev.vriege.render3dfw.scene.SceneSnapshot;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;

public final class MassiveFrameProfile {
    private static final int COMMAND_COUNT = 500_000;
    private static final long PROFILE_NANOS = Duration.ofSeconds(5L).toNanos();
    private static volatile long sink;

    private MassiveFrameProfile() {}

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 2) {
            throw new IllegalArgumentException("expected mode and recording path");
        }
        String mode = arguments[0];
        Path recordingPath = Path.of(arguments[1]);
        SceneSnapshot snapshot = createSnapshot(mode.equals("dynamic"));

        ProfileResult result =
                switch (mode) {
                    case "uncached" -> profileUncached(snapshot, recordingPath, false);
                    case "dynamic" -> profileUncached(snapshot, recordingPath, true);
                    case "cached" -> profileCached(snapshot, recordingPath);
                    default -> throw new IllegalArgumentException("unknown mode: " + mode);
                };
        System.out.printf(
                "mode=%s iterations=%d elapsedMs=%.3f averageMicros=%.3f sink=%d%n",
                mode,
                result.iterations(),
                result.elapsedNanos() / 1_000_000.0,
                result.elapsedNanos() / 1_000.0 / result.iterations(),
                sink);
    }

    private static ProfileResult profileUncached(SceneSnapshot snapshot, Path recordingPath, boolean dynamic)
            throws Exception {
        GeometryCache cache = new GeometryCache(ShapeRegistry.createDefault(), 128L * 1024L * 1024L);
        FrameCompiler compiler = new FrameCompiler(cache);
        Vec3d cameraPosition = new Vec3d(100.0, 100.0, 50.0);
        CameraView camera = dynamic
                ? new CameraView(cameraPosition, (minimumX, minimumY, minimumZ, maximumX, maximumY, maximumZ) -> true)
                : CameraView.at(cameraPosition);
        RenderSettings settings = RenderSettings.massive();
        for (int index = 0; index < 3; index++) {
            consume(compiler.compileSnapshots(List.of(snapshot), camera, settings));
        }
        printPhases(compiler.compileSnapshots(List.of(snapshot), camera, settings));
        return record(recordingPath, () -> consume(compiler.compileSnapshots(List.of(snapshot), camera, settings)));
    }

    private static ProfileResult profileCached(SceneSnapshot snapshot, Path recordingPath) throws Exception {
        Render3DSystem system = Render3DSystem.create(ShapeRegistry.createDefault(), RenderSettings.massive());
        system.scene("massive").publish(snapshot);
        CameraView camera = CameraView.reusable(new Vec3d(100.0, 100.0, 50.0), (a, b, c, d, e, f) -> true, 42L);
        consume(system.compile(camera));
        for (int index = 0; index < 100_000; index++) {
            consume(system.compile(camera));
        }
        ProfileResult result = record(recordingPath, () -> consume(system.compile(camera)));
        Render3DSystem.FrameReuseStats stats = system.frameReuseStats();
        System.out.printf("frameReuseHits=%d frameReuseMisses=%d%n", stats.hits(), stats.misses());
        return result;
    }

    private static ProfileResult record(Path recordingPath, ProfileStep step) throws Exception {
        Files.createDirectories(recordingPath.getParent());
        long startedAt;
        long iterations = 0L;
        try (Recording recording = new Recording(Configuration.getConfiguration("profile"))) {
            recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(5L));
            recording.start();
            startedAt = System.nanoTime();
            long deadline = startedAt + PROFILE_NANOS;
            do {
                step.run();
                iterations++;
            } while (System.nanoTime() < deadline);
            long elapsed = System.nanoTime() - startedAt;
            recording.stop();
            recording.dump(recordingPath);
            return new ProfileResult(iterations, elapsed);
        }
    }

    private static SceneSnapshot createSnapshot(boolean dynamic) {
        Line3d shape = new Line3d(Vec3d.ZERO, new Vec3d(1.0, 0.0, 0.0));
        RenderCommand firstModel =
                RenderCommand.of(shape, RenderStyle.lines(ColorRgba.WHITE)).withState(RenderState.OPAQUE_DEBUG);
        RenderCommand secondModel = dynamic
                ? RenderCommand.of(shape, RenderStyle.lines(ColorRgba.of(255, 96, 32, 255)))
                        .withState(RenderState.OPAQUE_DEBUG)
                : firstModel;
        List<RenderCommand> commands = new ArrayList<>(COMMAND_COUNT);
        for (int index = 0; index < COMMAND_COUNT; index++) {
            double x = (index % 100) * 2.0;
            double y = ((index / 100) % 100) * 2.0;
            double z = (index / 10_000) * 2.0;
            RenderCommand model = (index & 1) == 0 ? firstModel : secondModel;
            commands.add(model.at(new Vec3d(x, y, z)));
        }
        return SceneSnapshot.of(commands);
    }

    private static void consume(RenderFrame frame) {
        sink += frame.stats().renderedCommands() + frame.batches().size();
    }

    private static void printPhases(RenderFrame frame) {
        System.out.printf(
                "cullingMs=%.3f geometryMs=%.3f batchingMs=%.3f totalMs=%.3f%n",
                frame.stats().cullingNanos() / 1_000_000.0,
                frame.stats().geometryNanos() / 1_000_000.0,
                frame.stats().batchingNanos() / 1_000_000.0,
                frame.stats().compilationNanos() / 1_000_000.0);
    }

    @FunctionalInterface
    private interface ProfileStep {
        void run();
    }

    private record ProfileResult(long iterations, long elapsedNanos) {}
}
