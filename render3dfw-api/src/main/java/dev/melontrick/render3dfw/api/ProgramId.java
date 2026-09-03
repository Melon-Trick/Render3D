package dev.melontrick.render3dfw.api;

import java.util.Objects;

/** Stable backend-independent identifier for a shader program or material pipeline. */
public record ProgramId(String value) implements Comparable<ProgramId> {
    public static final ProgramId FLAT_COLOR = new ProgramId("render3dfw:flat_color");

    public ProgramId {
        Objects.requireNonNull(value, "value");
        if (!value.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+")) {
            throw new IllegalArgumentException("program id must use namespace:path syntax");
        }
    }

    @Override
    public int compareTo(ProgramId other) {
        return value.compareTo(other.value);
    }
}
