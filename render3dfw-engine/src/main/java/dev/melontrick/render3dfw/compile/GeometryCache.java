package dev.melontrick.render3dfw.compile;

import dev.melontrick.render3dfw.model.Shape3d;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.LongAdder;

public final class GeometryCache {
    private final ShapeRegistry registry;
    private final long maximumBytes;
    private final Map<Key, CompiledShape> entries = new LinkedHashMap<>(64, 0.75F, true);
    private final AtomicReferenceArray<RecentGeometry> recentGeometry =
            new AtomicReferenceArray<>(DetailLevel.values().length);
    private long bytes;
    private final LongAdder hits = new LongAdder();
    private final LongAdder misses = new LongAdder();

    public GeometryCache(ShapeRegistry registry, long maximumBytes) {
        this.registry = Objects.requireNonNull(registry, "registry");
        if (maximumBytes < 1L) {
            throw new IllegalArgumentException("maximumBytes must be positive");
        }
        this.maximumBytes = maximumBytes;
    }

    public CompiledShape get(Shape3d shape, DetailLevel detail) {
        int recentIndex = detail.ordinal();
        RecentGeometry recent = recentGeometry.get(recentIndex);
        if (recent != null && recent.matches(shape)) {
            hits.increment();
            return recent.geometry();
        }
        return getSlow(shape, detail, recentIndex);
    }

    public LookupSession openLookupSession() {
        return new LookupSession();
    }

    private synchronized CompiledShape getSlow(Shape3d shape, DetailLevel detail, int recentIndex) {
        RecentGeometry recent = recentGeometry.get(recentIndex);
        if (recent != null && recent.matches(shape)) {
            hits.increment();
            return recent.geometry();
        }
        Key key = new Key(shape, detail);
        CompiledShape cached = entries.get(key);
        if (cached != null) {
            hits.increment();
            recentGeometry.set(recentIndex, new RecentGeometry(shape, cached));
            return cached;
        }
        misses.increment();
        CompiledShape compiled = registry.compile(shape, detail);
        long weight = compiled.estimatedBytes();
        if (weight <= maximumBytes) {
            entries.put(key, compiled);
            bytes += weight;
            evictIfNeeded();
            recentGeometry.set(recentIndex, new RecentGeometry(shape, compiled));
        }
        return compiled;
    }

    public synchronized CacheStats stats() {
        return new CacheStats(entries.size(), bytes, hits.sum(), misses.sum());
    }

    public synchronized void clear() {
        entries.clear();
        for (int index = 0; index < recentGeometry.length(); index++) {
            recentGeometry.set(index, null);
        }
        bytes = 0L;
    }

    private void evictIfNeeded() {
        var iterator = entries.entrySet().iterator();
        while (bytes > maximumBytes && iterator.hasNext()) {
            Map.Entry<Key, CompiledShape> eldest = iterator.next();
            bytes -= eldest.getValue().estimatedBytes();
            iterator.remove();
        }
    }

    private record Key(Shape3d shape, DetailLevel detail) {
        private Key {
            Objects.requireNonNull(shape, "shape");
            Objects.requireNonNull(detail, "detail");
        }
    }

    private record RecentGeometry(Shape3d shape, CompiledShape geometry) {
        private boolean matches(Shape3d candidate) {
            return shape == candidate || shape.equals(candidate);
        }
    }

    public record CacheStats(int entries, long bytes, long hits, long misses) {}

    public final class LookupSession implements AutoCloseable {
        private final Shape3d[] shapes = new Shape3d[DetailLevel.values().length];
        private final CompiledShape[] geometry = new CompiledShape[DetailLevel.values().length];
        private long localHits;

        public CompiledShape get(Shape3d shape, DetailLevel detail) {
            int index = detail.ordinal();
            Shape3d localShape = shapes[index];
            if (shape == localShape || (localShape != null && shape.equals(localShape))) {
                localHits++;
                return geometry[index];
            }
            CompiledShape compiled = GeometryCache.this.get(shape, detail);
            shapes[index] = shape;
            geometry[index] = compiled;
            return compiled;
        }

        @Override
        public void close() {
            hits.add(localHits);
            localHits = 0L;
        }
    }
}
