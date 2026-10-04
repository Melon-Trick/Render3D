package dev.vriege.render3dfw.frame;

import dev.vriege.render3dfw.math.Vec3d;
import java.util.Objects;

public record CameraView(Vec3d position, VisibilityTest visibility, long reuseKey) {
    public static final long NO_REUSE = Long.MIN_VALUE;

    public CameraView {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(visibility, "visibility");
    }

    public CameraView(Vec3d position, VisibilityTest visibility) {
        this(position, visibility, NO_REUSE);
    }

    public static CameraView at(Vec3d position) {
        return new CameraView(position, VisibilityTest.ALL, exactKey(position));
    }

    public static CameraView reusable(Vec3d position, VisibilityTest visibility, long reuseKey) {
        if (reuseKey == NO_REUSE) {
            throw new IllegalArgumentException("reuseKey is reserved");
        }
        return new CameraView(position, visibility, reuseKey);
    }

    public static long quantizedKey(
            Vec3d position, double yaw, double pitch, double positionQuantum, double rotationQuantum) {
        Objects.requireNonNull(position, "position");
        if (!(positionQuantum > 0.0) || !(rotationQuantum > 0.0)) {
            throw new IllegalArgumentException("quantums must be positive");
        }
        long result = mix(quantize(position.x(), positionQuantum));
        result = mix(result ^ quantize(position.y(), positionQuantum));
        result = mix(result ^ quantize(position.z(), positionQuantum));
        result = mix(result ^ quantize(yaw, rotationQuantum));
        result = mix(result ^ quantize(pitch, rotationQuantum));
        return result == NO_REUSE ? result + 1L : result;
    }

    public boolean reusable() {
        return reuseKey != NO_REUSE;
    }

    private static long exactKey(Vec3d position) {
        long result = mix(Double.doubleToLongBits(position.x()));
        result = mix(result ^ Double.doubleToLongBits(position.y()));
        result = mix(result ^ Double.doubleToLongBits(position.z()));
        return result == NO_REUSE ? result + 1L : result;
    }

    private static long quantize(double value, double quantum) {
        return Math.round(value / quantum);
    }

    private static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xbf58476d1ce4e5b9L;
        value ^= value >>> 27;
        value *= 0x94d049bb133111ebL;
        return value ^ value >>> 31;
    }
}
