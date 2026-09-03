package dev.melontrick.render3dfw.scene;

import dev.melontrick.render3dfw.api.RenderCommand;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class RenderScene {
    private final AtomicReference<SceneSnapshot> snapshot = new AtomicReference<>(SceneSnapshot.empty());

    public List<RenderCommand> snapshot() {
        return snapshot.get().commands();
    }

    public SceneSnapshot indexedSnapshot() {
        return snapshot.get();
    }

    public void publish(SceneSnapshot preparedSnapshot) {
        snapshot.set(Objects.requireNonNull(preparedSnapshot, "preparedSnapshot"));
    }

    public void replace(Collection<RenderCommand> commands) {
        Objects.requireNonNull(commands, "commands");
        snapshot.set(SceneSnapshot.of(commands));
    }

    public void update(Consumer<List<RenderCommand>> editor) {
        Objects.requireNonNull(editor, "editor");
        List<RenderCommand> mutable = new ArrayList<>(snapshot.get().commands());
        editor.accept(mutable);
        snapshot.set(SceneSnapshot.of(mutable));
    }

    public void clear() {
        snapshot.set(SceneSnapshot.empty());
    }
}
