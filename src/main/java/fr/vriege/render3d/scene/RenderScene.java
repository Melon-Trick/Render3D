package fr.vriege.render3d.scene;

import fr.vriege.render3d.api.RenderCommand;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Lock-free read scene. Writers publish a complete immutable snapshot atomically. */
public final class RenderScene {
    private final AtomicReference<List<RenderCommand>> snapshot = new AtomicReference<>(List.of());

    public List<RenderCommand> snapshot() {
        return snapshot.get();
    }

    public void replace(Collection<RenderCommand> commands) {
        Objects.requireNonNull(commands, "commands");
        snapshot.set(List.copyOf(commands));
    }

    public void update(Consumer<List<RenderCommand>> editor) {
        Objects.requireNonNull(editor, "editor");
        List<RenderCommand> mutable = new ArrayList<>(snapshot.get());
        editor.accept(mutable);
        snapshot.set(List.copyOf(mutable));
    }

    public void clear() {
        snapshot.set(List.of());
    }
}
