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
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.melontrick.render3dfw.api.BlendMode;
import dev.melontrick.render3dfw.api.CullMode;
import dev.melontrick.render3dfw.api.DepthMode;
import dev.melontrick.render3dfw.api.RenderState;
import dev.melontrick.render3dfw.compile.CompiledShape;
import dev.melontrick.render3dfw.frame.RenderBatch;
import dev.melontrick.render3dfw.math.Vec3d;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

final class FlatColorProgram implements FabricProgram {
    private static final String DYNAMIC_TRANSFORMS = "DynamicTransforms";
    private static final String PROJECTION = "Projection";
    private static final String FOG = "Fog";
    private static final String GLOBALS = "Globals";
    private static final Vector4f WHITE = new Vector4f(1.0F, 1.0F, 1.0F, 1.0F);
    private static final Vector3f ZERO = new Vector3f();
    private static final Matrix4f IDENTITY = new Matrix4f();
    private static final int INITIAL_STAGING_BYTES = 1 << 20;

    private final Map<PipelineKey, RenderPipeline> pipelines = new ConcurrentHashMap<>();
    private final Map<BufferKey, GpuBuffer> gpuBuffers = new ConcurrentHashMap<>();
    private final ByteBufferBuilder lineStaging = new ByteBufferBuilder(INITIAL_STAGING_BYTES);
    private final ByteBufferBuilder triangleStaging = new ByteBufferBuilder(INITIAL_STAGING_BYTES);
    private final InstancedFlatColorRenderer instanced = new InstancedFlatColorRenderer();

    @Override
    public void draw(LevelRenderContext context, Vec3d cameraPosition, RenderBatch batch) {
        int lineVertices = countLineVertices(batch);
        if (lineVertices > 0 && !instanced.drawLines(context, cameraPosition, batch)) {
            drawLines(context, cameraPosition, batch);
        }
        int triangleVertices = countTriangleVertices(batch);
        if (triangleVertices > 0 && !instanced.drawTriangles(context, cameraPosition, batch)) {
            drawTriangles(context, cameraPosition, batch);
        }
    }

    private void drawLines(LevelRenderContext context, Vec3d camera, RenderBatch batch) {
        lineStaging.clear();
        try {
            BufferBuilder builder = new BufferBuilder(
                    lineStaging, VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH);
            for (int instance = 0; instance < batch.size(); instance++) {
                if (!batch.style(instance).shapeMode().lines()) {
                    continue;
                }
                CompiledShape geometry = batch.geometry(instance);
                for (int index = 0; index < geometry.lineIndexCount(); index += 2) {
                    int first = geometry.lineIndex(index);
                    int second = geometry.lineIndex(index + 1);
                    float x1 = relativeX(geometry, first, batch, instance, camera);
                    float y1 = relativeY(geometry, first, batch, instance, camera);
                    float z1 = relativeZ(geometry, first, batch, instance, camera);
                    float x2 = relativeX(geometry, second, batch, instance, camera);
                    float y2 = relativeY(geometry, second, batch, instance, camera);
                    float z2 = relativeZ(geometry, second, batch, instance, camera);
                    float dx = x2 - x1;
                    float dy = y2 - y1;
                    float dz = z2 - z1;
                    float inverseLength = inverseLength(dx, dy, dz);
                    vertex(
                            builder,
                            x1,
                            y1,
                            z1,
                            batch,
                            instance,
                            dx * inverseLength,
                            dy * inverseLength,
                            dz * inverseLength);
                    vertex(
                            builder,
                            x2,
                            y2,
                            z2,
                            batch,
                            instance,
                            dx * inverseLength,
                            dy * inverseLength,
                            dz * inverseLength);
                }
            }
            try (MeshData mesh = builder.buildOrThrow()) {
                PipelineKey key = new PipelineKey(batch.state(), PrimitiveStream.LINES);
                drawMesh(context, mesh, key);
            }
        } finally {
            lineStaging.clear();
        }
    }

    private void drawTriangles(LevelRenderContext context, Vec3d camera, RenderBatch batch) {
        triangleStaging.clear();
        try {
            BufferBuilder builder =
                    new BufferBuilder(triangleStaging, VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
            for (int instance = 0; instance < batch.size(); instance++) {
                if (!batch.style(instance).shapeMode().fill()) {
                    continue;
                }
                CompiledShape geometry = batch.geometry(instance);
                for (int index = 0; index < geometry.triangleIndexCount(); index++) {
                    int vertex = geometry.triangleIndex(index);
                    builder.addVertex(
                                    relativeX(geometry, vertex, batch, instance, camera),
                                    relativeY(geometry, vertex, batch, instance, camera),
                                    relativeZ(geometry, vertex, batch, instance, camera))
                            .setColor(batch.style(instance).fillColor().argb());
                }
            }
            try (MeshData mesh = builder.buildOrThrow()) {
                PipelineKey key = new PipelineKey(batch.state(), PrimitiveStream.TRIANGLES);
                drawMesh(context, mesh, key);
            }
        } finally {
            triangleStaging.clear();
        }
    }

    private static void vertex(
            VertexConsumer output,
            float x,
            float y,
            float z,
            RenderBatch batch,
            int instance,
            float normalX,
            float normalY,
            float normalZ) {
        output.addVertex(x, y, z)
                .setColor(batch.style(instance).lineColor().argb())
                .setNormal(normalX, normalY, normalZ)
                .setLineWidth(batch.style(instance).lineWidth());
    }

    @Override
    public void close() {
        gpuBuffers.values().forEach(buffer -> {
            if (!buffer.isClosed()) {
                buffer.close();
            }
        });
        gpuBuffers.clear();
        instanced.close();
        lineStaging.close();
        triangleStaging.close();
    }

    private void drawMesh(LevelRenderContext context, MeshData mesh, PipelineKey key) {
        RenderPipeline pipeline = pipeline(key);
        MeshData.DrawState drawState = mesh.drawState();
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        GpuBuffer vertices = upload(encoder, new BufferKey(key, false), mesh.vertexBuffer(), GpuBuffer.USAGE_VERTEX);
        GpuBuffer indices;
        VertexFormat.IndexType indexType;
        if (mesh.indexBuffer() == null) {
            RenderSystem.AutoStorageIndexBuffer sequential = RenderSystem.getSequentialBuffer(drawState.mode());
            indices = sequential.getBuffer(drawState.indexCount());
            indexType = sequential.type();
        } else {
            indices = upload(encoder, new BufferKey(key, true), mesh.indexBuffer(), GpuBuffer.USAGE_INDEX);
            indexType = drawState.indexType();
        }

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
                () -> "Render3D direct batch", color, OptionalInt.empty(), depth, OptionalDouble.empty())) {
            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform(DYNAMIC_TRANSFORMS, transform);
            pass.setVertexBuffer(0, vertices);
            pass.setIndexBuffer(indices, indexType);
            pass.drawIndexed(0, 0, drawState.indexCount(), 1);
        }
    }

    private RenderPipeline pipeline(PipelineKey key) {
        return pipelines.computeIfAbsent(key, FlatColorProgram::createPipeline);
    }

    private GpuBuffer upload(CommandEncoder encoder, BufferKey key, ByteBuffer data, int usage) {
        int requiredBytes = data.remaining();
        GpuBuffer buffer = gpuBuffers.compute(key, (ignored, existing) -> {
            if (existing != null && !existing.isClosed() && existing.size() >= requiredBytes) {
                return existing;
            }
            if (existing != null && !existing.isClosed()) {
                existing.close();
            }
            long capacity = bufferCapacity(requiredBytes);
            return RenderSystem.getDevice()
                    .createBuffer(() -> "Render3DFW " + key.path(), usage | GpuBuffer.USAGE_COPY_DST, capacity);
        });
        encoder.writeToBuffer(buffer.slice(0L, requiredBytes), data);
        return buffer;
    }

    private static long bufferCapacity(int requiredBytes) {
        long capacity = 1L;
        while (capacity < requiredBytes) {
            capacity <<= 1;
        }
        return Math.max(4_096L, capacity);
    }

    private static RenderPipeline createPipeline(PipelineKey key) {
        boolean lines = key.stream() == PrimitiveStream.LINES;
        RenderPipeline.Builder builder = RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath("render3dfw", key.path()))
                .withUniform(DYNAMIC_TRANSFORMS, UniformType.UNIFORM_BUFFER)
                .withUniform(PROJECTION, UniformType.UNIFORM_BUFFER)
                .withVertexShader(lines ? "core/rendertype_lines" : "core/position_color")
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

    private static int countLineVertices(RenderBatch batch) {
        int count = 0;
        for (int instance = 0; instance < batch.size(); instance++) {
            if (batch.style(instance).shapeMode().lines()) {
                count = Math.addExact(count, batch.geometry(instance).lineIndexCount());
            }
        }
        return count;
    }

    private static int countTriangleVertices(RenderBatch batch) {
        int count = 0;
        for (int instance = 0; instance < batch.size(); instance++) {
            if (batch.style(instance).shapeMode().fill()) {
                count = Math.addExact(count, batch.geometry(instance).triangleIndexCount());
            }
        }
        return count;
    }

    private static float relativeX(CompiledShape shape, int vertex, RenderBatch batch, int instance, Vec3d camera) {
        return (float) (shape.x(vertex) + batch.translation(instance).x() - camera.x());
    }

    private static float relativeY(CompiledShape shape, int vertex, RenderBatch batch, int instance, Vec3d camera) {
        return (float) (shape.y(vertex) + batch.translation(instance).y() - camera.y());
    }

    private static float relativeZ(CompiledShape shape, int vertex, RenderBatch batch, int instance, Vec3d camera) {
        return (float) (shape.z(vertex) + batch.translation(instance).z() - camera.z());
    }

    private static float inverseLength(float x, float y, float z) {
        float squared = x * x + y * y + z * z;
        return squared > 1.0E-12F ? (float) (1.0 / Math.sqrt(squared)) : 0.0F;
    }

    private enum PrimitiveStream {
        LINES,
        TRIANGLES
    }

    private record PipelineKey(RenderState state, PrimitiveStream stream) {
        private String path() {
            return "pipeline/flat_color/" + stream.name().toLowerCase() + "/"
                    + state.depth().name().toLowerCase() + "/"
                    + state.blend().name().toLowerCase() + "/"
                    + state.cull().name().toLowerCase();
        }
    }

    private record BufferKey(PipelineKey pipeline, boolean indices) {
        private String path() {
            return pipeline.path() + (indices ? "/indices" : "/vertices");
        }
    }
}
