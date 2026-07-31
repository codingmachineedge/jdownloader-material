package org.jdownloader.material.integration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** Detects and launches editors without passing user-controlled text through a shell. */
public final class ExternalEditorService {

    public static final String AUTO_SELECTION = "auto";
    public static final String CUSTOM_SELECTION = "custom";

    public record Editor(String id, String name, String commandTemplate) {
        public Editor {
            id = identifier(id == null || id.isBlank() ? name : id);
            name = Objects.requireNonNullElse(name, "Editor").strip();
            commandTemplate = Objects.requireNonNullElse(commandTemplate, "").strip();
            if (commandTemplate.isBlank()) throw new IllegalArgumentException("Editor command template is required");
        }

        public Editor(String name, String commandTemplate) {
            this(name, name, commandTemplate);
        }
    }

    @FunctionalInterface
    public interface Launcher {
        Process start(List<String> arguments) throws IOException;
    }

    @FunctionalInterface
    public interface Detector {
        List<Editor> detect();
    }

    private final Launcher launcher;
    private final Detector detector;
    private volatile List<Editor> detectedEditors = List.of();

    public ExternalEditorService() {
        this(arguments -> new ProcessBuilder(arguments).start(), ExternalEditorService::detectWindowsEditors);
    }

    /** Injectable boundary used by deterministic tests; arguments are never joined into a shell command. */
    public ExternalEditorService(Launcher launcher, Detector detector) {
        this.launcher = Objects.requireNonNull(launcher, "launcher");
        this.detector = Objects.requireNonNull(detector, "detector");
    }

    public List<Editor> detect() {
        Map<String, Editor> unique = new LinkedHashMap<>();
        List<Editor> found = detector.detect();
        if (found != null) {
            for (Editor editor : found) {
                if (editor != null) unique.putIfAbsent(editor.id(), editor);
            }
        }
        detectedEditors = List.copyOf(unique.values());
        return detectedEditors;
    }

    public List<Editor> detectedEditors() {
        return detectedEditors;
    }

    /** Resolves a persisted stable editor id without overwriting the separately persisted custom command. */
    public Optional<String> commandTemplate(String selection, String customCommand) {
        String selected = Objects.requireNonNullElse(selection, AUTO_SELECTION).strip().toLowerCase(Locale.ROOT);
        if (CUSTOM_SELECTION.equals(selected)) {
            String custom = Objects.requireNonNullElse(customCommand, "").strip();
            return custom.isBlank() ? Optional.empty() : Optional.of(custom);
        }
        if (detectedEditors.isEmpty()) return Optional.empty();
        if (selected.isBlank() || AUTO_SELECTION.equals(selected)) {
            return Optional.of(detectedEditors.getFirst().commandTemplate());
        }
        return detectedEditors.stream().filter(editor -> editor.id().equals(selected))
                .map(Editor::commandTemplate).findFirst();
    }

    public Process open(Path selected, String commandTemplate) throws IOException {
        Objects.requireNonNull(selected, "selected");
        String template = Objects.requireNonNullElse(commandTemplate, "").strip();
        if (template.isEmpty()) throw new IOException("No external editor is configured.");
        Path absolute = selected.toAbsolutePath().normalize();
        List<String> arguments = commandArguments(absolute, template);
        try {
            return launcher.start(List.copyOf(arguments));
        } catch (IOException failure) {
            throw new IOException("The configured editor could not be started: " + arguments.getFirst(), failure);
        }
    }

    /** Expands placeholders into a direct argv list; metacharacters remain ordinary arguments. */
    public static List<String> commandArguments(Path selected, String commandTemplate) throws IOException {
        Path absolute = Objects.requireNonNull(selected, "selected").toAbsolutePath().normalize();
        Path folder = Files.isDirectory(absolute) ? absolute : absolute.getParent();
        String template = Objects.requireNonNullElse(commandTemplate, "").strip();
        if (template.isEmpty()) throw new IOException("No external editor is configured.");
        List<String> arguments = parseCommandLine(template);
        if (arguments.isEmpty()) throw new IOException("The external editor command is empty.");
        boolean replaced = false;
        for (int index = 0; index < arguments.size(); index++) {
            String argument = arguments.get(index);
            String next = argument.replace("%file%", absolute.toString())
                    .replace("%folder%", folder == null ? absolute.toString() : folder.toString());
            replaced |= !next.equals(argument);
            arguments.set(index, next);
        }
        if (!replaced) arguments.add(absolute.toString());
        return List.copyOf(arguments);
    }

    /** Windows-compatible quoting parser; no metacharacter is ever executed by a command shell. */
    public static List<String> parseCommandLine(String command) throws IOException {
        List<String> result = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < command.length(); index++) {
            char current = command.charAt(index);
            if (current == '"') {
                quoted = !quoted;
            } else if (Character.isWhitespace(current) && !quoted) {
                if (!token.isEmpty()) {
                    result.add(token.toString());
                    token.setLength(0);
                }
            } else {
                token.append(current);
            }
        }
        if (quoted) throw new IOException("The editor command has an unmatched quote.");
        if (!token.isEmpty()) result.add(token.toString());
        return result;
    }

    private static List<Editor> detectWindowsEditors() {
        Map<String, Editor> found = new LinkedHashMap<>();
        addFromPath(found, "vscode", "Visual Studio Code", "code", "\"%s\" \"%%file%%\"");
        addFromPath(found, "vscode-insiders", "Visual Studio Code Insiders", "code-insiders",
                "\"%s\" \"%%file%%\"");
        addFromPath(found, "notepad-plus-plus", "Notepad++", "notepad++", "\"%s\" \"%%file%%\"");
        addFromPath(found, "intellij-idea", "IntelliJ IDEA", "idea64", "\"%s\" \"%%file%%\"");
        addFromPath(found, "eclipse", "Eclipse", "eclipse", "\"%s\" \"%%folder%%\"");

        addKnown(found, "vscode", "Visual Studio Code",
                envPath("LOCALAPPDATA", "Programs", "Microsoft VS Code", "Code.exe"), "%file%");
        addKnown(found, "vscode", "Visual Studio Code",
                envPath("ProgramFiles", "Microsoft VS Code", "Code.exe"), "%file%");
        addKnown(found, "notepad-plus-plus", "Notepad++",
                envPath("ProgramFiles", "Notepad++", "notepad++.exe"), "%file%");
        addJetBrainsInstall(found);
        return List.copyOf(found.values());
    }

    private static void addFromPath(Map<String, Editor> found, String id, String name, String executable,
                                    String template) {
        try {
            Process process = new ProcessBuilder("where.exe", executable).redirectErrorStream(true).start();
            if (!process.waitFor(3, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return;
            }
            String output = new String(process.getInputStream().readAllBytes()).strip();
            if (process.exitValue() == 0 && !output.isBlank()) {
                String first = output.lines().findFirst().orElse("").strip();
                if (!first.isBlank()) found.putIfAbsent(id, new Editor(id, name, template.formatted(first)));
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (IOException ignored) {
            // Detection is opportunistic; the user can always enter a command.
        }
    }

    private static void addKnown(Map<String, Editor> found, String id, String name, Path candidate,
                                 String placeholder) {
        if (candidate != null && Files.isRegularFile(candidate)) {
            found.putIfAbsent(id, new Editor(id, name,
                    quote(candidate.toString()) + " \"" + placeholder + "\""));
        }
    }

    private static void addJetBrainsInstall(Map<String, Editor> found) {
        if (found.containsKey("intellij-idea")) return;
        Path root = envPath("ProgramFiles", "JetBrains");
        if (root == null || !Files.isDirectory(root)) return;
        try (var installs = Files.list(root)) {
            for (Path install : installs.sorted().toList().reversed()) {
                Path executable = install.resolve("bin").resolve("idea64.exe");
                if (Files.isRegularFile(executable)) {
                    addKnown(found, "intellij-idea", "IntelliJ IDEA", executable, "%file%");
                    return;
                }
            }
        } catch (IOException ignored) {
            // Detection is best effort; Settings still exposes the custom command path.
        }
    }

    private static Path envPath(String variable, String... parts) {
        String root = System.getenv(variable);
        if (root == null || root.isBlank()) return null;
        return Path.of(root, parts);
    }

    private static String quote(String value) { return "\"" + value.replace("\"", "") + "\""; }

    private static String identifier(String value) {
        String normalized = Objects.requireNonNullElse(value, "editor").strip().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return normalized.isBlank() ? "editor" : normalized;
    }
}
