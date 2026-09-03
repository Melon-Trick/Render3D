package dev.melontrick.render3dfw.fabric.v26_1;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.melontrick.render3dfw.api.BlendMode;
import dev.melontrick.render3dfw.api.CullMode;
import dev.melontrick.render3dfw.api.DepthMode;
import dev.melontrick.render3dfw.api.RenderState;
import dev.melontrick.render3dfw.api.RenderStyle;
import dev.melontrick.render3dfw.compile.CompiledShape;
import dev.melontrick.render3dfw.frame.RenderBatch;
import dev.melontrick.render3dfw.math.Vec3d;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

final class InstancedFlatColorRenderer implements AutoCloseable {
    private static final String DYNAMIC_TRANSFORMS = "DynamicTransforms";
    private static final String PROJECTION = "Projection";
    private static final String FOG = "Fog";
    private static final String GLOBALS = "Globals";
    private static final String INSTANCES = "Instances";
    private static final int INSTANCE_BYTES = 12;
    private static final int OFFSET_SCALE = 32;
    private static final int MINIMUM_INSTANCES_PER_GEOMETRY = 8;
    private static final long MAXIMUM_GEOMETRY_BYTES = 128L * 1024L * 1024L;
    private static final Vector4f WHITE = new Vector4f(1.0F, 1.0F, 1.0F, 1.0F);
    private static final Vector3f ZERO = new Vector3f();
    private static final Matrix4f IDENTITY = new Matrix4f();

    private final Map<PipelineKey, RenderPipeline> pipelines = new LinkedHashMap<>();
    private final Map<GeometryKey, GpuGeometry> geometries = new LinkedHashMap<>(64, 0.75F, true);
    private final ByteBufferBuilder geometryStaging = new ByteBufferBuilder(1 << 20);
    private final ByteBufferBuilder instanceStaging = new ByteBufferBuilder(1 << 20);
    private long geometryBytes;

    boolean drawLines(LevelRenderContext context, Vec3d camera, RenderBatch batch) {
        return draw(context, camera, batch, PrimitiveStream.LINES);
    }

    boolean drawTriangles(LevelRenderContext context, Vec3d camera, RenderBatch batch) {
        return draw(context, camera, batch, PrimitiveStream.TRIANGLES);
    }

    private boolean draw(LevelRenderContext context, Vec3d camera, RenderBatch batch, PrimitiveStream stream) {
        List<InstanceGroup> groups = groups(batch, stream);
        int instanceCount = groups.stream().mapToInt(InstanceGroup::size).sum();
        if (instanceCount == 0 || instanceCount < groups.size() * MINIMUM_INSTANCES_PER_GEOMETRY) {
            return false;
        }
        if (batch.state().blend() == BlendMode.ALPHA && groups.size() != 1) {
            return false;
        }
        for (InstanceGroup group : groups) {
            if (!canEncode(batch, group, camera)) {
                return false;
            }
        }
        for (InstanceGroup group : groups) {
            drawGroup(context, camera, batch, stream, group);
        }
        return true;
    }

    private List<InstanceGroup> groups(RenderBatch batch, PrimitiveStream stream) {
        Map<CompiledShape, InstanceGroup> byGeometry = new IdentityHashMap<>();
        List<InstanceGroup> result = new ArrayList<>();
        for (int instance = 0; instance < batch.size(); instance++) {
            boolean visible = stream == PrimitiveStream.LINES
                    ? batch.style(instance).shapeMode().lines()
                    : batch.style(instance).shapeMode().fill();
            if (!visible) {
                continue;
            }
            CompiledShape geometry = batch.geometry(instance);
            InstanceGroup group = byGeometry.get(geometry);
            if (group == null) {
                group = new InstanceGroup(geometry);
                byGeometry.put(geometry, group);
                result.add(group);
            }
            group.add(instance);
        }
        return result;
    }

    private static boolean canEncode(RenderBatch batch, InstanceGroup group, Vec3d camera) {
        double limit = (double) Short.MAX_VALUE / OFFSET_SCALE;
        for (int position = 0; position < group.size(); position++) {
            Vec3d translation = batch.translation(group.instance(position));
            if (Math.abs(translation.x() - camera.x()) > limit
                    || Math.abs(translation.y() - camera.y()) > limit
                    || Math.abs(translation.z() - camera.z()) > limit) {
                return false;
            }
        }
        return true;
    }

    private void drawGroup(
            LevelRenderContext context, Vec3d camera, RenderBatch batch, PrimitiveStream stream, InstanceGroup group) {
        GeometryKey geometryKey = new GeometryKey(group.geometry(), stream);
        GpuGeometry geometry = geometry(geometryKey);
        PipelineKey pipelineKey = new PipelineKey(batch.state(), stream);
        RenderPipeline pipeline = pipelines.computeIfAbsent(pipelineKey, InstancedFlatColorRenderer::createPipeline);

        instanceStaging.clear();
        try {
            for (int position = 0; position < group.size(); position++) {
                int instance = group.instance(position);
                writeInstance(batch.translation(instance), batch.style(instance), camera, stream);
            }
            try (ByteBufferBuilder.Result instances = instanceStaging.build()) {
                drawInstanced(context, pipeline, geometry, instances.byteBuffer(), group.size(), geometryKey);
            }
        } finally {
            instanceStaging.clear();
        }
    }

    private void drawInstanced(
            LevelRenderContext context,
            RenderPipeline pipeline,
            GpuGeometry geometry,
            ByteBuffer instanceData,
            int instanceCount,
            GeometryKey geometryKey) {
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        GpuBuffer instanceBuffer = geometry.ensureInstanceBuffer(instanceData.remaining(), geometryKey);
        encoder.writeToBuffer(instanceBuffer.slice(0L, instanceData.remaining()), instanceData);

        RenderSystem.AutoStorageIndexBuffer sequential = RenderSystem.getSequentialBuffer(geometry.mode());
        GpuBuffer indices = sequential.getBuffer(geometry.indexCount());
        VertexFormat.IndexType indexType = sequential.type();
        GpuBufferSlice transform = RenderSystem.getDynamicUniforms()
                .writeTransform(RenderSystem.getModelViewMatrix(), WHITE, ZERO, IDENTITY);
        RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
        GpuTextureView color = RenderSystem.outputColorTextureOverride != null
                ? RenderSystem.outputColorTextureOverride
                : target.getColorTextureView();
        GpuTextureView depth = RenderSystem.outputDepthTextureOverride != null
                ? RenderSystem.outputDepthTextureOverride
                : target.getDepthTextureView();
        try (RenderPass pass = encoder.createRenderPass(
                () -> "Render3DFW instanced batch", color, OptionalInt.empty(), depth, OptionalDouble.empty())) {
            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform(DYNAMIC_TRANSFORMS, transform);
            pass.setUniform(INSTANCES, instanceBuffer);
            pass.setVertexBuffer(0, geometry.vertices());
            pass.setIndexBuffer(indices, indexType);
            pass.drawIndexed(0, 0, geometry.indexCount(), instanceCount);
        }
    }

    private GpuGeometry geometry(GeometryKey key) {
        GpuGeometry cached = geometries.get(key);
        if (cached != null) {
            return cached;
        }
        GpuGeometry created = createGeometry(key);
        geometries.put(key, created);
        geometryBytes += created.geometryBytes();
        evictGeometry();
        return created;
    }

    private GpuGeometry createGeometry(GeometryKey key) {
        geometryStaging.clear();
        try {
            BufferBuilder builder;
            if (key.stream() == PrimitiveStream.LINES) {
                builder = new BufferBuilder(
                        geometryStaging, VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH);
                appendLines(builder, key.geometry());
            } else {
                builder = new BufferBuilder(
                        geometryStaging, VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
                appendTriangles(builder, key.geometry());
            }
            try (MeshData mesh = builder.buildOrThrow()) {
                ByteBuffer vertices = mesh.vertexBuffer();
                int bytes = vertices.remaining();
                GpuBuffer vertexBuffer = RenderSystem.getDevice()
                        .createBuffer(() -> "Render3DFW instanced geometry", GpuBuffer.USAGE_VERTEX, vertices);
                return new GpuGeometry(
                        vertexBuffer, mesh.drawState().mode(), mesh.drawState().indexCount(), bytes);
            }
        } finally {
            geometryStaging.clear();
        }
    }

    private static void appendLines(BufferBuilder builder, CompiledShape geometry) {
        for (int index = 0; index < geometry.lineIndexCount(); index += 2) {
            int first = geometry.lineIndex(index);
            int second = geometry.lineIndex(index + 1);
            float x1 = (float) geometry.x(first);
            float y1 = (float) geometry.y(first);
            float z1 = (float) geometry.z(first);
            float x2 = (float) geometry.x(second);
            float y2 = (float) geometry.y(second);
            float z2 = (float) geometry.z(second);
            float deltaX = x2 - x1;
            float deltaY = y2 - y1;
            float deltaZ = z2 - z1;
            float inverseLength = inverseLength(deltaX, deltaY, deltaZ);
            geometryVertex(builder, x1, y1, z1, deltaX * inverseLength, deltaY * inverseLength, deltaZ * inverseLength);
            geometryVertex(builder, x2, y2, z2, deltaX * inverseLength, deltaY * inverseLength, deltaZ * inverseLength);
        }
    }

    private static void geometryVertex(
            BufferBuilder builder, float x, float y, float z, float normalX, float normalY, float normalZ) {
        builder.addVertex(x, y, z)
                .setColor(-1)
                .setNormal(normalX, normalY, normalZ)
                .setLineWidth(1.0F);
    }

    private static void appendTriangles(BufferBuilder builder, CompiledShape geometry) {
        for (int index = 0; index < geometry.triangleIndexCount(); index++) {
            int vertex = geometry.triangleIndex(index);
            builder.addVertex((float) geometry.x(vertex), (float) geometry.y(vertex), (float) geometry.z(vertex))
                    .setColor(-1);
        }
    }

    private void writeInstance(Vec3d translation, RenderStyle style, Vec3d camera, PrimitiveStream stream) {
        int x = quantizedOffset(translation.x() - camera.x());
        int y = quantizedOffset(translation.y() - camera.y());
        int z = quantizedOffset(translation.z() - camera.z());
        int color = stream == PrimitiveStream.LINES
                ? style.lineColor().argb()
                : style.fillColor().argb();
        int width = Math.clamp(Math.round(style.lineWidth() * 16.0F), 0, 0xFFFF);
        long address = instanceStaging.reserve(INSTANCE_BYTES);
        putShort(address, x);
        putShort(address + 2L, y);
        putShort(address + 4L, z);
        MemoryUtil.memPutByte(address + 6L, (byte) (color >>> 16));
        MemoryUtil.memPutByte(address + 7L, (byte) (color >>> 8));
        MemoryUtil.memPutByte(address + 8L, (byte) color);
        MemoryUtil.memPutByte(address + 9L, (byte) (color >>> 24));
        putShort(address + 10L, width);
    }

    private static int quantizedOffset(double value) {
        return Math.clamp((int) Math.round(value * OFFSET_SCALE), Short.MIN_VALUE, Short.MAX_VALUE);
    }

    private static void putShort(long address, int value) {
        MemoryUtil.memPutByte(address, (byte) value);
        MemoryUtil.memPutByte(address + 1L, (byte) (value >>> 8));
    }

    private void evictGeometry() {
        var iterator = geometries.entrySet().iterator();
        while (geometryBytes > MAXIMUM_GEOMETRY_BYTES && geometries.size() > 1 && iterator.hasNext()) {
            Map.Entry<GeometryKey, GpuGeometry> eldest = iterator.next();
            geometryBytes -= eldest.getValue().geometryBytes();
            eldest.getValue().close();
            iterator.remove();
        }
    }

    private static RenderPipeline createPipeline(PipelineKey key) {
        boolean lines = key.stream() == PrimitiveStream.LINES;
        Identifier vertexShader = Identifier.fromNamespaceAndPath(
                "render3dfw", lines ? "core/flat_color_line_instanced" : "core/flat_color_instanced");
        RenderPipeline.Builder builder = RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath("render3dfw", key.path()))
                .withUniform(DYNAMIC_TRANSFORMS, UniformType.UNIFORM_BUFFER)
                .withUniform(PROJECTION, UniformType.UNIFORM_BUFFER)
                .withUniform(INSTANCES, UniformType.TEXEL_BUFFER, TextureFormat.RGBA8)
                .withVertexShader(vertexShader)
                .withFragmentShader(lines ? "core/rendertype_lines" : "core/position_color")
                .withVertexFormat(
                        lines
                                ? DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH
                                : DefaultVertexFormat.POSITION_COLOR,
                        lines ? VertexFormat.Mode.LINES : VertexFormat.Mode.TRIANGLES)
                .withCull(key.state().cull() == CullMode.BACK)
                .withDepthStencilState(depthState(key.state().depth()));
        if (lines) {
            builder.withUniform(FOG, UniformType.UNIFORM_BUFFER).withUniform(GLOBALS, UniformType.UNIFORM_BUFFER);
        }
        if (key.state().blend() == BlendMode.ALPHA) {
            builder.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT));
        }
        return builder.build();
    }

    private static DepthStencilState depthState(DepthMode mode) {
        return switch (mode) {
            case TESTED_WRITE -> new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true);
            case TESTED_READ_ONLY -> new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false);
            case ALWAYS_VISIBLE -> new DepthStencilState(CompareOp.ALWAYS_PASS, false);
        };
    }

    private static float inverseLength(float x, float y, float z) {
        float squared = x * x + y * y + z * z;
        return squared > 1.0E-12F ? (float) (1.0 / Math.sqrt(squared)) : 0.0F;
    }

    @Override
    public void close() {
        geometries.values().forEach(GpuGeometry::close);
        geometries.clear();
        geometryStaging.close();
        instanceStaging.close();
    }

    private enum PrimitiveStream {
        LINES,
        TRIANGLES
    }

    private record PipelineKey(RenderState state, PrimitiveStream stream) {
        private String path() {
            return "pipeline/flat_color_instanced/" + stream.name().toLowerCase() + "/"
                    + state.depth().name().toLowerCase() + "/"
                    + state.blend().name().toLowerCase() + "/"
                    + state.cull().name().toLowerCase();
        }
    }

    private record GeometryKey(CompiledShape geometry, PrimitiveStream stream) {}

    private static final class InstanceGroup {
        private final CompiledShape geometry;
        private int[] instances = new int[16];
        private int size;

        private InstanceGroup(CompiledShape geometry) {
            this.geometry = geometry;
        }

        private void add(int instance) {
            if (size == instances.length) {
                instances = Arrays.copyOf(instances, instances.length << 1);
            }
            instances[size++] = instance;
        }

        private CompiledShape geometry() {
            return geometry;
        }

        private int instance(int index) {
            return instances[index];
        }

        private int size() {
            return size;
        }
    }

    private static final class GpuGeometry implements AutoCloseable {
        private final GpuBuffer vertices;
        private final VertexFormat.Mode mode;
        private final int indexCount;
        private final long geometryBytes;
        private GpuBuffer instances;

        private GpuGeometry(GpuBuffer vertices, VertexFormat.Mode mode, int indexCount, long geometryBytes) {
            this.vertices = vertices;
            this.mode = mode;
            this.indexCount = indexCount;
            this.geometryBytes = geometryBytes;
        }

        private GpuBuffer ensureInstanceBuffer(int requiredBytes, GeometryKey key) {
            if (instances != null && !instances.isClosed() && instances.size() >= requiredBytes) {
                return instances;
            }
            if (instances != null && !instances.isClosed()) {
                instances.close();
            }
            long capacity = bufferCapacity(requiredBytes);
            instances = RenderSystem.getDevice()
                    .createBuffer(
                            () -> "Render3DFW instances " + key.stream().name().toLowerCase(),
                            GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER | GpuBuffer.USAGE_COPY_DST,
                            capacity);
            return instances;
        }

        private static long bufferCapacity(int requiredBytes) {
            long capacity = 4_096L;
            while (capacity < requiredBytes) {
                capacity <<= 1;
            }
            return capacity;
        }

        private GpuBuffer vertices() {
            return vertices;
        }

        private VertexFormat.Mode mode() {
            return mode;
        }

        private int indexCount() {
            return indexCount;
        }

        private long geometryBytes() {
            return geometryBytes;
        }

        @Override
        public void close() {
            if (!vertices.isClosed()) {
                vertices.close();
            }
            if (instances != null && !instances.isClosed()) {
                instances.close();
            }
        }
    }
}
