package org.jdownloader.material.engine.history;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.TreeMap;

/**
 * Immutable, deterministic state captured by the history system. Each snapshot
 * deliberately has one UTF-8 file for settings, Downloads, and LinkGrabber;
 * that makes Git diffs small and keeps the two list views independently
 * inspectable even though one action commits all durable state.
 */
public final class HistorySnapshot {

    public static final String SETTINGS_FILE = "settings.properties";
    public static final String DOWNLOADS_FILE = "downloads.properties";
    public static final String LINKGRABBER_FILE = "linkgrabber.properties";

    private static final byte[] EMPTY = new byte[0];

    private final byte[] settings;
    private final byte[] downloads;
    private final byte[] linkGrabber;

    private HistorySnapshot(byte[] settings, byte[] downloads, byte[] linkGrabber) {
        this.settings = copy(settings);
        this.downloads = copy(downloads);
        this.linkGrabber = copy(linkGrabber);
    }

    public static HistorySnapshot empty() {
        return new HistorySnapshot(EMPTY, EMPTY, EMPTY);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a snapshot from separately maintained properties. Credential
     * fields are always removed from the settings file before it reaches the
     * Git object store.
     */
    public static HistorySnapshot fromProperties(Properties settings, Properties downloads,
                                                 Properties linkGrabber) {
        return new HistorySnapshot(
                canonicalProperties(settings, true),
                canonicalProperties(downloads, false),
                canonicalProperties(linkGrabber, false));
    }

    /**
     * Splits the current AppStateStore-style properties into stable history
     * files. Queue and LinkGrabber prefixes are retained with their respective
     * list; all other values become the settings snapshot.
     */
    public static HistorySnapshot fromState(Properties state) {
        Properties safeState = state == null ? new Properties() : state;
        Properties settings = new Properties();
        Properties downloads = new Properties();
        Properties linkGrabber = new Properties();
        for (Map.Entry<Object, Object> entry : safeState.entrySet()) {
            String key = String.valueOf(entry.getKey());
            String value = String.valueOf(entry.getValue());
            if (key.startsWith("queue.")) {
                // Transfer telemetry belongs to the live engine and its .part
                // file, not to a list/settings revision. Durable queue intent
                // (URL, name, destination, enabled state, priority and state)
                // remains available for undo and restore. The terminal-only
                // metadata that makes a finished/error row intelligible is
                // selectively retained below.
                if (!isVolatileQueueField(key)) downloads.setProperty(key, value);
            } else if (key.startsWith("linkgrabber.")) {
                linkGrabber.setProperty(key, value);
            } else {
                settings.setProperty(key, value);
            }
        }
        retainTerminalQueueMetadata(safeState, downloads);
        return fromProperties(settings, downloads, linkGrabber);
    }

    /**
     * Active transfer telemetry is intentionally not replayed, but terminal
     * rows need their durable user-facing metadata. A completed row needs its
     * final byte count/path to remain usable; an error row needs its final
     * reason instead of becoming an unexplained generic error after restore.
     */
    private static void retainTerminalQueueMetadata(Properties state, Properties downloads) {
        for (Map.Entry<Object, Object> entry : state.entrySet()) {
            String key = String.valueOf(entry.getKey());
            String stateValue = String.valueOf(entry.getValue());
            if (!key.startsWith("queue.") || !key.endsWith(".state")
                    || (!"FINISHED".equals(stateValue) && !"ERROR".equals(stateValue))) continue;
            String prefix = key.substring(0, key.length() - "state".length());
            if ("FINISHED".equals(stateValue)) {
                copyIfPresent(state, downloads, prefix + "loaded");
                copyIfPresent(state, downloads, prefix + "outputPath");
            }
            copyIfPresent(state, downloads, prefix + "detail");
        }
    }

    private static void copyIfPresent(Properties source, Properties target, String key) {
        String value = source.getProperty(key);
        if (value != null) target.setProperty(key, value);
    }

    private static boolean isVolatileQueueField(String key) {
        return key.endsWith(".loaded")
                || key.endsWith(".speed")
                || key.endsWith(".outputPath")
                || key.endsWith(".detail")
                || key.endsWith(".retryAttempt")
                || key.endsWith(".retryAt")
                || key.endsWith(".retryReason");
    }

    /**
     * Converts text snapshots to canonical UTF-8. Settings text is parsed as
     * properties and scrubbed of credential fields; list text is line-end
     * normalized without imposing a schema on callers.
     */
    public static HistorySnapshot fromText(String settings, String downloads, String linkGrabber) {
        return new HistorySnapshot(canonicalSettingsText(settings), canonicalText(downloads), canonicalText(linkGrabber));
    }

    /** Internal fast path for bytes already read from this service's canonical Git files. */
    static HistorySnapshot fromCanonicalBytes(byte[] settings, byte[] downloads, byte[] linkGrabber) {
        return new HistorySnapshot(settings, downloads, linkGrabber);
    }

    public byte[] settingsBytes() {
        return copy(settings);
    }

    public byte[] downloadsBytes() {
        return copy(downloads);
    }

    public byte[] linkGrabberBytes() {
        return copy(linkGrabber);
    }

    public String settingsText() {
        return new String(settings, StandardCharsets.UTF_8);
    }

    public String downloadsText() {
        return new String(downloads, StandardCharsets.UTF_8);
    }

    public String linkGrabberText() {
        return new String(linkGrabber, StandardCharsets.UTF_8);
    }

    public Properties settingsProperties() {
        return loadProperties(settings);
    }

    public Properties downloadsProperties() {
        return loadProperties(downloads);
    }

    public Properties linkGrabberProperties() {
        return loadProperties(linkGrabber);
    }

    public long byteCount() {
        return (long) settings.length + downloads.length + linkGrabber.length;
    }

    private static byte[] canonicalSettingsText(String text) {
        Properties settings = new Properties();
        String normalized = normalizeLineEndings(text);
        if (!normalized.isEmpty()) {
            try {
                settings.load(new StringReader(normalized));
            } catch (IOException impossible) {
                throw new IllegalArgumentException("Unable to parse settings snapshot", impossible);
            }
        }
        return canonicalProperties(settings, true);
    }

    private static byte[] canonicalText(String text) {
        return normalizeLineEndings(text).getBytes(StandardCharsets.UTF_8);
    }

    /** A deterministic, header-free Java-properties encoder using UTF-8. */
    static byte[] canonicalProperties(Properties source, boolean scrubCredentials) {
        TreeMap<String, String> sorted = new TreeMap<>();
        if (source != null) {
            for (Map.Entry<Object, Object> entry : source.entrySet()) {
                String key = String.valueOf(entry.getKey());
                if (scrubCredentials && isCredentialField(key)) continue;
                sorted.put(key, String.valueOf(entry.getValue()));
            }
        }
        StringBuilder encoded = new StringBuilder();
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            encoded.append(escape(entry.getKey(), true));
            encoded.append('=');
            encoded.append(escape(entry.getValue(), false));
            encoded.append('\n');
        }
        return encoded.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static boolean isCredentialField(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        return normalized.contains("myjd")
                || normalized.contains("my.jdownloader")
                || normalized.contains("myjdownloader");
    }


    private static String escape(String value, boolean key) {
        String text = Objects.requireNonNullElse(value, "");
        StringBuilder escaped = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            switch (character) {
                case '\\' -> escaped.append("\\\\");
                case '\t' -> escaped.append("\\t");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\f' -> escaped.append("\\f");
                case ' ', '=', ':', '#', '!' -> {
                    if (key || index == 0 || character != ' ') escaped.append('\\');
                    escaped.append(character);
                }
                default -> {
                    if (character < 0x20 || character == 0x7f) {
                        escaped.append(String.format(Locale.ROOT, "\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.toString();
    }

    private static Properties loadProperties(byte[] bytes) {
        Properties properties = new Properties();
        if (bytes.length == 0) return properties;
        try {
            properties.load(new StringReader(new String(bytes, StandardCharsets.UTF_8)));
        } catch (IOException impossible) {
            throw new IllegalStateException("History snapshot properties could not be read", impossible);
        }
        return properties;
    }

    private static String normalizeLineEndings(String text) {
        String normalized = Objects.requireNonNullElse(text, "").replace("\r\n", "\n").replace('\r', '\n');
        return normalized.isEmpty() || normalized.endsWith("\n") ? normalized : normalized + "\n";
    }

    private static byte[] copy(byte[] bytes) {
        return bytes == null || bytes.length == 0 ? EMPTY : bytes.clone();
    }

    public static final class Builder {
        private byte[] settings = EMPTY;
        private byte[] downloads = EMPTY;
        private byte[] linkGrabber = EMPTY;

        private Builder() {
        }

        public Builder settings(Properties values) {
            settings = canonicalProperties(values, true);
            return this;
        }

        public Builder downloads(Properties values) {
            downloads = canonicalProperties(values, false);
            return this;
        }

        public Builder linkGrabber(Properties values) {
            linkGrabber = canonicalProperties(values, false);
            return this;
        }

        public Builder settingsText(String value) {
            settings = canonicalSettingsText(value);
            return this;
        }

        public Builder downloadsText(String value) {
            downloads = canonicalText(value);
            return this;
        }

        public Builder linkGrabberText(String value) {
            linkGrabber = canonicalText(value);
            return this;
        }

        public HistorySnapshot build() {
            return new HistorySnapshot(settings, downloads, linkGrabber);
        }
    }
}
