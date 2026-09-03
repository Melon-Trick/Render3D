package fr.vriege.render3d.compile;

import fr.vriege.render3d.model.Shape3d;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/** Maps model types to geometry compilers and supports application-defined forms. */
public final class ShapeRegistry {
    private final Map<Class<?>, ShapeCompiler<?>> compilers = new ConcurrentHashMap<>();
    private final Map<Class<?>, ShapeCompiler<?>> resolved = new ConcurrentHashMap<>();

    public static ShapeRegistry createDefault() {
        ShapeRegistry registry = new ShapeRegistry();
        BuiltinShapeCompilers.registerInto(registry);
        ServiceLoader.load(ShapeCompilerProvider.class).forEach(provider -> provider.registerCompilers(registry));
        return registry;
    }

    public <S extends Shape3d> void register(Class<S> type, ShapeCompiler<? super S> compiler) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(compiler, "compiler");
        if (compilers.putIfAbsent(type, compiler) != null) {
            throw new IllegalStateException("a shape compiler is already registered for " + type.getName());
        }
        resolved.clear();
    }

    public CompiledShape compile(Shape3d shape, DetailLevel detail) {
        Objects.requireNonNull(shape, "shape");
        Objects.requireNonNull(detail, "detail");
        ShapeCompiler<?> compiler = resolved.computeIfAbsent(shape.getClass(), this::resolve);
        GeometryBuilder output = new GeometryBuilder();
        compileUnchecked(compiler, shape, detail, output);
        return output.build(shape.bounds());
    }

    private ShapeCompiler<?> resolve(Class<?> concreteType) {
        ShapeCompiler<?> exact = compilers.get(concreteType);
        if (exact != null) {
            return exact;
        }
        ShapeCompiler<?> candidate = null;
        for (Map.Entry<Class<?>, ShapeCompiler<?>> entry : compilers.entrySet()) {
            if (entry.getKey().isAssignableFrom(concreteType)) {
                if (candidate != null) {
                    throw new IllegalStateException("ambiguous shape compilers for " + concreteType.getName());
                }
                candidate = entry.getValue();
            }
        }
        if (candidate == null) {
            throw new IllegalArgumentException("no shape compiler registered for " + concreteType.getName());
        }
        return candidate;
    }

    @SuppressWarnings("unchecked")
    private static <S extends Shape3d> void compileUnchecked(
            ShapeCompiler<?> compiler, S shape, DetailLevel detail, GeometryBuilder output) {
        ((ShapeCompiler<S>) compiler).compile(shape, detail, output);
    }
}
