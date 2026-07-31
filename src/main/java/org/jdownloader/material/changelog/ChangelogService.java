package org.jdownloader.material.changelog;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;
import org.jdownloader.material.engine.LanguageMode;
import org.jdownloader.material.i18n.I18n;

/** Loads every factual release bundled at build time and exports the current view. */
public final class ChangelogService {
    private final List<ChangelogEntry> entries;

    public ChangelogService() {
        this.entries = List.copyOf(loadEntries());
    }

    public List<ChangelogEntry> entries() { return entries; }

    public List<ChangelogEntry> filter(LocalDate from, LocalDate to, Predicate<ChangelogEntry> search) {
        Predicate<ChangelogEntry> predicate = search == null ? ignored -> true : search;
        return entries.stream()
                .filter(entry -> from == null || !entry.date().isBefore(from))
                .filter(entry -> to == null || !entry.date().isAfter(to))
                .filter(predicate)
                .toList();
    }

    public static Predicate<ChangelogEntry> plainSearch(String query) {
        String needle = Objects.requireNonNullElse(query, "").strip().toLowerCase(Locale.ROOT);
        return entry -> needle.isEmpty() || entry.searchable().toLowerCase(Locale.ROOT).contains(needle);
    }

    public String markdown(List<ChangelogEntry> selected, LanguageMode mode) {
        return markdown(selected, entry -> entry.localized(mode));
    }

    public String markdown(List<ChangelogEntry> selected, I18n i18n) {
        Objects.requireNonNull(i18n, "i18n");
        return markdown(selected, entry -> entry.localized(i18n));
    }

    private String markdown(List<ChangelogEntry> selected,
                            java.util.function.Function<ChangelogEntry, String> localized) {
        List<ChangelogEntry> safe = selected == null ? List.of() : selected;
        String range = safe.isEmpty() ? "empty selection"
                : safe.getLast().date() + " through " + safe.getFirst().date();
        StringBuilder markdown = new StringBuilder("# JDownloader Material changelog\n\n")
                .append("Exported range: ").append(range).append("\n\n");
        for (ChangelogEntry entry : safe) {
            markdown.append("## ").append(entry.version()).append(" — ").append(entry.date()).append("\n\n")
                    .append("**").append(entry.category()).append("** · commit `")
                    .append(entry.commit()).append("`\n\n")
                    .append(localized.apply(entry)).append("\n\n");
        }
        return markdown.toString();
    }

    public Path exportMarkdown(Path target, List<ChangelogEntry> selected, LanguageMode mode) throws IOException {
        return exportMarkdown(target, markdown(selected, mode));
    }

    public Path exportMarkdown(Path target, List<ChangelogEntry> selected, I18n i18n) throws IOException {
        return exportMarkdown(target, markdown(selected, i18n));
    }

    private Path exportMarkdown(Path target, String markdown) throws IOException {
        Path output = Objects.requireNonNull(target, "target").toAbsolutePath().normalize();
        Path parent = output.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temp = Files.createTempFile(parent == null ? Path.of(System.getProperty("java.io.tmpdir")) : parent,
                "changelog-", ".tmp");
        try {
            Files.writeString(temp, markdown, StandardCharsets.UTF_8);
            try {
                Files.move(temp, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temp, output, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
        return output;
    }

    private static List<ChangelogEntry> loadEntries() {
        List<ChangelogEntry> loaded = new ArrayList<>();
        try (var input = ChangelogService.class.getResourceAsStream("/changelog.tsv")) {
            if (input == null) return loaded;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank() || line.startsWith("#")) continue;
                    String[] fields = line.split("\\t", 6);
                    if (fields.length != 6) continue;
                    loaded.add(new ChangelogEntry(fields[0], LocalDate.parse(fields[1]), fields[2], fields[3],
                            fields[4], fields[5]));
                }
            }
        } catch (IOException | RuntimeException error) {
            System.err.println("Bundled changelog could not be read: " + error.getMessage());
        }
        addRuntimeRelease(loaded);
        loaded.sort(Comparator.comparing(ChangelogEntry::date, Comparator.reverseOrder())
                .thenComparing(ChangelogEntry::version, (left, right) -> compareVersions(right, left)));
        return loaded;
    }

    private static void addRuntimeRelease(List<ChangelogEntry> loaded) {
        String version = System.getProperty("jdownloader.material.version", "").strip();
        String dateText = System.getProperty("jdownloader.material.releaseDate", "").strip();
        String commit = System.getProperty("jdownloader.material.commit", "").strip();
        if (version.isEmpty() || dateText.isEmpty() || commit.isEmpty()
                || loaded.stream().anyMatch(entry -> entry.version().equals(version))) return;
        try {
            LocalDate date = LocalDate.parse(dateText);
            loaded.add(new ChangelogEntry(version, date, "Build", commit,
                    "No additional categorized changes were recorded inside this installer; "
                            + "the build was verified from commit " + commit + ".",
                    "呢個安裝程式冇另外記錄分類改動；個 build 已經由 commit " + commit + " 驗證。"));
        } catch (RuntimeException invalidMetadata) {
            System.err.println("Runtime release metadata could not be added: " + invalidMetadata.getMessage());
        }
    }

    private static int compareVersions(String left, String right) {
        int[] leftParts = versionParts(left);
        int[] rightParts = versionParts(right);
        int length = Math.max(leftParts.length, rightParts.length);
        for (int index = 0; index < length; index++) {
            int leftPart = index < leftParts.length ? leftParts[index] : 0;
            int rightPart = index < rightParts.length ? rightParts[index] : 0;
            int comparison = Integer.compare(leftPart, rightPart);
            if (comparison != 0) return comparison;
        }
        return left.compareToIgnoreCase(right);
    }

    private static int[] versionParts(String version) {
        String normalized = version == null ? "" : version.replaceFirst("^[^0-9]*", "");
        String[] parts = normalized.split("[^0-9]+");
        int[] values = new int[parts.length];
        for (int index = 0; index < parts.length; index++) {
            try { values[index] = parts[index].isEmpty() ? 0 : Integer.parseInt(parts[index]); }
            catch (NumberFormatException ignored) { values[index] = Integer.MAX_VALUE; }
        }
        return values;
    }
}
