package dev.melontrick.render3dfw.compile;

import dev.melontrick.render3dfw.model.Shape3d;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

public final class ShapeRegistry {
    private final Map<Class<?>, Registration<?>> compilers = new ConcurrentHashMap<>();
    private final Map<Class<?>, Registration<?>> resolved = new ConcurrentHashMap<>();

    public static ShapeRegistry createDefault() {
        ShapeRegistry registry = new ShapeRegistry();
        BuiltinShapeCompilers.registerInto(registry);
        ServiceLoader.load(ShapeCompilerProvider.class).forEach(provider -> provider.registerCompilers(registry));
        return registry;
    }

    public <S extends Shape3d> void register(Class<S> type, ShapeCompiler<? super S> compiler) {
        register(type, compiler, false);
    }

    public <S extends Shape3d> void registerInvariant(Class<S> type, ShapeCompiler<? super S> compiler) {
        register(type, compiler, true);
    }

    private <S extends Shape3d> void register(
            Class<S> type, ShapeCompiler<? super S> compiler, boolean detailInvariant) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(compiler, "compiler");
        if (compilers.putIfAbsent(type, new Registration<>(compiler, detailInvariant)) != null) {
            throw new IllegalStateException("a shape compiler is already registered for " + type.getName());
        }
        resolved.clear();
    }

    public CompiledShape compile(Shape3d shape, DetailLevel detail) {
        Objects.requireNonNull(shape, "shape");
        Objects.requireNonNull(detail, "detail");
        Registration<?> registration = resolved.computeIfAbsent(shape.getClass(), this::resolve);
        GeometryBuilder output = new GeometryBuilder();
        compileUnchecked(registration.compiler(), shape, detail, output);
        return output.build(shape.bounds());
    }

    boolean detailInvariant(Shape3d shape) {
        return resolved.computeIfAbsent(shape.getClass(), this::resolve).detailInvariant();
    }

    private Registration<?> resolve(Class<?> concreteType) {
        Registration<?> exact = compilers.get(concreteType);
        if (exact != null) {
            return exact;
        }
        Registration<?> candidate = null;
        for (Map.Entry<Class<?>, Registration<?>> entry : compilers.entrySet()) {
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

    private record Registration<S extends Shape3d>(ShapeCompiler<? super S> compiler, boolean detailInvariant) {}
}
