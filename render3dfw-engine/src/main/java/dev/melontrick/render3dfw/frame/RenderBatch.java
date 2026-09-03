package dev.melontrick.render3dfw.frame;

import dev.melontrick.render3dfw.api.RenderState;
import dev.melontrick.render3dfw.api.RenderStyle;
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
        for (int index = 0; index < size; index++) {
            DrawInstance instance = instances.get(index);
            geometries[index] = instance.geometry();
            translations[index] = instance.translation();
            styles[index] = instance.style();
            distancesSquared[index] = instance.distanceSquared();
        }
    }

    private RenderBatch(RenderState state, Builder builder) {
        this.state = state;
        geometries = builder.geometries;
        translations = builder.translations;
        styles = builder.styles;
        distancesSquared = builder.distancesSquared;
        size = builder.size;
    }

    public RenderState state() {
        return state;
    }

    public int size() {
        return size;
    }

    public CompiledShape geometry(int index) {
        checkIndex(index);
        return geometries[index];
    }

    public Vec3d translation(int index) {
        checkIndex(index);
        return translations[index];
    }

    public RenderStyle style(int index) {
        checkIndex(index);
        return styles[index];
    }

    public double distanceSquared(int index) {
        checkIndex(index);
        return distancesSquared[index];
    }

    public List<DrawInstance> instances() {
        return new InstanceView();
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException(index);
        }
    }

    static final class Builder {
        private CompiledShape[] geometries;
        private Vec3d[] translations;
        private RenderStyle[] styles;
        private double[] distancesSquared;
        private int size;

        Builder() {
            this(16);
        }

        Builder(int initialCapacity) {
            int capacity = Math.max(1, initialCapacity);
            geometries = new CompiledShape[capacity];
            translations = new Vec3d[capacity];
            styles = new RenderStyle[capacity];
            distancesSquared = new double[capacity];
        }

        void add(CompiledShape geometry, Vec3d translation, RenderStyle style, double distanceSquared) {
            ensureCapacity(size + 1);
            geometries[size] = geometry;
            translations[size] = translation;
            styles[size] = style;
            distancesSquared[size] = distanceSquared;
            size++;
        }

        void sortBackToFront() {
            quickSort(0, size - 1);
        }

        RenderBatch build(RenderState state) {
            if (size == 0) {
                throw new IllegalStateException("cannot build an empty render batch");
            }
            return new RenderBatch(state, this);
        }

        private void ensureCapacity(int required) {
            if (required <= geometries.length) {
                return;
            }
            int capacity = Math.max(required, geometries.length << 1);
            geometries = Arrays.copyOf(geometries, capacity);
            translations = Arrays.copyOf(translations, capacity);
            styles = Arrays.copyOf(styles, capacity);
            distancesSquared = Arrays.copyOf(distancesSquared, capacity);
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
            CompiledShape geometry = geometries[first];
            geometries[first] = geometries[second];
            geometries[second] = geometry;
            Vec3d translation = translations[first];
            translations[first] = translations[second];
            translations[second] = translation;
            RenderStyle style = styles[first];
            styles[first] = styles[second];
            styles[second] = style;
            double distanceSquared = distancesSquared[first];
            distancesSquared[first] = distancesSquared[second];
            distancesSquared[second] = distanceSquared;
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
