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
import dev.melontrick.render3dfw.frame.DrawInstance;
import dev.melontrick.render3dfw.frame.RenderBatch;
import dev.melontrick.render3dfw.math.Vec3d;
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

/**
 * Direct batched GPU renderer using one line stream and one triangle stream per state bucket,
 * followed by at most two indexed draws.
 */
final class FlatColorProgram implements FabricProgram {
    private static final String DYNAMIC_TRANSFORMS = "DynamicTransforms";
    private static final String PROJECTION = "Projection";
    private static final String FOG = "Fog";
    private static final String GLOBALS = "Globals";
    private static final Vector4f WHITE = new Vector4f(1.0F, 1.0F, 1.0F, 1.0F);
    private static final Vector3f ZERO = new Vector3f();
    private static final Matrix4f IDENTITY = new Matrix4f();

    private final Map<PipelineKey, RenderPipeline> pipelines = new ConcurrentHashMap<>();

    @Override
    public void draw(LevelRenderContext context, Vec3d cameraPosition, RenderBatch batch) {
        int lineVertices = countLineVertices(batch);
        if (lineVertices > 0) {
            drawLines(context, cameraPosition, batch, lineVertices);
        }
        int triangleVertices = countTriangleVertices(batch);
        if (triangleVertices > 0) {
            drawTriangles(context, cameraPosition, batch, triangleVertices);
        }
    }

    private void drawLines(LevelRenderContext context, Vec3d camera, RenderBatch batch, int vertexCount) {
        int byteCount =
                Math.multiplyExact(vertexCount, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH.getVertexSize());
        try (ByteBufferBuilder memory = ByteBufferBuilder.exactlySized(byteCount)) {
            BufferBuilder builder = new BufferBuilder(
                    memory, VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH);
            for (DrawInstance instance : batch.instances()) {
                if (!instance.style().shapeMode().lines()) {
                    continue;
                }
                CompiledShape geometry = instance.geometry();
                for (int index = 0; index < geometry.lineIndexCount(); index += 2) {
                    int first = geometry.lineIndex(index);
                    int second = geometry.lineIndex(index + 1);
                    float x1 = relativeX(geometry, first, instance, camera);
                    float y1 = relativeY(geometry, first, instance, camera);
                    float z1 = relativeZ(geometry, first, instance, camera);
                    float x2 = relativeX(geometry, second, instance, camera);
                    float y2 = relativeY(geometry, second, instance, camera);
                    float z2 = relativeZ(geometry, second, instance, camera);
                    float dx = x2 - x1;
                    float dy = y2 - y1;
                    float dz = z2 - z1;
                    float inverseLength = inverseLength(dx, dy, dz);
                    vertex(builder, x1, y1, z1, instance, dx * inverseLength, dy * inverseLength, dz * inverseLength);
                    vertex(builder, x2, y2, z2, instance, dx * inverseLength, dy * inverseLength, dz * inverseLength);
                }
            }
            try (MeshData mesh = builder.buildOrThrow()) {
                drawMesh(context, mesh, pipeline(batch.state(), PrimitiveStream.LINES));
            }
        }
    }

    private void drawTriangles(LevelRenderContext context, Vec3d camera, RenderBatch batch, int vertexCount) {
        int byteCount = Math.multiplyExact(vertexCount, DefaultVertexFormat.POSITION_COLOR.getVertexSize());
        try (ByteBufferBuilder memory = ByteBufferBuilder.exactlySized(byteCount)) {
            BufferBuilder builder =
                    new BufferBuilder(memory, VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
            for (DrawInstance instance : batch.instances()) {
                if (!instance.style().shapeMode().fill()) {
                    continue;
                }
                CompiledShape geometry = instance.geometry();
                for (int index = 0; index < geometry.triangleIndexCount(); index++) {
                    int vertex = geometry.triangleIndex(index);
                    builder.addVertex(
                                    relativeX(geometry, vertex, instance, camera),
                                    relativeY(geometry, vertex, instance, camera),
                                    relativeZ(geometry, vertex, instance, camera))
                            .setColor(instance.style().fillColor().argb());
                }
            }
            try (MeshData mesh = builder.buildOrThrow()) {
                drawMesh(context, mesh, pipeline(batch.state(), PrimitiveStream.TRIANGLES));
            }
        }
    }

    private static void vertex(
            VertexConsumer output,
            float x,
            float y,
            float z,
            DrawInstance instance,
            float normalX,
            float normalY,
            float normalZ) {
        output.addVertex(x, y, z)
                .setColor(instance.style().lineColor().argb())
                .setNormal(normalX, normalY, normalZ)
                .setLineWidth(instance.style().lineWidth());
    }

    private static void drawMesh(LevelRenderContext context, MeshData mesh, RenderPipeline pipeline) {
        MeshData.DrawState drawState = mesh.drawState();
        GpuBuffer vertices = pipeline.getVertexFormat().uploadImmediateVertexBuffer(mesh.vertexBuffer());
        GpuBuffer indices;
        VertexFormat.IndexType indexType;
        if (mesh.indexBuffer() == null) {
            RenderSystem.AutoStorageIndexBuffer sequential = RenderSystem.getSequentialBuffer(drawState.mode());
            indices = sequential.getBuffer(drawState.indexCount());
            indexType = sequential.type();
        } else {
            indices = pipeline.getVertexFormat().uploadImmediateIndexBuffer(mesh.indexBuffer());
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
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
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

    private RenderPipeline pipeline(RenderState state, PrimitiveStream stream) {
        return pipelines.computeIfAbsent(new PipelineKey(state, stream), FlatColorProgram::createPipeline);
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
        for (DrawInstance instance : batch.instances()) {
            if (instance.style().shapeMode().lines()) {
                count = Math.addExact(count, instance.geometry().lineIndexCount());
            }
        }
        return count;
    }

    private static int countTriangleVertices(RenderBatch batch) {
        int count = 0;
        for (DrawInstance instance : batch.instances()) {
            if (instance.style().shapeMode().fill()) {
                count = Math.addExact(count, instance.geometry().triangleIndexCount());
            }
        }
        return count;
    }

    private static float relativeX(CompiledShape shape, int vertex, DrawInstance instance, Vec3d camera) {
        return (float) (shape.x(vertex) + instance.translation().x() - camera.x());
    }

    private static float relativeY(CompiledShape shape, int vertex, DrawInstance instance, Vec3d camera) {
        return (float) (shape.y(vertex) + instance.translation().y() - camera.y());
    }

    private static float relativeZ(CompiledShape shape, int vertex, DrawInstance instance, Vec3d camera) {
        return (float) (shape.z(vertex) + instance.translation().z() - camera.z());
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
}
