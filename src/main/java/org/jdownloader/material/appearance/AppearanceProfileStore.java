package org.jdownloader.material.appearance;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

/** Bounded, atomic profile persistence with forward-compatible unknown-field retention. */
public final class AppearanceProfileStore {

    public static final long MAX_IMPORT_BYTES = 4L * 1024 * 1024;
    public static final int MAX_TARGETS = 5_000;
    public static final int MAX_PROPERTIES = 100_000;

    private static final String SCHEMA = "schema.version";
    private static final String TARGET_PREFIX = "target.";
    private static final String PRESET_PREFIX = "preset.";

    private final Path file;

    public AppearanceProfileStore(Path file) {
        this.file = file.toAbsolutePath().normalize();
    }

    public static AppearanceProfileStore defaultStore() {
        return new AppearanceProfileStore(Path.of(System.getProperty("user.home", "."),
                ".jdownloader-material", "appearance.properties"));
    }

    public Path file() { return file; }

    public AppearanceProfile load() throws IOException {
        return Files.isRegularFile(file) ? read(file) : new AppearanceProfile();
    }

    public void save(AppearanceProfile profile) throws IOException {
        writeAtomically(file, profile);
    }

    public AppearanceProfile importFrom(Path source) throws IOException {
        return read(source.toAbsolutePath().normalize());
    }

    /** Imports only after full validation, then atomically replaces the managed profile file. */
    public AppearanceProfile importAndSave(Path source) throws IOException {
        AppearanceProfile imported = importFrom(source);
        save(imported);
        return imported;
    }

    public void exportTo(Path destination, AppearanceProfile profile) throws IOException {
        writeAtomically(destination.toAbsolutePath().normalize(), profile);
    }

    /** Deterministic, header-free payload used by encrypted settings backups and local history. */
    public static String serialize(AppearanceProfile profile) throws IOException {
        Properties properties = encode(profile.copy());
        TreeMap<String, String> sorted = new TreeMap<>();
        properties.forEach((key, value) -> sorted.put(String.valueOf(key), String.valueOf(value)));
        StringBuilder payload = new StringBuilder();
        sorted.forEach((key, value) -> payload.append(escape(key, true)).append('=')
                .append(escape(value, false)).append('\n'));
        if (payload.toString().getBytes(StandardCharsets.UTF_8).length > MAX_IMPORT_BYTES) {
            throw new IOException("Serialized appearance profile exceeds the 4 MiB limit");
        }
        return payload.toString();
    }

    /** Validates and decodes the deterministic Settings/history payload. */
    public static AppearanceProfile deserialize(String payload) throws IOException {
        String encoded = payload == null ? "" : payload;
        if (encoded.isBlank()) return new AppearanceProfile();
        if (encoded.getBytes(StandardCharsets.UTF_8).length > MAX_IMPORT_BYTES) {
            throw new IOException("Serialized appearance profile exceeds the 4 MiB limit");
        }
        Properties properties = new Properties();
        try (Reader reader = new StringReader(encoded)) {
            properties.load(reader);
        }
        if (properties.size() > MAX_PROPERTIES) {
            throw new IOException("Serialized appearance profile contains too many properties");
        }
        try {
            return decode(properties);
        } catch (IllegalArgumentException invalid) {
            throw new IOException("Invalid serialized appearance profile: " + invalid.getMessage(), invalid);
        }
    }

    public static AppearanceProfile read(Path source) throws IOException {
        if (!Files.isRegularFile(source)) throw new IOException("Appearance profile does not exist: " + source);
        if (Files.size(source) > MAX_IMPORT_BYTES) throw new IOException("Appearance profile exceeds the 4 MiB limit");
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        if (properties.size() > MAX_PROPERTIES) throw new IOException("Appearance profile contains too many properties");
        try {
            return decode(properties);
        } catch (IllegalArgumentException failure) {
            throw new IOException("Invalid appearance profile: " + failure.getMessage(), failure);
        }
    }

    private static AppearanceProfile decode(Properties properties) {
        AppearanceProfile profile = new AppearanceProfile();
        int schema = integer(required(properties, SCHEMA, Integer.toString(AppearanceProfile.CURRENT_SCHEMA_VERSION)), SCHEMA);
        profile.setSourceSchemaVersion(schema);

        consumeEnum(properties, "global.theme", ThemeMode.class, profile::setTheme);
        consumeEnum(properties, "global.density", Density.class, profile::setDensity);
        consumeColor(properties, "global.seed", profile::setSeedColor);
        consumeColor(properties, "global.accent", profile::setAccentColor);
        String family = consume(properties, "global.font.family");
        String source = consume(properties, "global.font.source");
        if (family != null || source != null) {
            profile.setFontFamily(family == null ? AppearanceProfile.DEFAULT_FONT_FAMILY : family,
                    source == null ? FontSource.INSTALLED : FontSource.valueOf(source));
        }
        String scale = consume(properties, "global.font.sizeScale");
        if (scale != null) profile.setFontSizeScale(finite(scale, "global.font.sizeScale"));
        String weight = consume(properties, "global.font.weight");
        if (weight != null) profile.setFontWeight(integer(weight, "global.font.weight"));
        String fallback = consume(properties, "global.font.cjkFallback");
        if (fallback != null) profile.setCjkFallback(fallback);
        properties.remove(SCHEMA);

        int targets = 0;
        int decodedProperties = 0;
        Set<AppearanceTargetId> decodedTargets = new HashSet<>();
        List<String> keys = new ArrayList<>(properties.stringPropertyNames());
        for (String key : keys) {
            if (!key.startsWith(TARGET_PREFIX)) continue;
            String[] parts = key.split("\\.", 4);
            if (parts.length != 4) continue;
            AppearanceTargetId targetId;
            AppearanceState state;
            String propertyId;
            try {
                targetId = AppearanceTargetId.of(decodeToken(parts[1]));
                state = AppearanceState.valueOf(parts[2]);
                propertyId = decodeToken(parts[3]);
            } catch (RuntimeException futureOrInvalid) {
                continue;
            }
            if (decodedTargets.add(targetId) && ++targets > MAX_TARGETS) {
                throw new IllegalArgumentException("Appearance profile contains too many targets");
            }
            if (++decodedProperties > MAX_PROPERTIES) throw new IllegalArgumentException("Too many target properties");
            String value = properties.getProperty(key, "");
            AppearanceStyle style = profile.target(targetId).style(state);
            AppearanceProperty property = AppearanceProperty.byId(propertyId);
            if (property == null) style.retainUnsupported(propertyId, value);
            else style.set(property, value);
            properties.remove(key);
        }

        decodePresets(properties, profile);
        properties.stringPropertyNames().stream().sorted()
                .forEach(key -> profile.retainUnsupportedRootField(key, properties.getProperty(key, "")));
        return profile;
    }

    private static void decodePresets(Properties properties, AppearanceProfile profile) {
        Map<String, Map<String, String>> grouped = new LinkedHashMap<>();
        for (String key : new ArrayList<>(properties.stringPropertyNames())) {
            if (!key.startsWith(PRESET_PREFIX)) continue;
            String[] parts = key.split("\\.", 3);
            if (parts.length != 3) continue;
            String id;
            try { id = decodeToken(parts[1]); } catch (RuntimeException invalid) { continue; }
            String field = parts[2];
            if (!knownPresetField(field)) continue;
            grouped.computeIfAbsent(id, ignored -> new LinkedHashMap<>()).put(field, properties.getProperty(key, ""));
            properties.remove(key);
        }
        if (grouped.size() > 500) throw new IllegalArgumentException("Too many user appearance presets");
        grouped.forEach((id, values) -> {
            AppearanceStyle style = new AppearanceStyle();
            values.entrySet().stream().filter(entry -> entry.getKey().startsWith("style."))
                    .forEach(entry -> {
                        String propertyId = decodeToken(entry.getKey().substring("style.".length()));
                        AppearanceProperty property = AppearanceProperty.byId(propertyId);
                        if (property == null) style.retainUnsupported(propertyId, entry.getValue());
                        else style.set(property, entry.getValue());
                    });
            AppearancePreset preset = new AppearancePreset(id, value(values, "name", id), false,
                    enumValue(values, "theme", ThemeMode.class, ThemeMode.LIGHT),
                    enumValue(values, "density", Density.class, Density.STANDARD),
                    ColorValue.fromStorageString(value(values, "seed", ColorTranslator.fromHex("#006B5C").toStorageString())),
                    ColorValue.fromStorageString(value(values, "accent", ColorTranslator.fromHex("#006B5C").toStorageString())),
                    value(values, "font.family", AppearanceProfile.DEFAULT_FONT_FAMILY),
                    enumValue(values, "font.source", FontSource.class, FontSource.INSTALLED),
                    finite(value(values, "font.sizeScale", "1.0"), "preset font scale"),
                    integer(value(values, "font.weight", "400"), "preset font weight"),
                    value(values, "font.cjkFallback", AppearanceProfile.DEFAULT_CJK_FALLBACK), style);
            profile.addUserPreset(preset);
        });
    }

    private static boolean knownPresetField(String field) {
        return field.startsWith("style.") || switch (field) {
            case "name", "theme", "density", "seed", "accent", "font.family", "font.source",
                    "font.sizeScale", "font.weight", "font.cjkFallback" -> true;
            default -> false;
        };
    }

    private static void writeAtomically(Path destination, AppearanceProfile profile) throws IOException {
        Properties properties = encode(profile.copy());
        Path parent = destination.getParent();
        if (parent == null) throw new IOException("Appearance profile destination has no parent");
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "appearance-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                properties.store(writer, "JDownloader Material appearance profile");
            }
            if (Files.size(temporary) > MAX_IMPORT_BYTES) throw new IOException("Appearance profile exceeds the 4 MiB limit");
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unavailable) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static Properties encode(AppearanceProfile profile) {
        Properties properties = new Properties();
        profile.unsupportedRootFields().forEach(properties::setProperty);
        properties.setProperty(SCHEMA, Integer.toString(Math.max(AppearanceProfile.CURRENT_SCHEMA_VERSION,
                profile.sourceSchemaVersion())));
        properties.setProperty("global.theme", profile.theme().name());
        properties.setProperty("global.density", profile.density().name());
        properties.setProperty("global.seed", profile.seedColor().toStorageString());
        properties.setProperty("global.accent", profile.accentColor().toStorageString());
        properties.setProperty("global.font.family", profile.fontFamily());
        properties.setProperty("global.font.source", profile.fontSource().name());
        properties.setProperty("global.font.sizeScale", Double.toString(profile.fontSizeScale()));
        properties.setProperty("global.font.weight", Integer.toString(profile.fontWeight()));
        properties.setProperty("global.font.cjkFallback", profile.cjkFallback());

        profile.targets().forEach((targetId, target) -> target.states().forEach((state, style) -> {
            String prefix = TARGET_PREFIX + encodeToken(targetId.value()) + "." + state.name() + ".";
            style.values().forEach((property, value) ->
                    properties.setProperty(prefix + encodeToken(property.id()), value));
            style.unsupportedValues().forEach((property, value) ->
                    properties.setProperty(prefix + encodeToken(property), value));
        }));

        profile.userPresets().forEach((id, preset) -> {
            String prefix = PRESET_PREFIX + encodeToken(id) + ".";
            properties.setProperty(prefix + "name", preset.name());
            properties.setProperty(prefix + "theme", preset.theme().name());
            properties.setProperty(prefix + "density", preset.density().name());
            properties.setProperty(prefix + "seed", preset.seedColor().toStorageString());
            properties.setProperty(prefix + "accent", preset.accentColor().toStorageString());
            properties.setProperty(prefix + "font.family", preset.fontFamily());
            properties.setProperty(prefix + "font.source", preset.fontSource().name());
            properties.setProperty(prefix + "font.sizeScale", Double.toString(preset.fontSizeScale()));
            properties.setProperty(prefix + "font.weight", Integer.toString(preset.fontWeight()));
            properties.setProperty(prefix + "font.cjkFallback", preset.cjkFallback());
            preset.style().values().forEach((property, value) ->
                    properties.setProperty(prefix + "style." + encodeToken(property.id()), value));
            preset.style().unsupportedValues().forEach((property, value) ->
                    properties.setProperty(prefix + "style." + encodeToken(property), value));
        });
        return properties;
    }

    private static String encodeToken(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String escape(String value, boolean key) {
        String text = value == null ? "" : value;
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
                        escaped.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.toString();
    }

    private static String decodeToken(String value) {
        String decoded = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
        if (decoded.length() > 1_024) throw new IllegalArgumentException("Encoded appearance token is too long");
        return decoded;
    }

    private static String consume(Properties properties, String key) {
        String value = properties.getProperty(key);
        properties.remove(key);
        return value;
    }

    private static <T extends Enum<T>> void consumeEnum(Properties properties, String key, Class<T> type,
                                                         java.util.function.Consumer<T> consumer) {
        String value = consume(properties, key);
        if (value != null) consumer.accept(Enum.valueOf(type, value));
    }

    private static void consumeColor(Properties properties, String key,
                                     java.util.function.Consumer<ColorValue> consumer) {
        String value = consume(properties, key);
        if (value != null) consumer.accept(ColorValue.fromStorageString(value));
    }

    private static String required(Properties properties, String key, String fallback) {
        return properties.getProperty(key, fallback);
    }

    private static int integer(String value, String name) {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException invalid) { throw new IllegalArgumentException(name + " must be an integer", invalid); }
    }

    private static double finite(String value, String name) {
        try {
            double parsed = Double.parseDouble(value);
            if (!Double.isFinite(parsed)) throw new NumberFormatException("not finite");
            return parsed;
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException(name + " must be a finite number", invalid);
        }
    }

    private static String value(Map<String, String> values, String key, String fallback) {
        return values.getOrDefault(key, fallback);
    }

    private static <T extends Enum<T>> T enumValue(Map<String, String> values, String key, Class<T> type, T fallback) {
        String value = values.get(key);
        return value == null ? fallback : Enum.valueOf(type, value);
    }
}
