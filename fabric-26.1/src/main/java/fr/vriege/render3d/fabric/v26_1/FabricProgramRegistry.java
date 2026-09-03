package fr.vriege.render3d.fabric.v26_1;

import fr.vriege.render3d.api.ProgramId;
import fr.vriege.render3d.frame.RenderBatch;
import fr.vriege.render3d.math.Vec3d;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;

/** Routes backend-independent program ids to Minecraft 26.1 implementations. */
public final class FabricProgramRegistry {
    private final Map<ProgramId, FabricProgram> programs = new ConcurrentHashMap<>();

    public static FabricProgramRegistry createDefault() {
        FabricProgramRegistry registry = new FabricProgramRegistry();
        registry.register(ProgramId.FLAT_COLOR, new MeteorFlatColorProgram());
        ServiceLoader.load(FabricProgramProvider.class).forEach(provider -> provider.registerPrograms(registry));
        return registry;
    }

    public void register(ProgramId id, FabricProgram program) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(program, "program");
        if (programs.putIfAbsent(id, program) != null) {
            throw new IllegalStateException("a Fabric program is already registered for " + id.value());
        }
    }

    public void draw(LevelRenderContext context, Vec3d cameraPosition, RenderBatch batch) {
        FabricProgram program = programs.get(batch.state().program());
        if (program == null) {
            throw new IllegalStateException("no Minecraft 26.1 program registered for "
                    + batch.state().program().value());
        }
        program.draw(context, cameraPosition, batch);
    }
}
