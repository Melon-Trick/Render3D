package fr.vriege.render3d.compile;

import fr.vriege.render3d.model.Shape3d;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Thread-safe weighted LRU cache for compiled model geometry. */
public final class GeometryCache {
    private final ShapeRegistry registry;
    private final long maximumBytes;
    private final Map<Key, CompiledShape> entries = new LinkedHashMap<>(64, 0.75F, true);
    private long bytes;
    private long hits;
    private long misses;

    public GeometryCache(ShapeRegistry registry, long maximumBytes) {
        this.registry = Objects.requireNonNull(registry, "registry");
        if (maximumBytes < 1L) {
            throw new IllegalArgumentException("maximumBytes must be positive");
        }
        this.maximumBytes = maximumBytes;
    }

    public synchronized CompiledShape get(Shape3d shape, DetailLevel detail) {
        Key key = new Key(shape, detail);
        CompiledShape cached = entries.get(key);
        if (cached != null) {
            hits++;
            return cached;
        }
        misses++;
        CompiledShape compiled = registry.compile(shape, detail);
        long weight = compiled.estimatedBytes();
        if (weight <= maximumBytes) {
            entries.put(key, compiled);
            bytes += weight;
            evictIfNeeded();
        }
        return compiled;
    }

    public synchronized CacheStats stats() {
        return new CacheStats(entries.size(), bytes, hits, misses);
    }

    public synchronized void clear() {
        entries.clear();
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

    public record CacheStats(int entries, long bytes, long hits, long misses) {}
}
