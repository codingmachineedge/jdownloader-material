package org.jdownloader.material.integration.jdownloader;

import java.util.Collection;
import java.util.Objects;

/** Minimal JSON value encoding for positional API parameters; not a response parser. */
public final class RemoteJson {
    private RemoteJson() { }

    public static String string(String value) {
        Objects.requireNonNull(value, "value");
        return quote(value.toCharArray());
    }

    static String secret(char[] value) {
        Objects.requireNonNull(value, "value");
        return quote(value);
    }

    public static String number(long value) { return Long.toString(value); }
    public static String bool(boolean value) { return Boolean.toString(value); }

    public static String longs(long[] values) {
        if (values == null || values.length == 0) return "[]";
        StringBuilder json = new StringBuilder(values.length * 8).append('[');
        for (int index = 0; index < values.length; index++) {
            if (index > 0) json.append(',');
            json.append(values[index]);
        }
        return json.append(']').toString();
    }

    public static String strings(Collection<String> values) {
        if (values == null || values.isEmpty()) return "[]";
        StringBuilder json = new StringBuilder().append('[');
        boolean first = true;
        for (String value : values) {
            if (!first) json.append(',');
            first = false;
            json.append(string(Objects.requireNonNull(value, "value")));
        }
        return json.append(']').toString();
    }

    /** Accepts a caller-owned JSON object or array without pretending to parse it. */
    public static String structured(String json) {
        String value = Objects.requireNonNull(json, "json").strip();
        if (!(value.startsWith("{") && value.endsWith("}"))
                && !(value.startsWith("[") && value.endsWith("]"))) {
            throw new IllegalArgumentException("Expected a JSON object or array");
        }
        return value;
    }

    private static String quote(char[] value) {
        StringBuilder json = new StringBuilder(value.length + 2).append('"');
        for (char character : value) {
            switch (character) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\b' -> json.append("\\b");
                case '\f' -> json.append("\\f");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (character < 0x20) json.append(String.format("\\u%04X", (int) character));
                    else json.append(character);
                }
            }
        }
        return json.append('"').toString();
    }
}
