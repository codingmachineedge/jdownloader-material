package org.jdownloader.material.appearance;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/** Sparse, typed style override. Missing values inherit from the target/global profile. */
public final class AppearanceStyle {

    private final EnumMap<AppearanceProperty, String> values = new EnumMap<>(AppearanceProperty.class);
    private final LinkedHashMap<String, String> unsupported = new LinkedHashMap<>();

    public AppearanceStyle() {
    }

    private AppearanceStyle(AppearanceStyle source) {
        values.putAll(source.values);
        unsupported.putAll(source.unsupported);
    }

    public AppearanceStyle copy() {
        return new AppearanceStyle(this);
    }

    public AppearanceStyle set(AppearanceProperty property, String value) {
        Objects.requireNonNull(property, "property");
        if (value == null || value.isBlank()) values.remove(property);
        else values.put(property, property.normalize(value));
        return this;
    }

    public AppearanceStyle set(AppearanceProperty property, double value) {
        return set(property, Double.toString(value));
    }

    public AppearanceStyle set(AppearanceProperty property, int value) {
        return set(property, Integer.toString(value));
    }

    public AppearanceStyle set(AppearanceProperty property, boolean value) {
        return set(property, Boolean.toString(value));
    }

    public AppearanceStyle set(AppearanceProperty property, ColorValue value) {
        Objects.requireNonNull(value, "value");
        return set(property, value.toStorageString());
    }

    public AppearanceStyle retainUnsupported(String propertyId, String value) {
        String id = Objects.requireNonNull(propertyId, "propertyId").trim();
        String stored = Objects.requireNonNullElse(value, "");
        if (id.isEmpty() || id.length() > 240 || stored.length() > 8_192) {
            throw new IllegalArgumentException("Unsupported appearance property exceeds storage bounds");
        }
        unsupported.put(id, stored);
        return this;
    }

    public Optional<String> get(AppearanceProperty property) {
        return Optional.ofNullable(values.get(property));
    }

    public OptionalDouble number(AppearanceProperty property) {
        String value = values.get(property);
        return value == null ? OptionalDouble.empty() : OptionalDouble.of(Double.parseDouble(value));
    }

    public boolean booleanValue(AppearanceProperty property, boolean fallback) {
        String value = values.get(property);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }

    public Optional<ColorValue> color(AppearanceProperty property) {
        String value = values.get(property);
        return value == null ? Optional.empty() : Optional.of(ColorValue.fromStorageString(value));
    }

    public void reset(AppearanceProperty property) {
        values.remove(Objects.requireNonNull(property, "property"));
    }

    public void resetUnsupported(String propertyId) {
        unsupported.remove(propertyId);
    }

    public void clear() {
        values.clear();
        unsupported.clear();
    }

    public boolean isEmpty() {
        return values.isEmpty() && unsupported.isEmpty();
    }

    public Map<AppearanceProperty, String> values() {
        return Collections.unmodifiableMap(values);
    }

    public Map<String, String> unsupportedValues() {
        return Collections.unmodifiableMap(unsupported);
    }

    /** Returns an inherited copy with this sparse override applied last. */
    public AppearanceStyle over(AppearanceStyle inherited) {
        AppearanceStyle result = inherited == null ? new AppearanceStyle() : inherited.copy();
        result.values.putAll(values);
        result.unsupported.putAll(unsupported);
        return result;
    }
}
