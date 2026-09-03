package dev.melontrick.render3dfw.frame;

import dev.melontrick.render3dfw.api.RenderCommand;
import dev.melontrick.render3dfw.api.RenderState;
import dev.melontrick.render3dfw.api.RenderStyle;
import dev.melontrick.render3dfw.api.ShapeMode;
import dev.melontrick.render3dfw.compile.CompiledShape;
import dev.melontrick.render3dfw.math.Vec3d;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.RandomAccess;

public final class RenderBatch {
    private final RenderState state;
    private final CompiledShape[] geometries;
    private final Vec3d[] translations;
    private final RenderStyle[] styles;
    private final double[] distancesSquared;
    private final CompiledShape uniformGeometry;
    private final RenderStyle uniformStyle;
    private final ShapeMode uniformShapeMode;
    private final Vec3d distanceOrigin;
    private final RenderCommand[] commandInstances;
    private final List<RenderCommand> snapshotCommands;
    private final int size;

    public RenderBatch(RenderState state, List<DrawInstance> instances) {
        this.state = Objects.requireNonNull(state, "state");
        Objects.requireNonNull(instances, "instances");
        if (instances.isEmpty()) {
            throw new IllegalArgumentException("a render batch must not be empty");
        }
        size = instances.size();
        geometries = new CompiledShape[size];
        translations = new Vec3d[size];
        styles = new RenderStyle[size];
        distancesSquared = new double[size];
        uniformGeometry = null;
        uniformStyle = null;
        ShapeMode shapeMode = instances.getFirst().style().shapeMode();
        distanceOrigin = null;
        commandInstances = null;
        snapshotCommands = null;
        for (int index = 0; index < size; index++) {
            DrawInstance instance = instances.get(index);
            geometries[index] = instance.geometry();
            translations[index] = instance.translation();
            styles[index] = instance.style();
            if (instance.style().shapeMode() != shapeMode) {
                shapeMode = null;
            }
            distancesSquared[index] = instance.distanceSquared();
        }
        uniformShapeMode = shapeMode;
    }

    private RenderBatch(RenderState state, Builder builder) {
        this.state = state;
        geometries = builder.geometries;
        translations = null;
        styles = null;
        distancesSquared = builder.distancesSquared;
        uniformGeometry = builder.uniformGeometry;
        uniformStyle = builder.uniformStyle;
        uniformShapeMode = builder.uniformShapeMode;
        distanceOrigin = builder.distanceOrigin;
        commandInstances = builder.commands;
        snapshotCommands = null;
        size = builder.size;
    }

    private RenderBatch(
            CompiledShape geometry,
            RenderCommand representative,
            RenderStyle uniformStyle,
            ShapeMode uniformShapeMode,
            List<RenderCommand> commands,
            Vec3d origin) {
        state = representative.state();
        geometries = null;
        translations = null;
        styles = null;
        distancesSquared = null;
        uniformGeometry = geometry;
        this.uniformStyle = uniformStyle;
        this.uniformShapeMode = uniformShapeMode;
        distanceOrigin = origin;
        commandInstances = null;
        snapshotCommands = commands;
        size = commands.size();
    }

    private RenderBatch(
            CompiledShape geometry,
            RenderCommand representative,
            RenderStyle uniformStyle,
            ShapeMode uniformShapeMode,
            RenderCommand[] commands,
            Vec3d origin) {
        state = representative.state();
        geometries = null;
        translations = null;
        styles = null;
        distancesSquared = null;
        uniformGeometry = geometry;
        this.uniformStyle = uniformStyle;
        this.uniformShapeMode = uniformShapeMode;
        distanceOrigin = origin;
        commandInstances = commands;
        snapshotCommands = null;
        size = commands.length;
    }

    public static RenderBatch uniformSnapshot(
            CompiledShape geometry,
            RenderCommand representative,
            RenderStyle uniformStyle,
            ShapeMode uniformShapeMode,
            List<RenderCommand> commands,
            Vec3d distanceOrigin) {
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(representative, "representative");
        Objects.requireNonNull(commands, "commands");
        Objects.requireNonNull(distanceOrigin, "distanceOrigin");
        if (commands.isEmpty()) {
            throw new IllegalArgumentException("a render batch must not be empty");
        }
        return new RenderBatch(geometry, representative, uniformStyle, uniformShapeMode, commands, distanceOrigin);
    }

    static RenderBatch uniformCommands(
            CompiledShape geometry,
            RenderCommand representative,
            RenderStyle uniformStyle,
            ShapeMode uniformShapeMode,
            RenderCommand[] commands,
            Vec3d distanceOrigin) {
        return new RenderBatch(geometry, representative, uniformStyle, uniformShapeMode, commands, distanceOrigin);
    }

    public RenderState state() {
        return state;
    }

    public int size() {
        return size;
    }

    public CompiledShape geometry(int index) {
        checkIndex(index);
        return geometries == null ? uniformGeometry : geometries[index];
    }

    public Vec3d translation(int index) {
        checkIndex(index);
        if (translations != null) {
            return translations[index];
        }
        return command(index).translation();
    }

    public RenderStyle style(int index) {
        checkIndex(index);
        if (styles != null) {
            return styles[index];
        }
        return uniformStyle != null ? uniformStyle : command(index).style();
    }

    public double distanceSquared(int index) {
        checkIndex(index);
        if (distancesSquared != null) {
            return distancesSquared[index];
        }
        CompiledShape geometry = geometry(index);
        Vec3d translation = translation(index);
        double centerX =
                (geometry.bounds().minimum().x() + geometry.bounds().maximum().x()) * 0.5 + translation.x();
        double centerY =
                (geometry.bounds().minimum().y() + geometry.bounds().maximum().y()) * 0.5 + translation.y();
        double centerZ =
                (geometry.bounds().minimum().z() + geometry.bounds().maximum().z()) * 0.5 + translation.z();
        double deltaX = centerX - distanceOrigin.x();
        double deltaY = centerY - distanceOrigin.y();
        double deltaZ = centerZ - distanceOrigin.z();
        return deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
    }

    public List<DrawInstance> instances() {
        return new InstanceView();
    }

    public boolean hasUniformGeometry() {
        return geometries == null;
    }

    public CompiledShape uniformGeometry() {
        if (geometries != null) {
            throw new IllegalStateException("batch geometry is not uniform");
        }
        return uniformGeometry;
    }

    public boolean hasUniformStyle() {
        return uniformStyle != null;
    }

    public RenderStyle uniformStyle() {
        if (!hasUniformStyle()) {
            throw new IllegalStateException("batch style is not uniform");
        }
        return uniformStyle;
    }

    public boolean hasUniformShapeMode() {
        return uniformShapeMode != null;
    }

    public ShapeMode uniformShapeMode() {
        if (!hasUniformShapeMode()) {
            throw new IllegalStateException("batch shape mode is not uniform");
        }
        return uniformShapeMode;
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException(index);
        }
    }

    private RenderCommand command(int index) {
        return snapshotCommands == null ? commandInstances[index] : snapshotCommands.get(index);
    }

    static final class Builder {
        private CompiledShape[] geometries;
        private RenderCommand[] commands;
        private double[] distancesSquared;
        private CompiledShape uniformGeometry;
        private RenderStyle uniformStyle;
        private ShapeMode uniformShapeMode;
        private final Vec3d distanceOrigin;
        private int size;

        Builder() {
            this(16, Vec3d.ZERO, true);
        }

        Builder(int initialCapacity) {
            this(initialCapacity, Vec3d.ZERO, true);
        }

        Builder(int initialCapacity, Vec3d distanceOrigin, boolean storeDistances) {
            int capacity = Math.max(1, initialCapacity);
            this.distanceOrigin = Objects.requireNonNull(distanceOrigin, "distanceOrigin");
            commands = new RenderCommand[capacity];
            distancesSquared = storeDistances ? new double[capacity] : null;
        }

        void add(RenderCommand command, CompiledShape geometry, double distanceSquared) {
            ensureCapacity(size + 1);
            RenderStyle style = command.style();
            if (size == 0) {
                uniformGeometry = geometry;
                uniformStyle = style;
                uniformShapeMode = style.shapeMode();
            } else {
                if (geometries == null && geometry != uniformGeometry) {
                    geometries = new CompiledShape[commands.length];
                    Arrays.fill(geometries, 0, size, uniformGeometry);
                    uniformGeometry = null;
                }
                if (style != uniformStyle) {
                    uniformStyle = null;
                }
                if (style.shapeMode() != uniformShapeMode) {
                    uniformShapeMode = null;
                }
            }
            if (geometries != null) {
                geometries[size] = geometry;
            }
            commands[size] = command;
            if (distancesSquared != null) {
                distancesSquared[size] = distanceSquared;
            }
            size++;
        }

        void sortBackToFront() {
            if (distancesSquared == null) {
                throw new IllegalStateException("batch distances are not stored");
            }
            quickSort(0, size - 1);
        }

        RenderBatch build(RenderState state) {
            if (size == 0) {
                throw new IllegalStateException("cannot build an empty render batch");
            }
            return new RenderBatch(state, this);
        }

        private void ensureCapacity(int required) {
            if (required <= commands.length) {
                return;
            }
            int capacity = Math.max(required, commands.length << 1);
            if (geometries != null) {
                geometries = Arrays.copyOf(geometries, capacity);
            }
            commands = Arrays.copyOf(commands, capacity);
            if (distancesSquared != null) {
                distancesSquared = Arrays.copyOf(distancesSquared, capacity);
            }
        }

        private void quickSort(int low, int high) {
            int lower = low;
            int upper = high;
            while (lower < upper) {
                int left = lower;
                int right = upper;
                double pivot = distancesSquared[(left + right) >>> 1];
                while (left <= right) {
                    while (distancesSquared[left] > pivot) {
                        left++;
                    }
                    while (distancesSquared[right] < pivot) {
                        right--;
                    }
                    if (left <= right) {
                        swap(left, right);
                        left++;
                        right--;
                    }
                }
                if (right - lower < upper - left) {
                    if (lower < right) {
                        quickSort(lower, right);
                    }
                    lower = left;
                } else {
                    if (left < upper) {
                        quickSort(left, upper);
                    }
                    upper = right;
                }
            }
        }

        private void swap(int first, int second) {
            if (geometries != null) {
                CompiledShape geometry = geometries[first];
                geometries[first] = geometries[second];
                geometries[second] = geometry;
            }
            RenderCommand command = commands[first];
            commands[first] = commands[second];
            commands[second] = command;
            if (distancesSquared != null) {
                double distanceSquared = distancesSquared[first];
                distancesSquared[first] = distancesSquared[second];
                distancesSquared[second] = distanceSquared;
            }
        }
    }

    private final class InstanceView extends AbstractList<DrawInstance> implements RandomAccess {
        @Override
        public DrawInstance get(int index) {
            return new DrawInstance(geometry(index), translation(index), style(index), distanceSquared(index));
        }

        @Override
        public int size() {
            return size;
        }
    }
}
