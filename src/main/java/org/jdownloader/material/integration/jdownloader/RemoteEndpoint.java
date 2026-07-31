package org.jdownloader.material.integration.jdownloader;

import java.util.Objects;
import java.util.regex.Pattern;

/** A validated API path with no authority, query, traversal, or encoded aliases. */
public record RemoteEndpoint(String path) {

    private static final Pattern SAFE_PATH = Pattern.compile("^/(?:[A-Za-z0-9._~-]+/)*[A-Za-z0-9._~-]+$");

    public RemoteEndpoint {
        path = Objects.requireNonNull(path, "path").strip();
        if (path.length() > 256 || !SAFE_PATH.matcher(path).matches()
                || path.contains("/../") || path.endsWith("/..")
                || path.contains("/./") || path.endsWith("/.")) {
            throw new IllegalArgumentException("Remote endpoint must be a simple absolute API path");
        }
    }

    public static RemoteEndpoint of(String path) {
        return new RemoteEndpoint(path);
    }

    @Override
    public String toString() {
        return path;
    }
}
