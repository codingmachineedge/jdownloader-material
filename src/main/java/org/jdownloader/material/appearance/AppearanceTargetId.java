package org.jdownloader.material.appearance;

import java.util.Locale;
import java.util.Objects;

/** Stable, persistence-safe identifier for one rendered element or logical appearance target. */
public record AppearanceTargetId(String value) implements Comparable<AppearanceTargetId> {

    public static final int MAX_LENGTH = 240;

    public AppearanceTargetId {
        value = Objects.requireNonNull(value, "value").trim();
        if (value.isEmpty() || value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Appearance target id must contain 1-" + MAX_LENGTH + " characters");
        }
        if (!value.matches("[\\p{L}\\p{N}._-]+")) {
            throw new IllegalArgumentException(
                    "Appearance target id may contain only letters, numbers, dots, underscores, and hyphens");
        }
    }

    public static AppearanceTargetId of(String value) {
        return new AppearanceTargetId(value);
    }

    /** Creates a readable deterministic id segment for generated structural targets. */
    public static String segment(String value) {
        String normalized = Objects.requireNonNullElse(value, "node").trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}._-]+", "-")
                .replaceAll("^-+|-+$", "");
        return normalized.isEmpty() ? "node" : normalized.substring(0, Math.min(64, normalized.length()));
    }

    @Override
    public int compareTo(AppearanceTargetId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
