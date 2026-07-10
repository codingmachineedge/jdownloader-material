package org.jdownloader.material.engine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Properties;
import org.jdownloader.material.model.CrawledLink;
import org.jdownloader.material.model.CrawledPackage;
import org.jdownloader.material.model.DownloadLink;
import org.jdownloader.material.model.DownloadPackage;
import org.jdownloader.material.model.DownloadPriority;
import org.jdownloader.material.model.DownloadState;
import org.jdownloader.material.model.LinkAvailability;

/**
 * Small, atomic local journal for non-secret settings and direct-download jobs.
 * Credentials deliberately stay out of this normal-state file; encrypted
 * import/export remains the only persistence route for them.
 */
final class AppStateStore {

    private static final int MAX_STATE_BYTES = 8 * 1024 * 1024;
    private static final int MAX_PACKAGES = 5_000;
    private static final int MAX_LINKS_PER_PACKAGE = 20_000;
    private static final String VERSION = "1";

    private final Path stateFile;

    AppStateStore(Path directory) {
        this.stateFile = directory.resolve("state.properties");
    }

    static Path defaultDirectory() {
        return Path.of(System.getProperty("user.home", "."), ".jdownloader-material");
    }

    Properties read() throws IOException {
        if (!Files.isRegularFile(stateFile)) return new Properties();
        if (Files.size(stateFile) > MAX_STATE_BYTES) {
            throw new IOException("Saved state is too large to load safely.");
        }
        Properties state = new Properties();
        try (InputStream in = Files.newInputStream(stateFile)) {
            state.load(in);
        }
        return state;
    }

    void write(Properties state) throws IOException {
        Files.createDirectories(stateFile.getParent());
        byte[] encoded;
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            state.store(out, "JDownloader Material non-secret app state");
            encoded = out.toByteArray();
        }
        // Never replace a readable journal with a snapshot that this same
        // application would reject on the next launch.
        if (encoded.length > MAX_STATE_BYTES) {
            throw new IOException("Saved state exceeds the safe journal size limit.");
        }
        Path temp = Files.createTempFile(stateFile.getParent(), "state-", ".tmp");
        try {
            Files.write(temp, encoded, StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(temp, stateFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, stateFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    static Properties snapshot(Settings settings, List<DownloadPackage> packages,
                               List<CrawledPackage> crawledPackages) {
        Properties state = SettingsIO.snapshot(settings);
        // Normal local state must never persist remote-control credentials in plaintext.
        state.remove("myjdEmail");
        state.remove("myjdPassword");
        state.setProperty("stateVersion", VERSION);
        state.setProperty("queue.packageCount", Integer.toString(packages.size()));
        for (int packageIndex = 0; packageIndex < packages.size(); packageIndex++) {
            DownloadPackage pkg = packages.get(packageIndex);
            String prefix = "queue.package." + packageIndex + ".";
            state.setProperty(prefix + "name", pkg.nameProp().get());
            state.setProperty(prefix + "destination", pkg.destinationProperty().get());
            state.setProperty(prefix + "linkCount", Integer.toString(pkg.links().size()));
            for (int linkIndex = 0; linkIndex < pkg.links().size(); linkIndex++) {
                DownloadLink link = pkg.links().get(linkIndex);
                String linkPrefix = prefix + "link." + linkIndex + ".";
                state.setProperty(linkPrefix + "name", link.nameProperty().getValue());
                state.setProperty(linkPrefix + "host", link.hostProperty().getValue());
                state.setProperty(linkPrefix + "url", link.url().get());
                state.setProperty(linkPrefix + "destination", link.destinationProperty().get());
                state.setProperty(linkPrefix + "outputPath", link.outputPathProperty().get());
                state.setProperty(linkPrefix + "total", Long.toString(link.total()));
                state.setProperty(linkPrefix + "loaded", Long.toString(link.loadedProp().get()));
                state.setProperty(linkPrefix + "state", link.state().name());
                state.setProperty(linkPrefix + "enabled", Boolean.toString(link.enabled().get()));
                state.setProperty(linkPrefix + "priority", link.priorityProperty().get().name());
                state.setProperty(linkPrefix + "detail", link.detailProperty().get());
                state.setProperty(linkPrefix + "retryAttempt", Integer.toString(link.retryAttemptProperty().get()));
                state.setProperty(linkPrefix + "retryAt", Long.toString(link.retryAtEpochMillisProperty().get()));
                state.setProperty(linkPrefix + "retryReason", link.retryReasonProperty().get());
            }
        }
        state.setProperty("linkgrabber.packageCount", Integer.toString(crawledPackages.size()));
        for (int packageIndex = 0; packageIndex < crawledPackages.size(); packageIndex++) {
            CrawledPackage pkg = crawledPackages.get(packageIndex);
            String prefix = "linkgrabber.package." + packageIndex + ".";
            state.setProperty(prefix + "name", pkg.nameProperty().get());
            state.setProperty(prefix + "destination", pkg.destinationProperty().get());
            state.setProperty(prefix + "linkCount", Integer.toString(pkg.links().size()));
            for (int linkIndex = 0; linkIndex < pkg.links().size(); linkIndex++) {
                CrawledLink link = pkg.links().get(linkIndex);
                String linkPrefix = prefix + "link." + linkIndex + ".";
                state.setProperty(linkPrefix + "name", link.nameProperty().get());
                state.setProperty(linkPrefix + "host", link.hostProperty().get());
                state.setProperty(linkPrefix + "url", link.urlProperty().get());
                state.setProperty(linkPrefix + "size", Long.toString(link.size()));
                state.setProperty(linkPrefix + "availability", link.availability().name());
            }
        }
        return state;
    }

    static void restore(Properties state, Settings settings, List<DownloadPackage> output,
                        List<CrawledPackage> crawledOutput) {
        SettingsIO.apply(state, settings);
        int packageCount = boundedInt(state.getProperty("queue.packageCount"), 0, MAX_PACKAGES);
        for (int packageIndex = 0; packageIndex < packageCount; packageIndex++) {
            String prefix = "queue.package." + packageIndex + ".";
            String name = state.getProperty(prefix + "name", "Recovered package " + (packageIndex + 1));
            String destination = state.getProperty(prefix + "destination", settings.downloadFolderProperty().get());
            DownloadPackage pkg = new DownloadPackage(name, destination);
            int linkCount = boundedInt(state.getProperty(prefix + "linkCount"), 0, MAX_LINKS_PER_PACKAGE);
            for (int linkIndex = 0; linkIndex < linkCount; linkIndex++) {
                String linkPrefix = prefix + "link." + linkIndex + ".";
                String linkName = state.getProperty(linkPrefix + "name", "download.bin");
                String host = state.getProperty(linkPrefix + "host", "unknown");
                long total = nonNegativeLong(state.getProperty(linkPrefix + "total"));
                DownloadLink link = new DownloadLink(linkName, host, total);
                link.url().set(state.getProperty(linkPrefix + "url", ""));
                link.destinationProperty().set(state.getProperty(linkPrefix + "destination", destination));
                link.outputPathProperty().set(state.getProperty(linkPrefix + "outputPath", ""));
                long loaded = nonNegativeLong(state.getProperty(linkPrefix + "loaded"));
                link.loadedProp().set(total > 0 ? Math.min(loaded, total) : loaded);
                link.enabled().set(Boolean.parseBoolean(state.getProperty(linkPrefix + "enabled", "true")));
                link.priorityProperty().set(parsePriority(state.getProperty(linkPrefix + "priority")));
                link.detailProperty().set(state.getProperty(linkPrefix + "detail", ""));
                link.retryAttemptProperty().set(boundedInt(state.getProperty(linkPrefix + "retryAttempt"), 0, 4));
                link.retryAtEpochMillisProperty().set(nonNegativeLong(state.getProperty(linkPrefix + "retryAt")));
                link.retryReasonProperty().set(state.getProperty(linkPrefix + "retryReason", ""));
                DownloadState recovered = parseState(state.getProperty(linkPrefix + "state"));
                // An active HTTP stream cannot survive process exit. Its .part file is resumed as queued.
                if (!link.enabled().get()) {
                    link.setState(recovered == DownloadState.FINISHED ? DownloadState.FINISHED : DownloadState.DISABLED);
                } else {
                    link.setState(recovered == DownloadState.RUNNING || recovered == DownloadState.PAUSED
                            ? DownloadState.QUEUED : recovered);
                }
                pkg.links().add(link);
            }
            if (!pkg.links().isEmpty()) output.add(pkg);
        }
        int crawledCount = boundedInt(state.getProperty("linkgrabber.packageCount"), 0, MAX_PACKAGES);
        for (int packageIndex = 0; packageIndex < crawledCount; packageIndex++) {
            String prefix = "linkgrabber.package." + packageIndex + ".";
            CrawledPackage pkg = new CrawledPackage(
                    state.getProperty(prefix + "name", "Recovered links " + (packageIndex + 1)),
                    state.getProperty(prefix + "destination", settings.downloadFolderProperty().get()));
            int linkCount = boundedInt(state.getProperty(prefix + "linkCount"), 0, MAX_LINKS_PER_PACKAGE);
            for (int linkIndex = 0; linkIndex < linkCount; linkIndex++) {
                String linkPrefix = prefix + "link." + linkIndex + ".";
                CrawledLink link = new CrawledLink(
                        state.getProperty(linkPrefix + "name", "download.bin"),
                        state.getProperty(linkPrefix + "host", "unknown"),
                        state.getProperty(linkPrefix + "url", ""),
                        nonNegativeLong(state.getProperty(linkPrefix + "size")));
                link.availabilityProperty().set(parseAvailability(state.getProperty(linkPrefix + "availability")));
                pkg.links().add(link);
            }
            if (!pkg.links().isEmpty()) crawledOutput.add(pkg);
        }
    }

    private static int boundedInt(String value, int fallback, int upperBound) {
        try {
            int parsed = Integer.parseInt(value);
            return Math.max(0, Math.min(parsed, upperBound));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static long nonNegativeLong(String value) {
        try { return Math.max(0, Long.parseLong(value)); } catch (Exception ignored) { return 0; }
    }

    private static DownloadState parseState(String value) {
        try { return DownloadState.valueOf(value); } catch (Exception ignored) { return DownloadState.QUEUED; }
    }

    private static LinkAvailability parseAvailability(String value) {
        try { return LinkAvailability.valueOf(value); } catch (Exception ignored) { return LinkAvailability.UNKNOWN; }
    }

    private static DownloadPriority parsePriority(String value) {
        try { return DownloadPriority.valueOf(value); } catch (Exception ignored) { return DownloadPriority.NORMAL; }
    }
}
